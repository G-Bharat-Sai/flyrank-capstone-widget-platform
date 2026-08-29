package com.flyrank.capstone.integration;

import com.flyrank.capstone.dto.AuthResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signupCreatesOwnerAndReturnsToken() throws Exception {
        String email = TestSupport.uniqueEmail();
        String body = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}";

        MvcResult result = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertEquals(email, response.email());
        assertNotNull(response.token());
        assertTrue(response.token().length() > 10);
    }

    @Test
    void signupWithAlreadyRegisteredEmailIsRejected() throws Exception {
        String email = TestSupport.uniqueEmail();
        String body = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}";

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void signupWithInvalidEmailIsRejected() throws Exception {
        String body = "{\"email\":\"not-an-email\",\"password\":\"correct-horse-battery\"}";

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupWithShortPasswordIsRejected() throws Exception {
        String body = "{\"email\":\"" + TestSupport.uniqueEmail() + "\",\"password\":\"short\"}";

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithCorrectPasswordSucceeds() throws Exception {
        String email = TestSupport.uniqueEmail();
        String signupBody = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}";
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated());

        String loginBody = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}";
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(response.token());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        String email = TestSupport.uniqueEmail();
        String signupBody = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}";
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated());

        String loginBody = "{\"email\":\"" + email + "\",\"password\":\"totally-wrong\"}";
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());
    }
}