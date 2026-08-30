package com.flyrank.capstone.service;
import com.flyrank.capstone.dto.CountryStatDto;
import com.flyrank.capstone.dto.DailyStatDto;
import com.flyrank.capstone.dto.DashboardResponse;
import com.flyrank.capstone.dto.WidgetStatDto;
import com.flyrank.capstone.entity.Owner;
import com.flyrank.capstone.repository.OwnerRepository;
import com.flyrank.capstone.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final OwnerRepository ownerRepository;
    private final SubmissionRepository submissionRepository;
    private Owner currentOwner() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ownerRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner not found"));
    }
    public UUID currentOwnerId() {
        return currentOwner().getId();
    }
    public DashboardResponse getDashboard() {
        Owner owner = currentOwner();
        UUID ownerId = owner.getId();
        List<WidgetStatDto> perWidget = submissionRepository.countSubmissionsPerWidget(ownerId).stream()
                .map(w -> new WidgetStatDto(w.getWidgetId(), w.getTitle(), w.getSubmissionCount()))
                .toList();
        List<CountryStatDto> byCountry = submissionRepository.countSubmissionsByCountry(ownerId).stream()
                .map(c -> new CountryStatDto(c.getCountry(), c.getCount()))
                .toList();
        OffsetDateTime since30 = OffsetDateTime.now().minusDays(30);
        List<DailyStatDto> dailyCounts = submissionRepository.countSubmissionsPerDay(ownerId, since30).stream()
                .map(d -> new DailyStatDto(d.getDay(), d.getCount()))
                .toList();
        long totalSubmissions = submissionRepository.countByOwnerId(ownerId);
        OffsetDateTime since7 = OffsetDateTime.now().minusDays(7);
        long last7 = submissionRepository.countByOwnerIdAndCreatedAtAfter(ownerId, since7);
        long last30 = submissionRepository.countByOwnerIdAndCreatedAtAfter(ownerId, since30);
        return new DashboardResponse(
                perWidget.size(),
                totalSubmissions,
                last7,
                last30,
                perWidget,
                byCountry,
                dailyCounts
        );
    }
}