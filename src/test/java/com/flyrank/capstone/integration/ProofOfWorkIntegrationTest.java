package com.flyrank.capstone.integration;

import com.flyrank.capstone.dto.AuthResponse;
import com.flyrank.capstone.dto.ChallengeResponse;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProofOfWorkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubmissionRepository submissionRepository;

    private WidgetResponse createWidgetWithProofOfWork() throws Exception {
        return createWidget(true);
    }

    private WidgetResponse createWidgetWithoutProofOfWork() throws Exception {
        return createWidget(false);
    }

    private WidgetResponse createWidget(boolean requireProofOfWork) throws Exception {
        String email = TestSupport.uniqueEmail();
        String signupBody = "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}";
        MvcResult signupResult = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readValue(signupResult.getResponse().getContentAsString(), AuthResponse.class).token();
        String widgetBody = "{\"type\": \"contact\", \"title\": \"PoW Test Widget\", \"fields\": [{\"name\": \"email\", \"label\": \"Email\", \"type\": \"email\", \"required\": true}], \"requireProofOfWork\": " + requireProofOfWork + "}";
        MvcResult widgetResult = mockMvc.perform(post("/widgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(widgetBody))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(widgetResult.getResponse().getContentAsString(), WidgetResponse.class);
    }

    private ChallengeResponse fetchChallenge(String ip) throws Exception {
        MvcResult result = mockMvc.perform(get("/submissions/challenge")
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ChallengeResponse.class);
    }

    private String solveChallenge(String seed, int difficulty) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String prefix = "0".repeat(difficulty);
        long attempt = 0;
        while (true) {
            String nonce = String.valueOf(attempt);
            byte[] hashBytes = digest.digest((seed + nonce).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            if (hex.toString().startsWith(prefix)) {
                return nonce;
            }
            attempt++;
        }
    }

    private long countSubmissionsForWidget(java.util.UUID widgetId) {
        return submissionRepository.findAll().stream()
                .filter(s -> s.getWidgetId().equals(widgetId))
                .count();
    }

    @Test
    void submissionWithoutSolvingChallengeIsSilentlyDroppedWhenWidgetRequiresProofOfWork() throws Exception {
        WidgetResponse widget = createWidgetWithProofOfWork();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"}}";
        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        assertEquals(0, countSubmissionsForWidget(widget.id()));
    }

    @Test
    void submissionWithACorrectlySolvedChallengeIsStored() throws Exception {
        WidgetResponse widget = createWidgetWithProofOfWork();
        String ip = TestSupport.uniqueIp();
        ChallengeResponse challenge = fetchChallenge(ip);

        long start = System.nanoTime();
        String nonce = solveChallenge(challenge.seed(), challenge.difficulty());
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("[ProofOfWorkIntegrationTest] solved difficulty " + challenge.difficulty()
                + " challenge in " + elapsedMillis + " ms");

        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"}"
                + ",\"challengeId\":\"" + challenge.challengeId() + "\",\"challengeNonce\":\"" + nonce + "\"}";
        MvcResult result = mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        SubmissionResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), SubmissionResponse.class);
        assertNotNull(response.id());
        assertEquals(1, countSubmissionsForWidget(widget.id()));
    }

    @Test
    void aSolvedChallengeCannotBeReplayedForASecondSubmission() throws Exception {
        WidgetResponse widget = createWidgetWithProofOfWork();
        String ip = TestSupport.uniqueIp();
        ChallengeResponse challenge = fetchChallenge(ip);
        String nonce = solveChallenge(challenge.seed(), challenge.difficulty());

        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"}"
                + ",\"challengeId\":\"" + challenge.challengeId() + "\",\"challengeNonce\":\"" + nonce + "\"}";

        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        assertEquals(1, countSubmissionsForWidget(widget.id()));

        mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        assertEquals(1, countSubmissionsForWidget(widget.id()));
    }

    @Test
    void widgetsWithoutProofOfWorkRequiredDoNotNeedAChallenge() throws Exception {
        WidgetResponse widget = createWidgetWithoutProofOfWork();
        String body = "{\"widgetId\":\"" + widget.id() + "\",\"fields\":{\"email\":\"visitor@example.com\"}}";
        MvcResult result = mockMvc.perform(post("/submissions")
                        .header("X-Forwarded-For", TestSupport.uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        SubmissionResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), SubmissionResponse.class);
        assertNotNull(response.id());
        assertEquals(1, countSubmissionsForWidget(widget.id()));
    }
}