package com.flyrank.capstone.integration;

import com.flyrank.capstone.dto.AuthResponse;
import com.flyrank.capstone.dto.SubmissionExportResponse;
import com.flyrank.capstone.dto.SubmissionResponse;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DoubleOptInIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private WidgetResponse createWidgetWithDoubleOptIn() throws Exception {
        return createWidget(true);
    }

    private WidgetResponse createWidgetWithoutDoubleOptIn() throws Exception {
        return createWidget(false);
    }

    private WidgetResponse createWidget(boolean requireDoubleOptIn) throws Exception {
        String email = TestSupport.uniqueEmail();
        String signupBody = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}";
        MvcResult signupResult = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readValue(signupResult.getResponse().getContentAsString(), AuthResponse.class).token();
        String widgetBody = "{\"type\": \"contact\", \"title\": \"Double Opt-In Test Widget\", \"fields\": [{\"name\": \"email\", \"label\": \"Email\", \"type\": \"email\", \"required\": true}], \"requireDoubleOptIn\": " + requireDoubleOptIn + "}";
        MvcResult widgetResult = mockMvc.perform(post("/widgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(widgetBody))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(widgetResult.getResponse().getContentAsString(), WidgetResponse.class);
    }

    @Test
    void submissionWithoutConsentIsRejectedWhenWidgetRequiresDoubleOptIn() throws Exception {
        WidgetResponse widget = createWidgetWithDoubleOptIn();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"}}";
        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submissionWithConsentIsStoredUnconfirmedThenConfirmEndpointMarksItConfirmed() throws Exception {
        WidgetResponse widget = createWidgetWithDoubleOptIn();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"},\"consent\":true}";
        MvcResult submitResult = mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        SubmissionResponse submitted = objectMapper.readValue(submitResult.getResponse().getContentAsString(), SubmissionResponse.class);
        assertFalse(submitted.confirmed());

        MvcResult confirmResult = mockMvc.perform(get("/submissions/" + submitted.id() + "/confirm"))
                .andExpect(status().isOk())
                .andReturn();
        SubmissionResponse confirmed = objectMapper.readValue(confirmResult.getResponse().getContentAsString(), SubmissionResponse.class);
        assertTrue(confirmed.confirmed());
        assertEquals(submitted.id(), confirmed.id());
    }

    @Test
    void exportEndpointReturnsTheVisitorsOwnData() throws Exception {
        WidgetResponse widget = createWidgetWithDoubleOptIn();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"},\"consent\":true}";
        MvcResult submitResult = mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        SubmissionResponse submitted = objectMapper.readValue(submitResult.getResponse().getContentAsString(), SubmissionResponse.class);

        MvcResult exportResult = mockMvc.perform(get("/submissions/" + submitted.id() + "/export"))
                .andExpect(status().isOk())
                .andReturn();
        SubmissionExportResponse export = objectMapper.readValue(exportResult.getResponse().getContentAsString(), SubmissionExportResponse.class);
        assertEquals(submitted.id(), export.id());
        assertEquals(widget.id(), export.widgetId());
        assertEquals("visitor@example.com", export.fields().get("email"));
        assertTrue(export.consentGiven());
        assertNotNull(export.consentAt());
        assertFalse(export.confirmed());
    }

    @Test
    void deleteEndpointRemovesTheSubmissionAndSubsequentExportReturnsNotFound() throws Exception {
        WidgetResponse widget = createWidgetWithDoubleOptIn();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"},\"consent\":true}";
        MvcResult submitResult = mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        SubmissionResponse submitted = objectMapper.readValue(submitResult.getResponse().getContentAsString(), SubmissionResponse.class);

        mockMvc.perform(delete("/submissions/" + submitted.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/submissions/" + submitted.id() + "/export"))
                .andExpect(status().isNotFound());
    }

    @Test
    void widgetsWithoutDoubleOptInAreAutoConfirmedAndDoNotNeedConsent() throws Exception {
        WidgetResponse widget = createWidgetWithoutDoubleOptIn();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"}}";
        MvcResult result = mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        SubmissionResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), SubmissionResponse.class);
        assertNotNull(response.id());
        assertTrue(response.confirmed());
    }
}