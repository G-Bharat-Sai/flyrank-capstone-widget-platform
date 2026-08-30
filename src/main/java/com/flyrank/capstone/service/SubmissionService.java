package com.flyrank.capstone.service;
import tools.jackson.databind.ObjectMapper;
import com.flyrank.capstone.dto.SubmissionExportResponse;
import com.flyrank.capstone.dto.SubmissionRequest;
import com.flyrank.capstone.dto.SubmissionResponse;
import com.flyrank.capstone.entity.Submission;
import com.flyrank.capstone.entity.Widget;
import com.flyrank.capstone.repository.SubmissionRepository;
import com.flyrank.capstone.repository.WidgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class SubmissionService {
    private static final int MAX_FIELD_VALUE_LENGTH = 5_000;
    private static final int MAX_PAYLOAD_BYTES = 100_000;
    private static final long MIN_FILL_TIME_MILLIS = 1_500;
    private final WidgetRepository widgetRepository;
    private final SubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;
    private final GeoEnrichmentService geoEnrichmentService;
    private final WebhookService webhookService;
    private final SubmissionEventBroadcaster submissionEventBroadcaster;
    private final PowChallengeService powChallengeService;
    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;
    public Optional<SubmissionResponse> submit(SubmissionRequest request, String ipAddress, String idempotencyKey) {
        Widget widget = widgetRepository.findById(request.widgetId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Widget not found"));
        boolean hasIdempotencyKey = idempotencyKey != null && !idempotencyKey.isBlank();
        if (hasIdempotencyKey) {
            Optional<Submission> existing = submissionRepository.findByWidgetIdAndIdempotencyKey(widget.getId(), idempotencyKey);
            if (existing.isPresent()) {
                Submission s = existing.get();
                return Optional.of(new SubmissionResponse(s.getId(), s.getWidgetId(), s.getCreatedAt(), s.getConfirmedAt() != null));
            }
        }
        if (request.honeypot() != null && !request.honeypot().isBlank()) {
            return Optional.empty();
        }
        if (request.formRenderedAt() != null) {
            long fillTimeMillis = System.currentTimeMillis() - request.formRenderedAt();
            if (fillTimeMillis >= 0 && fillTimeMillis < MIN_FILL_TIME_MILLIS) {
                return Optional.empty();
            }
        }
        if (widget.isRequireProofOfWork()) {
            if (!powChallengeService.verifyAndConsume(request.challengeId(), request.challengeNonce())) {
                return Optional.empty();
            }
        }
        if (widget.isRequireDoubleOptIn() && !Boolean.TRUE.equals(request.consent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consent is required for this widget");
        }
        validateFieldsAgainstWidgetSchema(widget, request.fields());
        String payloadJson = writeJson(request.fields());
        if (payloadJson.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Submission payload exceeds maximum size of " + MAX_PAYLOAD_BYTES + " bytes");
        }
        Submission.SubmissionBuilder builder = Submission.builder()
                .widgetId(widget.getId())
                .ownerId(widget.getOwnerId())
                .payload(payloadJson)
                .ipAddress(ipAddress)
                .idempotencyKey(hasIdempotencyKey ? idempotencyKey : null);
        if (widget.isRequireDoubleOptIn()) {
            builder.consentGiven(true).consentAt(OffsetDateTime.now());
        } else {
            builder.confirmedAt(OffsetDateTime.now());
        }
        geoEnrichmentService.lookup(ipAddress).ifPresent(geo -> {
            builder.geoCountry(geo.country());
            builder.geoCity(geo.city());
            builder.geoProviderUsed(geo.provider());
        });
        Submission submission = builder.build();
        try {
            submissionRepository.save(submission);
        } catch (DataIntegrityViolationException e) {
            if (!hasIdempotencyKey) {
                throw e;
            }
            Submission winner = submissionRepository.findByWidgetIdAndIdempotencyKey(widget.getId(), idempotencyKey)
                    .orElseThrow(() -> e);
            return Optional.of(new SubmissionResponse(winner.getId(), winner.getWidgetId(), winner.getCreatedAt(), winner.getConfirmedAt() != null));
        }
        webhookService.notify(widget, submission, request.fields());
        if (widget.isRequireDoubleOptIn()) {
            logConfirmationEmail(request, submission);
        }
        SubmissionResponse response = new SubmissionResponse(submission.getId(), submission.getWidgetId(), submission.getCreatedAt(), submission.getConfirmedAt() != null);
        submissionEventBroadcaster.publishNewSubmission(widget.getOwnerId(), response, widget.getTitle());
        return Optional.of(response);
    }
    public SubmissionResponse confirm(UUID id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
        if (submission.getConfirmedAt() == null) {
            submission.setConfirmedAt(OffsetDateTime.now());
            submissionRepository.save(submission);
        }
        return new SubmissionResponse(submission.getId(), submission.getWidgetId(), submission.getCreatedAt(), true);
    }
    public SubmissionExportResponse export(UUID id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
        return new SubmissionExportResponse(
                submission.getId(),
                submission.getWidgetId(),
                readPayload(submission.getPayload()),
                submission.getCreatedAt(),
                submission.getConfirmedAt() != null,
                submission.getConfirmedAt(),
                submission.isConsentGiven(),
                submission.getConsentAt(),
                submission.getGeoCountry(),
                submission.getGeoCity()
        );
    }
    public void deleteSubmission(UUID id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
        submissionRepository.delete(submission);
    }
    @SuppressWarnings("unchecked")
    private void validateFieldsAgainstWidgetSchema(Widget widget, Map<String, Object> submittedFields) {
        List<Map<String, Object>> schemaFields;
        try {
            schemaFields = objectMapper.readValue(widget.getFields(), List.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Widget configuration is corrupt");
        }
        for (Map<String, Object> field : schemaFields) {
            Object requiredObj = field.get("required");
            boolean required = requiredObj instanceof Boolean b && b;
            String name = String.valueOf(field.get("name"));
            if (required) {
                Object value = submittedFields.get(name);
                if (value == null || String.valueOf(value).isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required field: " + name);
                }
            }
        }
        if (submittedFields.size() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many fields submitted");
        }
        for (Map.Entry<String, Object> entry : submittedFields.entrySet()) {
            String value = String.valueOf(entry.getValue());
            if (value.length() > MAX_FIELD_VALUE_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field '" + entry.getKey() + "' exceeds maximum length of " + MAX_FIELD_VALUE_LENGTH + " characters");
            }
        }
    }
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid submission data");
        }
    }
    private void logConfirmationEmail(SubmissionRequest request, Submission submission) {
        Object emailValue = request.fields().get("email");
        String email = emailValue != null ? String.valueOf(emailValue) : "(no email field on this widget)";
        System.out.println("[ConfirmationEmail] To: " + email
                + " | Subject: Please confirm your submission"
                + " | Body: Click to confirm: " + baseUrl + "/submissions/" + submission.getId() + "/confirm");
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}