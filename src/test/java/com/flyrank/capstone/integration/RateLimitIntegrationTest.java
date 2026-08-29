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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private WidgetResponse createWidgetAsNewOwner() throws Exception {
        String email = TestSupport.uniqueEmail();
        String signupBody = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}";
        MvcResult signupResult = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readValue(signupResult.getResponse().getContentAsString(), AuthResponse.class).token();

        String widgetBody = "{\"type\": \"contact\", \"title\": \"Rate Limit Test Widget\", \"fields\": [{\"name\": \"email\", \"label\": \"Email\", \"type\": \"email\", \"required\": true}]}";
        MvcResult widgetResult = mockMvc.perform(post("/widgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(widgetBody))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(widgetResult.getResponse().getContentAsString(), WidgetResponse.class);
    }

    @Test
    void burstOfRequestsFromOneIpGetsRateLimitedAfterFive() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();
        String ip = TestSupport.uniqueIp();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"}}";

        int successCount = 0;
        int rateLimitedCount = 0;
        for (int i = 0; i < 8; i++) {
            MvcResult result = mockMvc.perform(post("/submissions")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
            int statusCode = result.getResponse().getStatus();
            if (statusCode == 201) {
                successCount++;
            } else if (statusCode == 429) {
                rateLimitedCount++;
            }
        }

        assertEquals(5, successCount);
        assertEquals(3, rateLimitedCount);
    }

    @Test
    void aDifferentIpIsNotAffectedByAnotherIpsRateLimit() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();
        String exhaustedIp = TestSupport.uniqueIp();
        String freshIp = TestSupport.uniqueIp();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"}}";

        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/submissions")
                            .header("X-Forwarded-For", exhaustedIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
        }

        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", freshIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}