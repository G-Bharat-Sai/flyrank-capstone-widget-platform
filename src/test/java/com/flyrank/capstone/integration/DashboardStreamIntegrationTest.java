package com.flyrank.capstone.integration;
import com.flyrank.capstone.dto.AuthResponse;
import com.flyrank.capstone.dto.WidgetResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest
@AutoConfigureMockMvc
class DashboardStreamIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    private record OwnerContext(String token, WidgetResponse widget) {}
    private OwnerContext createWidgetAsNewOwner() throws Exception {
        String email = TestSupport.uniqueEmail();
        String signupBody = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}";
        MvcResult signupResult = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readValue(signupResult.getResponse().getContentAsString(), AuthResponse.class).token();
        String widgetBody = "{\"type\": \"contact\", \"title\": \"Stream Test Widget\", \"fields\": [{\"name\": \"email\", \"label\": \"Email\", \"type\": \"email\", \"required\": true}]}";
        MvcResult widgetResult = mockMvc.perform(post("/widgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(widgetBody))
                .andExpect(status().isCreated())
                .andReturn();
        WidgetResponse widget = objectMapper.readValue(widgetResult.getResponse().getContentAsString(), WidgetResponse.class);
        return new OwnerContext(token, widget);
    }
    @Test
    void streamingWithoutAuthIsRejected() throws Exception {
        mockMvc.perform(get("/dashboard/stream"))
                .andExpect(status().isForbidden());
    }
    @Test
    void ownerReceivesLiveEventWhenANewSubmissionArrivesForTheirWidget() throws Exception {
        OwnerContext ctx = createWidgetAsNewOwner();
        MvcResult streamResult = mockMvc.perform(get("/dashboard/stream")
                        .header("Authorization", "Bearer " + ctx.token()))
                .andExpect(request().asyncStarted())
                .andReturn();
        long renderedAt = System.currentTimeMillis() - 5000;
        String submissionBody = "{\"widgetId\":\"" + ctx.widget().id() + "\",\"fields\":{\"email\":\"visitor@example.com\"},\"formRenderedAt\":" + renderedAt + "}";
        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionBody))
                .andExpect(status().isCreated());
        String streamed = streamResult.getResponse().getContentAsString();
        assertTrue(streamed.contains("connected"));
        assertTrue(streamed.contains("submission"));
        assertTrue(streamed.contains(ctx.widget().id().toString()));
    }
}