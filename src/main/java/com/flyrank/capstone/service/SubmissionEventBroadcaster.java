package com.flyrank.capstone.service;

import com.flyrank.capstone.dto.SubmissionResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SubmissionEventBroadcaster {

    private static final long EMITTER_TIMEOUT_MILLIS = 30L * 60 * 1000;

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByOwner = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID ownerId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        CopyOnWriteArrayList<SseEmitter> emitters =
                emittersByOwner.computeIfAbsent(ownerId, key -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> remove(ownerId, emitter));
        emitter.onTimeout(() -> remove(ownerId, emitter));
        emitter.onError(ex -> remove(ownerId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok", MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            remove(ownerId, emitter);
        }
        return emitter;
    }

    public void publishNewSubmission(UUID ownerId, SubmissionResponse submission, String widgetTitle) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByOwner.get(ownerId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "submissionId", submission.id(),
                "widgetId", submission.widgetId(),
                "widgetTitle", widgetTitle,
                "createdAt", submission.createdAt().toString()
        );
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("submission").data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    private void remove(UUID ownerId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByOwner.get(ownerId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}