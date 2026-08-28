package com.flyrank.capstone.service;
import tools.jackson.databind.ObjectMapper;
import com.flyrank.capstone.dto.SubmissionRequest;
import com.flyrank.capstone.dto.SubmissionResponse;
import com.flyrank.capstone.entity.Submission;
import com.flyrank.capstone.entity.Widget;
import com.flyrank.capstone.repository.SubmissionRepository;
import com.flyrank.capstone.repository.WidgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
@Service
@RequiredArgsConstructor
public class SubmissionService {
    private final WidgetRepository widgetRepository;
    private final SubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;
    private final GeoEnrichmentService geoEnrichmentService;
    private final WebhookService webhookService;
    public Optional<SubmissionResponse> submit(SubmissionRequest request, String ipAddress) {
        Widget widget = widgetRepository.findById(request.widgetId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Widget not found"));
        if (request.honeypot() != null && !request.honeypot().isBlank()) {
            return Optional.empty();
        }
        validateFieldsAgainstWidgetSchema(widget, request.fields());
        Submission.SubmissionBuilder builder = Submission.builder()
                .widgetId(widget.getId())
                .ownerId(widget.getOwnerId())
                .payload(writeJson(request.fields()))
                .ipAddress(ipAddress);
        geoEnrichmentService.lookup(ipAddress).ifPresent(geo -> {
            builder.geoCountry(geo.country());
            builder.geoCity(geo.city());
            builder.geoProviderUsed(geo.provider());
        });
        Submission submission = builder.build();
        submissionRepository.save(submission);
        webhookService.notify(widget, submission, request.fields());
        return Optional.of(new SubmissionResponse(submission.getId(), submission.getWidgetId(), submission.getCreatedAt()));
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
    }
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid submission data");
        }
    }
}