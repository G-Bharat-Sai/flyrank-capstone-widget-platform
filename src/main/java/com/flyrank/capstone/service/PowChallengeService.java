package com.flyrank.capstone.service;

import com.flyrank.capstone.dto.ChallengeResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PowChallengeService {

    private static final int DIFFICULTY = 4;
    private static final long CHALLENGE_TTL_MILLIS = 2 * 60 * 1000;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, ChallengeRecord> challenges = new ConcurrentHashMap<>();

    public ChallengeResponse issueChallenge() {
        pruneExpired();
        String id = UUID.randomUUID().toString();
        byte[] seedBytes = new byte[16];
        secureRandom.nextBytes(seedBytes);
        String seed = Base64.getUrlEncoder().withoutPadding().encodeToString(seedBytes);
        challenges.put(id, new ChallengeRecord(seed, Instant.now().plusMillis(CHALLENGE_TTL_MILLIS)));
        return new ChallengeResponse(id, seed, DIFFICULTY);
    }

    public boolean verifyAndConsume(String challengeId, String nonce) {
        if (challengeId == null || nonce == null) {
            return false;
        }
        ChallengeRecord record = challenges.get(challengeId);
        if (record == null) {
            return false;
        }
        if (Instant.now().isAfter(record.expiresAt())) {
            challenges.remove(challengeId);
            return false;
        }
        String hash = sha256Hex(record.seed() + nonce);
        if (!hasLeadingZeroHexDigits(hash, DIFFICULTY)) {
            return false;
        }
        challenges.remove(challengeId);
        return true;
    }

    private void pruneExpired() {
        Instant now = Instant.now();
        challenges.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    private boolean hasLeadingZeroHexDigits(String hexHash, int count) {
        if (hexHash.length() < count) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            if (hexHash.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record ChallengeRecord(String seed, Instant expiresAt) {}
}