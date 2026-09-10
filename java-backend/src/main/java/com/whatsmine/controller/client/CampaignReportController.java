package com.whatsmine.controller.client;

import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.Campaign;
import com.whatsmine.model.CampaignRecipient;
import com.whatsmine.model.Contact;
import com.whatsmine.repository.CampaignRecipientRepository;
import com.whatsmine.repository.CampaignRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Per-campaign delivery report, porting PHP's CampaignReportController /
 * AnalyticsService campaign-report methods against the same CampaignRecipient
 * data the Inbox/Campaigns pagination fix and delivery-tracking work earlier
 * this session already relies on (sentAt/deliveredAt/readAt/clickedAt/
 * optedOutAt/failedReason). Renders the existing client/Reports/Campaign/Show
 * page — same component PHP already ships.
 */
@RestController
@RequestMapping("/app/reports/campaigns")
public class CampaignReportController {

    private static final int PAGE_SIZE = 50;

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignRecipientRepository campaignRecipientRepository;

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        Long workspaceId = userDetails.getWorkspaceId();

        List<Campaign> campaigns = campaignRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        List<Map<String, Object>> rows = campaigns.stream().map(c -> {
            List<CampaignRecipient> recipients = campaignRecipientRepository.findByCampaignId(c.getId());
            long total = recipients.size();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uuid", c.getUuid());
            row.put("name", c.getName());
            row.put("channel", c.getChannel());
            row.put("status", c.getStatus());
            row.put("created_at", c.getCreatedAt());
            row.put("total", total);
            row.put("delivered_pct", pct(recipients.stream().filter(r -> r.getDeliveredAt() != null).count(), total));
            row.put("read_pct", pct(recipients.stream().filter(r -> r.getReadAt() != null).count(), total));
            row.put("failed_pct", pct(recipients.stream().filter(r -> "failed".equals(r.getStatus())).count(), total));
            return row;
        }).toList();

