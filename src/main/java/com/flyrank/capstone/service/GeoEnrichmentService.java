package com.flyrank.capstone.service;

import com.flyrank.capstone.dto.GeoInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
public class GeoEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(GeoEnrichmentService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String providerAUrl;
    private final String providerBUrl;

    public GeoEnrichmentService(ObjectMapper objectMapper,
                                 @Value("${app.geo.provider-a-url}") String providerAUrl,
                                 @Value("${app.geo.provider-b-url}") String providerBUrl) {
        this.objectMapper = objectMapper;
        this.providerAUrl = providerAUrl;
        this.providerBUrl = providerBUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public Optional<GeoInfo> lookup(String ipAddress) {
        Optional<GeoInfo> fromA = tryProviderA(ipAddress);
        if (fromA.isPresent()) {
            return fromA;
        }
        Optional<GeoInfo> fromB = tryProviderB(ipAddress);
        if (fromB.isPresent()) {
            return fromB;
        }
        log.warn("Geo enrichment: both providers failed or returned no usable data for ip={}", ipAddress);
        return Optional.empty();
    }

    private Optional<GeoInfo> tryProviderA(String ipAddress) {
        try {
            String url = providerAUrl + "/" + ipAddress;
            Map<String, Object> body = fetchJson(url);

            if (body == null || !"success".equals(body.get("status"))) {
                return Optional.empty();
            }

            String country = String.valueOf(body.get("country"));
            String city = String.valueOf(body.get("city"));
            return Optional.of(new GeoInfo(country, city, "ip-api"));
        } catch (Exception e) {
            log.warn("Geo provider A (ip-api) failed for ip={}: {}", ipAddress, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<GeoInfo> tryProviderB(String ipAddress) {
        try {
            String url = providerBUrl + "/" + ipAddress + "/json/";
            Map<String, Object> body = fetchJson(url);

            if (body == null || Boolean.TRUE.equals(body.get("error"))) {
                return Optional.empty();
            }

            String country = String.valueOf(body.get("country_name"));
            String city = String.valueOf(body.get("city"));
            return Optional.of(new GeoInfo(country, city, "ipapi.co"));
        } catch (Exception e) {
            log.warn("Geo provider B (ipapi.co) failed for ip={}: {}", ipAddress, e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> fetchJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(2))
                .header("User-Agent", "flyrank-capstone/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return null;
        }

        return objectMapper.readValue(response.body(), Map.class);
    }
}