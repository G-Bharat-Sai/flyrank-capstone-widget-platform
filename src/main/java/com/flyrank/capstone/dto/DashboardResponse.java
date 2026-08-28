package com.flyrank.capstone.dto;
import java.util.List;
public record DashboardResponse(
    int totalWidgets,
    long totalSubmissions,
    long submissionsLast7Days,
    long submissionsLast30Days,
    List<WidgetStatDto> perWidget,
    List<CountryStatDto> byCountry,
    List<DailyStatDto> dailyCounts
) {}