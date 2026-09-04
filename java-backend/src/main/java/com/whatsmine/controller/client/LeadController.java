package com.whatsmine.controller.client;

import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.Contact;
import com.whatsmine.model.Lead;
import com.whatsmine.model.LeadScrapeJob;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.LeadRepository;
import com.whatsmine.repository.LeadScrapeJobRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.leads.GooglePlacesScraper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/app/leads")
public class LeadController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private LeadScrapeJobRepository scrapeJobRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private GooglePlacesScraper placesScraper;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<Lead> leads = leadRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);
        List<LeadScrapeJob> jobs = scrapeJobRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);

        Map<String, Object> leadsPaginated = Map.of(
                "data", leads,
                "total", leads.size(),
                "current_page", 1,
                "last_page", 1
        );

        List<LeadScrapeJob> recentJobs = jobs.stream().limit(10).toList();

        Map<String, Object> props = Map.of(
                "leads", leadsPaginated,
                "scrapeJobs", recentJobs
        );

        return inertiaRenderer.render("Leads/Index", props, request);
    }

    @PostMapping("/scrape")
    public Object scrape(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body) {
        Long workspaceId = getWorkspaceId(userDetails);
        String keyword = (String) body.get("keyword");
        String location = (String) body.get("location");
        Integer radiusMeters = body.get("radius_meters") != null ? Integer.parseInt(String.valueOf(body.get("radius_meters"))) : 5000;

        if (keyword == null || keyword.isBlank() || location == null || location.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Keyword and location are required.");
        }

        LeadScrapeJob job = new LeadScrapeJob();
        job.setWorkspaceId(workspaceId);
        job.setKeyword(keyword.trim());
        job.setLocation(location.trim());
        job.setRadiusMeters(radiusMeters);
        job.setStatus("pending");
        job = scrapeJobRepository.save(job);

        // Run scraper execution synchronously / background task
        placesScraper.run(job);

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", "/app/leads")
                .body(Map.of("success", "Scrape job started. Results will appear shortly."));
    }

    @PostMapping("/push-to-contacts")
    @SuppressWarnings("unchecked")
    public Object pushToContacts(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<Object> rawIds = body.get("ids") instanceof List ? (List<Object>) body.get("ids") : List.of();

        List<Long> ids = rawIds.stream().map(id -> Long.parseLong(String.valueOf(id))).toList();
        List<Lead> leads = leadRepository.findByWorkspaceIdAndIdIn(workspaceId, ids);

        int count = 0;
        for (Lead lead : leads) {
            if (lead.isPushedToContacts()) {
                continue;
            }
            if ((lead.getPhone() == null || lead.getPhone().isBlank()) && (lead.getEmail() == null || lead.getEmail().isBlank())) {
                continue;
            }

            String phone = lead.getPhone() != null && !lead.getPhone().isBlank() ? lead.getPhone() : null;
            if (phone != null && contactRepository.findByWorkspaceIdAndPhoneE164(workspaceId, phone).isPresent()) {
                lead.setPushedToContacts(true);
                leadRepository.save(lead);
                continue;
            }

            String[] nameParts = (lead.getName() != null ? lead.getName() : "").split(" ", 2);
            Contact contact = new Contact();
            contact.setWorkspaceId(workspaceId);
            contact.setFirstName(nameParts[0]);
            contact.setLastName(nameParts.length > 1 ? nameParts[1] : "");
            contact.setEmail(lead.getEmail());
            contact.setPhoneE164(phone);
            contact.setSource("lead_scraper");
            contact.setUuid(UUID.randomUUID().toString());
            contactRepository.save(contact);

            lead.setPushedToContacts(true);
            leadRepository.save(lead);
            count++;
        }

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", "/app/leads")
                .body(Map.of("success", count + " lead(s) pushed to contacts."));
    }

    @DeleteMapping("/{id}")
    public Object destroy(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        Long workspaceId = getWorkspaceId(userDetails);
        Lead lead = leadRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found."));

        leadRepository.delete(lead);

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", "/app/leads")
                .body(Map.of("success", "Lead deleted."));
    }
}
