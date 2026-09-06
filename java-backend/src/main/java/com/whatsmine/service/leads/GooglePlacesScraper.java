package com.whatsmine.service.leads;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.Lead;
import com.whatsmine.model.LeadScrapeJob;
import com.whatsmine.repository.LeadRepository;
import com.whatsmine.repository.LeadScrapeJobRepository;
import com.whatsmine.service.IntegrationCredentialsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ports php/app/Modules/Leads/Services/GooglePlacesScraper.php: a Text Search
 * call per page (paginating via next_page_token, capped at Google's own
 * 3-page/~60-result limit) plus one Place Details call per result to pick up
 * phone/website, which Text Search doesn't return. Real outbound calls only
 * — no transaction wraps the whole run so the DB connection isn't held open
 * across the network round-trips and the required 2s pagination delay.
 */
@Service
public class GooglePlacesScraper {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesScraper.class);

    private static final String TEXT_SEARCH_URL = "https://maps.googleapis.com/maps/api/place/textsearch/json";
    private static final String DETAILS_URL = "https://maps.googleapis.com/maps/api/place/details/json";
    private static final int MAX_PAGES = 3;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final LeadScrapeJobRepository jobRepository;
    private final LeadRepository leadRepository;
    private final IntegrationCredentialsService credentialsService;
    private final ObjectMapper objectMapper;

    public GooglePlacesScraper(LeadScrapeJobRepository jobRepository,
                                LeadRepository leadRepository,
                                IntegrationCredentialsService credentialsService,
                                ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.leadRepository = leadRepository;
        this.credentialsService = credentialsService;
        this.objectMapper = objectMapper;
    }

    public void run(LeadScrapeJob job) {
        job.setStatus("running");
        job.setStartedAt(LocalDateTime.now());
        jobRepository.save(job);

        String apiKey = credentialsService.getCredential("google_places", "api_key");
        if (apiKey == null || apiKey.isBlank()) {
            job.setStatus("failed");
            job.setError("Google Places API is not configured — add an API key under Admin > Integrations > Google Places API.");
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
            return;
        }

        try {
            int count = 0;
            String pageToken = null;
            String query = job.getKeyword() + " in " + job.getLocation();

            for (int page = 0; page < MAX_PAGES; page++) {
                JsonNode response = textSearch(query, apiKey, pageToken);

                String status = response.path("status").asText("");
                if (!"OK".equals(status) && !"ZERO_RESULTS".equals(status)) {
                    throw new IllegalStateException("Google Places error: " + status + " " + response.path("error_message").asText(""));
                }

                for (JsonNode place : response.path("results")) {
                    if (upsertLead(job, place, apiKey)) {
                        count++;
                    }
                }

                pageToken = response.path("next_page_token").asText(null);
                if (pageToken == null || pageToken.isBlank()) {
                    break;
                }
                // A next_page_token isn't valid until a couple of seconds after it's issued.
                Thread.sleep(2000);
            }

            job.setStatus("done");
            job.setLeadsFound(count);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        } catch (Exception e) {
            log.warn("Lead scrape job {} failed: {}", job.getId(), e.getMessage());
            job.setStatus("failed");
            job.setError(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }

    private JsonNode textSearch(String query, String apiKey, String pageToken) throws Exception {
        StringBuilder url = new StringBuilder(TEXT_SEARCH_URL)
                .append("?query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8))
                .append("&key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        if (pageToken != null && !pageToken.isBlank()) {
            url.append("&pagetoken=").append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
        }
        return get(url.toString());
    }

    private JsonNode placeDetails(String placeId, String apiKey) throws Exception {
        String url = DETAILS_URL
                + "?place_id=" + URLEncoder.encode(placeId, StandardCharsets.UTF_8)
                + "&fields=formatted_phone_number,website,url"
                + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return get(url);
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private boolean upsertLead(LeadScrapeJob job, JsonNode place, String apiKey) throws Exception {
        String placeId = place.path("place_id").asText(null);
        if (placeId == null || placeId.isBlank()) {
            return false;
        }

        JsonNode details = placeDetails(placeId, apiKey).path("result");

        Lead lead = leadRepository.findByGooglePlaceId(placeId).orElseGet(Lead::new);
        lead.setWorkspaceId(job.getWorkspaceId());
        lead.setName(place.path("name").asText(null));
        lead.setPhone(details.path("formatted_phone_number").asText(null));
        lead.setWebsite(details.path("website").asText(null));
        lead.setAddress(place.path("formatted_address").asText(null));

        List<String> types = new ArrayList<>();
        for (JsonNode t : place.path("types")) {
            if (types.size() >= 3) break;
            types.add(t.asText());
        }
        lead.setCategory(String.join(", ", types));

        if (place.hasNonNull("rating")) {
            lead.setRating(BigDecimal.valueOf(place.path("rating").asDouble()));
        }
        lead.setReviewCount(place.path("user_ratings_total").asInt(0));
        lead.setGooglePlaceId(placeId);

        JsonNode location = place.path("geometry").path("location");
        if (location.hasNonNull("lat")) {
            lead.setLat(BigDecimal.valueOf(location.path("lat").asDouble()));
        }
        if (location.hasNonNull("lng")) {
            lead.setLng(BigDecimal.valueOf(location.path("lng").asDouble()));
        }

        leadRepository.save(lead);
        return true;
    }
}
