package com.flyrank.capstone.service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.flyrank.capstone.dto.CreateWidgetRequest;
import com.flyrank.capstone.dto.WidgetFieldDto;
import com.flyrank.capstone.dto.WidgetResponse;
import com.flyrank.capstone.entity.Owner;
import com.flyrank.capstone.entity.Widget;
import com.flyrank.capstone.repository.OwnerRepository;
import com.flyrank.capstone.repository.WidgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class WidgetService {
    private static final Set<String> ALLOWED_TYPES = Set.of("signup", "contact", "popover");
    private final WidgetRepository widgetRepository;
    private final OwnerRepository ownerRepository;
    private final ObjectMapper objectMapper;
    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;
    private Owner currentOwner() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ownerRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner not found"));
    }
    public WidgetResponse create(CreateWidgetRequest request) {
        validateType(request.type());
        Owner owner = currentOwner();
        Widget widget = Widget.builder()
                .ownerId(owner.getId())
                .type(request.type())
                .title(request.title())
                .description(request.description())
                .fields(writeJson(request.fields()))
                .buttonText(request.buttonText() != null ? request.buttonText() : "Submit")
                .displayOptions(request.displayOptions() != null ? writeJson(request.displayOptions()) : null)
                .webhookUrl(request.webhookUrl())
                .version(1)
                .build();
        widgetRepository.save(widget);
        return toResponse(widget);
    }
    public List<WidgetResponse> listForCurrentOwner() {
        Owner owner = currentOwner();
        return widgetRepository.findAllByOwnerId(owner.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }
    public WidgetResponse getOne(UUID id) {
        Widget widget = findOwnedWidget(id);
        return toResponse(widget);
    }
    public WidgetResponse update(UUID id, CreateWidgetRequest request) {
        validateType(request.type());
        Widget widget = findOwnedWidget(id);
        widget.setType(request.type());
        widget.setTitle(request.title());
        widget.setDescription(request.description());
        widget.setFields(writeJson(request.fields()));
        widget.setButtonText(request.buttonText() != null ? request.buttonText() : "Submit");
        widget.setDisplayOptions(request.displayOptions() != null ? writeJson(request.displayOptions()) : null);
        widget.setWebhookUrl(request.webhookUrl());
        widget.setVersion(widget.getVersion() + 1);
        widgetRepository.save(widget);
        return toResponse(widget);
    }
    public void delete(UUID id) {
        Widget widget = findOwnedWidget(id);
        widgetRepository.delete(widget);
    }
    private Widget findOwnedWidget(UUID id) {
        Owner owner = currentOwner();
        return widgetRepository.findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Widget not found"));
    }
    private void validateType(String type) {
        if (!ALLOWED_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be one of " + ALLOWED_TYPES);
        }
    }
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid field data");
        }
    }
    private List<WidgetFieldDto> readFields(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<WidgetFieldDto>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> readDisplayOptions(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
    private WidgetResponse toResponse(Widget widget) {
        String embedSnippet = String.format(
                "<script src=\"%s/widgets/%s/widget.js\" data-widget-id=\"%s\"></script>",
                baseUrl, widget.getId(), widget.getId()
        );
        return new WidgetResponse(
                widget.getId(),
                widget.getType(),
                widget.getTitle(),
                widget.getDescription(),
                readFields(widget.getFields()),
                widget.getButtonText(),
                readDisplayOptions(widget.getDisplayOptions()),
                widget.getWebhookUrl(),
                widget.getVersion(),
                embedSnippet,
                widget.getCreatedAt(),
                widget.getUpdatedAt()
        );
    }
}