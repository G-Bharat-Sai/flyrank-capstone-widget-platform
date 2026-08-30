package com.flyrank.capstone.controller;
import com.flyrank.capstone.dto.DashboardResponse;
import com.flyrank.capstone.service.DashboardService;
import com.flyrank.capstone.service.SubmissionEventBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;
    private final SubmissionEventBroadcaster submissionEventBroadcaster;
    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSubmissions() {
        UUID ownerId = dashboardService.currentOwnerId();
        return submissionEventBroadcaster.subscribe(ownerId);
    }
}