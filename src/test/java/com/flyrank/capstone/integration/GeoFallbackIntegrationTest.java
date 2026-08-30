package com.flyrank.capstone.integration;
import com.flyrank.capstone.dto.AuthResponse;
import com.flyrank.capstone.dto.WidgetResponse;
import com.flyrank.capstone.entity.Submission;
import com.flyrank.capstone.repository.SubmissionRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest
@AutoConfigureMockMvc
class GeoFallbackIntegrationTest {
    private static final HttpServer PROVIDER_B_STUB = startProviderBStub();
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SubmissionRepository submissionRepository;
    private static HttpServer startProviderBStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = "{\"country_name\":\"Testland\",\"city\":\"Testville\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.setExecutor(null);
            server.start();
            return server;
        } catch (Exception e) {
            throw new RuntimeException("Failed to start provider B stub", e);
        }
    }
    @DynamicPropertySource
    static void overrideGeoProviders(DynamicPropertyRegistry registry) {
        registry.add("app.geo.provider-a-url", () -> "http://127.0.0.1:1");
        registry.add("app.geo.provider-b-url", () -> "http://127.0.0.1:" + PROVIDER_B_STUB.getAddress().getPort());
    }
    private WidgetResponse createWidgetAsNewOwner() throws Exception {
        String email = TestSupport.uniqueEmail();
        String signupBody = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}";
        MvcResult signupResult = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readValue(signupResult.getResponse().getContentAsString(), AuthResponse.class).token();
        String widgetBody = "{\"type\": \"contact\", \"title\": \"Geo Fallback Test Widget\", \"fields\": [{\"name\": \"email\", \"label\": \"Email\", \"type\": \"email\", \"required\": true}]}";
        MvcResult widgetResult = mockMvc.perform(post("/widgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(widgetBody))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(widgetResult.getResponse().getContentAsString(), WidgetResponse.class);
    }
    @Test
    void providerAFailureFallsBackToProviderBAndEnrichesTheSubmission() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();
        String submissionBody = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"},\"formRenderedAt\":" + (System.currentTimeMillis() - 5000) + "}";
        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody))
                .andExpect(status().isCreated());
        List<Submission> stored = submissionRepository.findAll().stream()
                .filter(s -> s.getWidgetId().equals(widget.id()))
                .toList();
        assertEquals(1, stored.size());
        Submission submission = stored.get(0);
        assertEquals("ipapi.co", submission.getGeoProviderUsed());
        assertEquals("Testland", submission.getGeoCountry());
        assertEquals("Testville", submission.getGeoCity());
    }
}