        Map<String, Object> props = Map.of("campaigns", rows);
        return inertiaRenderer.render("client/Reports/Campaign/Index", props, request);
    }

    @GetMapping("/{uuid}")
    public Object show(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String uuid,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") int page
    ) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        Long workspaceId = userDetails.getWorkspaceId();

        Campaign campaign = campaignRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found."));

        List<CampaignRecipient> all = campaignRecipientRepository.findByCampaignId(campaign.getId());
        long total = all.size();

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("campaign", campaignProp(campaign));
        props.put("kpis", kpis(all, total));
        props.put("funnel", funnel(all));
        props.put("deliveryOverTime", deliveryOverTime(all));
        props.put("failedReasons", failedReasons(all));
        props.put("lag", lag(all));
        props.put("recipients", recipients(campaign.getId(), campaign.getUuid(), status, page, total));
        props.put("filters", Map.of("status", status != null ? status : ""));

        return inertiaRenderer.render("client/Reports/Campaign/Show", props, request);
    }

    private Map<String, Object> campaignProp(Campaign campaign) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uuid", campaign.getUuid());
        m.put("name", campaign.getName());
        m.put("channel", campaign.getChannel());
        return m;
    }

    private Map<String, Object> kpis(List<CampaignRecipient> all, long total) {
        long delivered = all.stream().filter(r -> r.getDeliveredAt() != null).count();
        long read = all.stream().filter(r -> r.getReadAt() != null).count();
        long failed = all.stream().filter(r -> "failed".equals(r.getStatus())).count();
        long clicked = all.stream().filter(r -> r.getClickedAt() != null).count();
        long optedOut = all.stream().filter(r -> r.getOptedOutAt() != null).count();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("total", total);
        kpis.put("delivered_pct", pct(delivered, total));
        kpis.put("read_pct", pct(read, total));
        kpis.put("failed_pct", pct(failed, total));
        kpis.put("clicked_pct", pct(clicked, total));
        kpis.put("opted_out", optedOut);
        return kpis;
    }

    private List<Map<String, Object>> funnel(List<CampaignRecipient> all) {
        Map<String, Long> byStatus = all.stream().collect(Collectors.groupingBy(CampaignRecipient::getStatus, Collectors.counting()));
        List<Map<String, Object>> funnel = new ArrayList<>();
        for (String s : List.of("queued", "sent", "delivered", "read")) {
            funnel.add(Map.of("name", s, "value", byStatus.getOrDefault(s, 0L)));
        }
        return funnel;
    }

    private List<Map<String, Object>> deliveryOverTime(List<CampaignRecipient> all) {
        DateTimeFormatter hourFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        Map<String, long[]> byHour = new TreeMap<>();
        for (CampaignRecipient r : all) {
            if (r.getSentAt() != null) bucket(byHour, r.getSentAt().format(hourFmt), 0);
            if (r.getDeliveredAt() != null) bucket(byHour, r.getDeliveredAt().format(hourFmt), 1);
            if (r.getReadAt() != null) bucket(byHour, r.getReadAt().format(hourFmt), 2);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, long[]> e : byHour.entrySet()) {
            long[] v = e.getValue();
            result.add(Map.of("hour", e.getKey(), "sent", v[0], "delivered", v[1], "read", v[2]));
        }
        return result;
    }

    private void bucket(Map<String, long[]> map, String key, int index) {
        map.computeIfAbsent(key, k -> new long[3])[index]++;
    }

    private List<Map<String, Object>> failedReasons(List<CampaignRecipient> all) {
        Map<String, Long> byReason = all.stream()
                .filter(r -> r.getFailedReason() != null && !r.getFailedReason().isBlank())
                .collect(Collectors.groupingBy(CampaignRecipient::getFailedReason, Collectors.counting()));
        return byReason.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    private Map<String, Object> lag(List<CampaignRecipient> all) {
        Map<String, Object> lag = new LinkedHashMap<>();
        lag.put("sent_to_delivered", avgLagSeconds(all, CampaignRecipient::getSentAt, CampaignRecipient::getDeliveredAt));
        lag.put("delivered_to_read", avgLagSeconds(all, CampaignRecipient::getDeliveredAt, CampaignRecipient::getReadAt));
        lag.put("sent_to_read", avgLagSeconds(all, CampaignRecipient::getSentAt, CampaignRecipient::getReadAt));
        return lag;
    }

    private long avgLagSeconds(List<CampaignRecipient> all, Function<CampaignRecipient, LocalDateTime> start, Function<CampaignRecipient, LocalDateTime> end) {
        List<Long> lags = new ArrayList<>();
        for (CampaignRecipient r : all) {
            LocalDateTime s = start.apply(r);
            LocalDateTime e = end.apply(r);
            if (s != null && e != null && !e.isBefore(s)) {
                lags.add(Duration.between(s, e).getSeconds());
            }
        }
        return lags.isEmpty() ? 0L : (long) lags.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private Map<String, Object> recipients(Long campaignId, String campaignUuid, String status, int page, long unfilteredTotal) {
        int safePage = Math.max(1, page);
        Pageable pageable = PageRequest.of(safePage - 1, PAGE_SIZE);

        boolean filtered = status != null && !status.isBlank();
        List<CampaignRecipient> pageItems = filtered
                ? campaignRecipientRepository.findByCampaignIdAndStatusOrderByUpdatedAtDesc(campaignId, status, pageable)
                : campaignRecipientRepository.findByCampaignIdOrderByUpdatedAtDesc(campaignId, pageable);
        long filteredTotal = filtered
                ? campaignRecipientRepository.countByCampaignIdAndStatus(campaignId, status)
                : unfilteredTotal;

        int lastPage = Math.max(1, (int) Math.ceil(filteredTotal / (double) PAGE_SIZE));
        String base = "/app/reports/campaigns/" + campaignUuid + "?";
        String statusQuery = filtered ? "&status=" + status : "";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", pageItems.stream().map(this::recipientRow).toList());
        result.put("current_page", safePage);
        result.put("last_page", lastPage);
        result.put("prev_page_url", safePage > 1 ? (base + "page=" + (safePage - 1) + statusQuery) : null);
        result.put("next_page_url", safePage < lastPage ? (base + "page=" + (safePage + 1) + statusQuery) : null);
        return result;
    }

    private Map<String, Object> recipientRow(CampaignRecipient r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("contact_id", r.getContactId());
        row.put("status", r.getStatus());
        row.put("sent_at", r.getSentAt());
        row.put("delivered_at", r.getDeliveredAt());
        row.put("read_at", r.getReadAt());
        row.put("failed_reason", r.getFailedReason());

        Contact c = r.getContact();
        if (c != null) {
            Map<String, Object> contact = new LinkedHashMap<>();
            contact.put("first_name", c.getFirstName());
            contact.put("last_name", c.getLastName());
            contact.put("phone_e164", c.getPhoneE164());
            contact.put("email", c.getEmail());
            row.put("contact", contact);
        }
        return row;
    }

    private double pct(long part, long total) {
        if (total == 0) return 0.0;
        return Math.round((part * 1000.0) / total) / 10.0;
    }
}
