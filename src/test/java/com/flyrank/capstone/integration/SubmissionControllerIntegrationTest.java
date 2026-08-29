package com.flyrank.capstone.integration;

import com.flyrank.capstone.dto.AuthResponse;
import com.flyrank.capstone.dto.SubmissionResponse;
import com.flyrank.capstone.dto.WidgetResponse;
import com.flyrank.capstone.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SubmissionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubmissionRepository submissionRepository;

    private WidgetResponse createWidgetAsNewOwner() throws Exception {
        String email = TestSupport.uniqueEmail();
        String signupBody = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}";
        MvcResult signupResult = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readValue(signupResult.getResponse().getContentAsString(), AuthResponse.class).token();

        String widgetBody = "{\"type\": \"contact\", \"title\": \"Submission Test Widget\", \"fields\": [{\"name\": \"email\", \"label\": \"Email\", \"type\": \"email\", \"required\": true}]}";
        MvcResult widgetResult = mockMvc.perform(post("/widgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(widgetBody))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(widgetResult.getResponse().getContentAsString(), WidgetResponse.class);
    }

    @Test
    void validSubmissionIsStored() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"}}";

        MvcResult result = mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        SubmissionResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), SubmissionResponse.class);
        assertEquals(widget.id(), response.widgetId());
        assertNotNull(response.id());
    }

    @Test
    void submissionMissingWidgetIdIsRejected() throws Exception {
        String body = "{\"fields\":{\"email\":\"visitor@example.com\"}}";

        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonBodyReturnsCleanBadRequest() throws Exception {
        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submissionToNonexistentWidgetIs404() throws Exception {
        String body = "{\"widgetId\":\"00000000-0000-0000-0000-000000000000\",\"fields\":{\"email\":\"visitor@example.com\"}}";

        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void oversizedSingleFieldIsRejected() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();
        String hugeValue = "a".repeat(6000);
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"" + hugeValue + "\"}}";

        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedAggregatePayloadIsRejected() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();
        StringBuilder fields = new StringBuilder("{\"email\":\"visitor@example.com\"");
        for (int i = 0; i < 25; i++) {
            fields.append(",\"field").append(i).append("\":\"").append("x".repeat(4500)).append("\"");
        }
        fields.append("}");
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":" + fields + "}";

        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void honeypotFilledSilentlyDropsTheSubmission() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"},\"honeypot\":\"i-am-a-bot\"}";

        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        long storedForThisWidget = submissionRepository.findAll().stream()
                .filter(s -> s.getWidgetId().equals(widget.id()))
                .count();
        assertEquals(0, storedForThisWidget);
    }

    @Test
    void repeatingIdempotencyKeyReturnsTheSameSubmission() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"}}";
        String key = "test-key-" + java.util.UUID.randomUUID();
        String ip = TestSupport.uniqueIp();

        MvcResult first = mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", ip)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        SubmissionResponse firstResponse = objectMapper.readValue(first.getResponse().getContentAsString(), SubmissionResponse.class);

        MvcResult second = mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", ip)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        SubmissionResponse secondResponse = objectMapper.readValue(second.getResponse().getContentAsString(), SubmissionResponse.class);

        assertEquals(firstResponse.id(), secondResponse.id());
    }

    @Test
    void differentIdempotencyKeyCreatesANewSubmission() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"}}";
        String ip = TestSupport.uniqueIp();

        MvcResult first = mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", ip)
                        .header("Idempotency-Key", "key-one-" + java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        SubmissionResponse firstResponse = objectMapper.readValue(first.getResponse().getContentAsString(), SubmissionResponse.class);

        MvcResult second = mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", ip)
                        .header("Idempotency-Key", "key-two-" + java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        SubmissionResponse secondResponse = objectMapper.readValue(second.getResponse().getContentAsString(), SubmissionResponse.class);

        assertNotEquals(firstResponse.id(), secondResponse.id());
    }

    @Test
    void submissionSubmittedTooQuicklyAfterFormRenderIsSilentlyDropped() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();
        long renderedAt = System.currentTimeMillis();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"},\"formRenderedAt\":" + renderedAt + "}";

        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        long storedForThisWidget = submissionRepository.findAll().stream()
                .filter(s -> s.getWidgetId().equals(widget.id()))
                .count();
        assertEquals(0, storedForThisWidget);
    }

    @Test
    void submissionSubmittedAfterAReasonableFillTimeIsStored() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();
        long renderedAt = System.currentTimeMillis() - 5000;
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"},\"formRenderedAt\":" + renderedAt + "}";

        MvcResult result = mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        SubmissionResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), SubmissionResponse.class);
        assertNotNull(response.id());
    }
}