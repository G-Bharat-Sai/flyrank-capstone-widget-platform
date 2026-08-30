package com.flyrank.capstone.dto;
public record ChallengeResponse(
    String challengeId,
    String seed,
    int difficulty
) {}