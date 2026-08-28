package com.flyrank.capstone.service;

import com.flyrank.capstone.entity.Submission;
import com.flyrank.capstone.entity.Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebhookService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public void notify(Widget widget, Submission submission, Map<String, Object> fields) {
        String url = widget.getWebhookUrl();
        if (url == null || url.isBlank()) {
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "event", "submission.created",
                    "widgetId", widget.getId().toString(),
                    "submissionId", submission.getId().toString(),
                    "fields", fields,
                    "createdAt", submission.getCreatedAt().toString()
            );
            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "flyrank-capstone/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Webhook delivered for submission {} to {} (status {})", submission.getId(), url, response.statusCode());
            } else {
                log.warn("Webhook returned non-2xx for submission {} to {} (status {})", submission.getId(), url, response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Webhook delivery failed for submission {} to {}: {} - {}", submission.getId(), url, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
