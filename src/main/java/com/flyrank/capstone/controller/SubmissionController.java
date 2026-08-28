package com.flyrank.capstone.controller;

import com.flyrank.capstone.dto.SubmissionRequest;
import com.flyrank.capstone.dto.SubmissionResponse;
import com.flyrank.capstone.service.SubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody SubmissionRequest request, HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        Optional<SubmissionResponse> response = submissionService.submit(request, ip);

        if (response.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "received"));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response.get());
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
