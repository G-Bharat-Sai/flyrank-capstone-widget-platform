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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WidgetControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String signUpAndGetToken() throws Exception {
        String email = TestSupport.uniqueEmail();
        String body = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}";
        MvcResult result = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return response.token();
    }

    private WidgetResponse createWidget(String token) throws Exception {
        String body = """
                {
                  "type": "contact",
                  "title": "Test Widget",
                  "fields": [
                    {"name": "email", "label": "Email", "type": "email", "required": true}
                  ],
                  "buttonText": "Send"
                }
                """;
        MvcResult result = mockMvc.perform(post("/widgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), WidgetResponse.class);
    }

    @Test
    void listingWidgetsWithoutAuthIsRejected() throws Exception {
        mockMvc.perform(get("/widgets"))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingWidgetWithoutAuthIsRejected() throws Exception {
        String body = "{\"type\": \"contact\", \"title\": \"No Auth Widget\", \"fields\": []}";
        mockMvc.perform(post("/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanCreateAndReadTheirOwnWidget() throws Exception {
        String token = signUpAndGetToken();
        WidgetResponse widget = createWidget(token);

        assertNotNull(widget.id());
        assertTrue(widget.embedSnippet().contains("widget.v1.js"));
        assertTrue(widget.embedSnippet().contains(widget.id().toString()));

        mockMvc.perform(get("/widgets/" + widget.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void creatingWidgetWithMissingTitleIsRejected() throws Exception {
        String token = signUpAndGetToken();
        String body = "{\"type\": \"contact\", \"fields\": [{\"name\": \"email\", \"label\": \"Email\", \"type\": \"email\", \"required\": true}]}";
        mockMvc.perform(post("/widgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerBCannotReadUpdateOrDeleteOwnerAsWidget() throws Exception {
        String tokenA = signUpAndGetToken();
        WidgetResponse widget = createWidget(tokenA);

        String tokenB = signUpAndGetToken();

        mockMvc.perform(get("/widgets/" + widget.id())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        String updateBody = "{\"type\": \"contact\", \"title\": \"Hijacked\", \"fields\": [{\"name\": \"email\", \"label\": \"Email\", \"type\": \"email\", \"required\": true}]}";
        mockMvc.perform(put("/widgets/" + widget.id())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/widgets/" + widget.id())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/widgets/" + widget.id())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void updatingWidgetIncrementsVersion() throws Exception {
        String token = signUpAndGetToken();
        WidgetResponse widget = createWidget(token);
        assertEquals(1, widget.version());

        String updateBody = "{\"type\": \"contact\", \"title\": \"Updated Title\", \"fields\": [{\"name\": \"email\", \"label\": \"Email\", \"type\": \"email\", \"required\": true}]}";
        MvcResult result = mockMvc.perform(put("/widgets/" + widget.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andReturn();
        WidgetResponse updated = objectMapper.readValue(result.getResponse().getContentAsString(), WidgetResponse.class);
        assertEquals(2, updated.version());
        assertEquals("Updated Title", updated.title());
    }

    @Test
    void deletingWidgetRemovesItForItsOwner() throws Exception {
        String token = signUpAndGetToken();
        WidgetResponse widget = createWidget(token);

        mockMvc.perform(delete("/widgets/" + widget.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/widgets/" + widget.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}