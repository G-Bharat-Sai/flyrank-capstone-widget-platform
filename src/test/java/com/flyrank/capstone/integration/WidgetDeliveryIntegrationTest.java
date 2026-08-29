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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WidgetDeliveryIntegrationTest {

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

        String widgetBody = "{\"type\": \"contact\", \"title\": \"Delivery Test Widget\", \"fields\": [{\"name\": \"email\", \"label\": \"Email\", \"type\": \"email\", \"required\": true}]}";
        MvcResult widgetResult = mockMvc.perform(post("/widgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(widgetBody))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(widgetResult.getResponse().getContentAsString(), WidgetResponse.class);
    }

    @Test
    void configEndpointIsPublicAndCached() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();

        mockMvc.perform(get("/widgets/" + widget.id() + "/config"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("Cache-Control", "max-age=300, public"));
    }

    @Test
    void versionedWidgetScriptServesCurrentVersion() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();

        mockMvc.perform(get("/widgets/" + widget.id() + "/widget.v1.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public"));
    }

    @Test
    void versionedWidgetScript404sForUnpublishedVersion() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();

        mockMvc.perform(get("/widgets/" + widget.id() + "/widget.v2.js"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unversionedWidgetScriptStillWorksForBackwardCompatibility() throws Exception {
        WidgetResponse widget = createWidgetAsNewOwner();

        mockMvc.perform(get("/widgets/" + widget.id() + "/widget.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=86400, public"));
    }

    @Test
    void invalidWidgetIdPathParameterReturnsCleanBadRequest() throws Exception {
        mockMvc.perform(get("/widgets/not-a-valid-uuid/config"))
                .andExpect(status().isBadRequest());
    }
}