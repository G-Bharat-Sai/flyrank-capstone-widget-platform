package com.flyrank.capstone.controller;

import com.flyrank.capstone.dto.CreateWidgetRequest;
import com.flyrank.capstone.dto.WidgetResponse;
import com.flyrank.capstone.service.WidgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/widgets")
@RequiredArgsConstructor
public class WidgetController {

    private final WidgetService widgetService;

    @PostMapping
    public ResponseEntity<WidgetResponse> create(@Valid @RequestBody CreateWidgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(widgetService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<WidgetResponse>> listMine() {
        return ResponseEntity.ok(widgetService.listForCurrentOwner());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WidgetResponse> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(widgetService.getOne(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WidgetResponse> update(@PathVariable UUID id, @Valid @RequestBody CreateWidgetRequest request) {
        return ResponseEntity.ok(widgetService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        widgetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
