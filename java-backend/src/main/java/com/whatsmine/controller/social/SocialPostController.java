package com.whatsmine.controller.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.SocialAccount;
import com.whatsmine.model.SocialPost;
import com.whatsmine.model.SocialPostAccount;
import com.whatsmine.queue.QueueDispatcher;
import com.whatsmine.repository.SocialAccountRepository;
import com.whatsmine.repository.SocialPostAccountRepository;
import com.whatsmine.repository.SocialPostRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.ai.LlmGateway;
import com.whatsmine.service.ai.llm.LlmResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Post composer/CRUD/scheduling/AI generation, porting PHP's
 * Social\Http\Controllers\SocialPostController. Renders the existing
 * Social/Composer.jsx, Social/Posts/Index.jsx, Social/Posts/Edit.jsx and
 * Social/Calendar.jsx pages unmodified.
 */
@RestController
@RequestMapping("/app/social")
public class SocialPostController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private SocialPostRepository postRepository;

    @Autowired
    private SocialAccountRepository accountRepository;

    @Autowired
    private SocialPostAccountRepository postAccountRepository;

    @Autowired
    private QueueDispatcher queueDispatcher;

    @Autowired
    private LlmGateway llmGateway;

    @Autowired
    private ObjectMapper objectMapper;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping("/posts")
    public Object index(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String network,
            @RequestParam(required = false, defaultValue = "1") int page
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<Map<String, Object>> accounts = activeAccountRows(workspaceId);

        PageRequest pageable = PageRequest.of(Math.max(0, page - 1), 20);
        Page<SocialPost> posts = (status != null && !status.isBlank())
                ? postRepository.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, status, pageable)
                : postRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable);

        List<Map<String, Object>> rows = posts.getContent().stream()
                .filter(p -> network == null || network.isBlank() || postTargetsNetwork(p, network))
                .map(this::postRow)
                .toList();

        Map<String, Object> paginated = new LinkedHashMap<>();
        paginated.put("data", rows);
        paginated.put("current_page", page);
        paginated.put("last_page", Math.max(1, posts.getTotalPages()));
        paginated.put("total", posts.getTotalElements());

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("posts", paginated);
        props.put("accounts", accounts);
        props.put("filters", Map.of("status", status != null ? status : "", "network", network != null ? network : ""));
        return inertiaRenderer.render("Social/Posts/Index", props, request);
    }

    @GetMapping("/composer")
    public Object composer(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        return inertiaRenderer.render("Social/Composer", Map.of("accounts", activeAccountRows(workspaceId)), request);
    }

    @GetMapping("/calendar")
    public Object calendar(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String month
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        YearMonth ym;
        try {
            ym = (month != null && !month.isBlank()) ? YearMonth.parse(month) : YearMonth.now();
        } catch (Exception e) {
            ym = YearMonth.now();
        }

        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);

        List<SocialPost> posts = postRepository.findByWorkspaceIdAndScheduledAtBetween(workspaceId, start, end);
        List<Map<String, Object>> rows = posts.stream().map(this::postRow).toList();

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("posts", rows);
        props.put("month", ym.toString());
        props.put("accounts", activeAccountRows(workspaceId));
        props.put("filters", Map.of());
        return inertiaRenderer.render("Social/Calendar", props, request);
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/posts")
    public Object store(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);

        String bodyText = str(body.get("body"));
        if (bodyText == null || bodyText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Post content is required.");
        }
        List<Long> targetAccountIds = toLongList(body.get("target_accounts"));
        if (targetAccountIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Select at least one account to post to.");
        }
        assertAccountsOwned(workspaceId, targetAccountIds);

        LocalDateTime scheduledAt = parseIso(str(body.get("scheduled_at")));
        if (scheduledAt != null && scheduledAt.isBefore(LocalDateTime.now().minusSeconds(30))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The scheduled time must be in the future.");
        }

        SocialPost post = new SocialPost();
        post.setWorkspaceId(workspaceId);
        post.setTitle(str(body.get("title")));
        post.setContent(bodyText);
        post.setMediaUrls(cleanMediaUrls(body.get("media_urls")));
        post.setTimezone(str(body.getOrDefault("timezone", "UTC")));
        post.setScheduledAt(scheduledAt);
        post.setStatus(scheduledAt != null ? "scheduled" : "draft");
        post = postRepository.save(post);

        linkTargetAccounts(post.getId(), targetAccountIds);

        if (scheduledAt == null) {
            post.setStatus("publishing");
            postRepository.save(post);
            queueDispatcher.dispatch("social", "PublishSocialPostJob", Map.of("postId", post.getId()));
        }

        Inertia.flashSuccess(session, "Post " + (scheduledAt != null ? "scheduled" : "queued for publishing") + ".");
        return Inertia.redirect("/app/social/posts");
    }

    @GetMapping("/posts/{id}/edit")
    public Object edit(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        Long workspaceId = getWorkspaceId(userDetails);
        SocialPost post = postRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (List.of("publishing", "published").contains(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot edit a post that is already published.");
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("post", postRow(post));
        props.put("accounts", activeAccountRows(workspaceId));
        return inertiaRenderer.render("Social/Posts/Edit", props, request);
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/posts/{id}")
    public Object update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        SocialPost post = postRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (List.of("publishing", "published").contains(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot edit a post that is already published or being published.");
        }

        String bodyText = str(body.get("body"));
        if (bodyText == null || bodyText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Post content is required.");
        }
        List<Long> targetAccountIds = toLongList(body.get("target_accounts"));
        if (targetAccountIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Select at least one account to post to.");
        }
        assertAccountsOwned(workspaceId, targetAccountIds);

        LocalDateTime scheduledAt = parseIso(str(body.get("scheduled_at")));
        if (scheduledAt != null && scheduledAt.isBefore(LocalDateTime.now().minusSeconds(30))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The scheduled time must be in the future.");
        }

        post.setTitle(str(body.get("title")));
        post.setContent(bodyText);
        post.setMediaUrls(cleanMediaUrls(body.get("media_urls")));
        post.setTimezone(str(body.getOrDefault("timezone", "UTC")));
        post.setScheduledAt(scheduledAt);
        post.setStatus(scheduledAt != null ? "scheduled" : "draft");
        postRepository.save(post);

        postAccountRepository.findBySocialPostId(post.getId()).forEach(postAccountRepository::delete);
        linkTargetAccounts(post.getId(), targetAccountIds);

        Inertia.flashSuccess(session, "Post updated successfully.");
        return Inertia.redirect("/app/social/posts");
    }

    @PostMapping("/posts/{id}/publish-now")
    public Object publishNow(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        SocialPost post = postRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (List.of("publishing", "published").contains(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Post is already being published or published.");
        }

        post.setScheduledAt(null);
        post.setStatus("publishing");
        postRepository.save(post);
        queueDispatcher.dispatch("social", "PublishSocialPostJob", Map.of("postId", post.getId()));

        Inertia.flashSuccess(session, "Post queued for immediate publishing.");
        return Inertia.redirect("/app/social/posts");
    }

    @PostMapping("/posts/{id}/cancel")
    public Object cancel(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        SocialPost post = postRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!"scheduled".equals(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Only scheduled posts can be cancelled.");
        }

        post.setStatus("draft");
        post.setScheduledAt(null);
        postRepository.save(post);

        Inertia.flashSuccess(session, "Scheduled post cancelled and moved to drafts.");
        return Inertia.redirect("/app/social/posts");
    }

    @DeleteMapping("/posts/{id}")
    public Object destroy(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        SocialPost post = postRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if ("publishing".equals(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Cannot delete a post that is currently being published.");
        }

        postAccountRepository.findBySocialPostId(post.getId()).forEach(postAccountRepository::delete);
        postRepository.delete(post);

        Inertia.flashSuccess(session, "Post deleted.");
        return Inertia.redirect("/app/social/posts");
    }

    @PostMapping("/ai-generate")
    public ResponseEntity<Map<String, Object>> aiGenerate(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body) {
        Long workspaceId = getWorkspaceId(userDetails);
        String prompt = str(body.get("prompt"));
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "A prompt is required."));
        }
        String network = str(body.getOrDefault("network", "any social network"));

        try {
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "You are a social media copywriter. Write engaging, concise posts optimized for " + network + ". Return ONLY the post text, no explanations."),
                    Map.of("role", "user", "content", prompt)
            );
            LlmResponse response = llmGateway.chat(workspaceId, messages, Map.of());
            return ResponseEntity.ok(Map.of("body", response.content()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/ai-plan")
    public ResponseEntity<Map<String, Object>> aiPlan(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body) {
        Long workspaceId = getWorkspaceId(userDetails);

        String topic = str(body.get("topic"));
        if (topic == null || topic.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "A topic is required."));
        }
        List<Long> targetAccountIds = toLongList(body.get("target_accounts"));
        if (targetAccountIds.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "Select at least one account."));
        }

        List<SocialAccount> accounts = targetAccountIds.stream()
                .map(id -> accountRepository.findByIdAndWorkspaceId(id, workspaceId).orElse(null))
                .filter(a -> a != null && Boolean.TRUE.equals(a.getActive()))
                .toList();
        if (accounts.size() != targetAccountIds.size()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "One or more selected accounts are invalid."));
        }

        List<String> networks = accounts.stream().map(SocialAccount::getProvider).distinct().toList();
        int postCount = body.get("post_count") instanceof Number n ? n.intValue() : 7;
        String tone = str(body.getOrDefault("tone", "professional"));
        String goal = str(body.getOrDefault("campaign_goal", "increase engagement and brand awareness"));
        String startDate = str(body.get("start_date"));
        String endDate = str(body.get("end_date"));
        String timezone = str(body.getOrDefault("timezone", "UTC"));

        try {
            List<Map<String, String>> messages = buildPlanMessages(topic, networks, postCount, tone, goal, startDate, endDate, timezone);
            LlmResponse response = llmGateway.chat(workspaceId, messages, Map.of("temperature", 0.7, "max_tokens", 4096));
            List<Map<String, Object>> posts = parsePlanResponse(response.content());
            return ResponseEntity.ok(Map.of("posts", posts, "accounts", accounts.stream().map(this::accountSummary).toList()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/posts/bulk")
    public ResponseEntity<Map<String, Object>> bulkStore(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body) {
        Long workspaceId = getWorkspaceId(userDetails);
        Object postsObj = body.get("posts");
        if (!(postsObj instanceof List) || ((List<?>) postsObj).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "At least one post is required."));
        }
        List<Map<String, Object>> postsData = (List<Map<String, Object>>) postsObj;

        List<Long> allIds = postsData.stream()
                .flatMap(p -> toLongList(p.get("target_accounts")).stream())
                .distinct()
                .toList();
        assertAccountsOwned(workspaceId, allIds);

        List<Long> createdIds = new ArrayList<>();
        for (Map<String, Object> postData : postsData) {
            LocalDateTime scheduledAt = parseIso(str(postData.get("scheduled_at")));
            SocialPost post = new SocialPost();
            post.setWorkspaceId(workspaceId);
            post.setTitle(str(postData.get("title")));
            post.setContent(str(postData.get("body")));
            post.setMediaUrls(List.of());
            post.setTimezone(str(postData.getOrDefault("timezone", "UTC")));
            post.setScheduledAt(scheduledAt);
            post.setStatus(scheduledAt != null ? "scheduled" : "draft");
            post = postRepository.save(post);
            linkTargetAccounts(post.getId(), toLongList(postData.get("target_accounts")));
            createdIds.add(post.getId());
        }

        return ResponseEntity.ok(Map.of("success", true, "created", createdIds.size(), "post_ids", createdIds));
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private List<Map<String, String>> buildPlanMessages(String topic, List<String> networks, int count, String tone, String goal, String startDate, String endDate, String timezone) {
        Map<String, Integer> limits = Map.of("twitter", 280, "tiktok", 2200, "linkedin", 3000, "facebook", 63206, "instagram", 2200, "youtube", 5000);
        String networksStr = String.join(", ", networks);
        StringBuilder limitLines = new StringBuilder();
        for (String n : networks) {
            limitLines.append("- ").append(n).append(": ").append(limits.getOrDefault(n, 5000)).append(" characters\n");
        }

        String system = "You are an expert social media strategist. Generate a content calendar as JSON.\n\n"
                + "RULES:\n"
                + "1. Output ONLY valid JSON — no markdown, no prose, no code fences.\n"
                + "2. Top-level object must be: {\"posts\": [...]}\n"
                + "3. Generate exactly " + count + " posts spread evenly between " + startDate + " and " + endDate + ".\n"
                + "4. Each post must have EXACTLY these fields:\n"
                + "   - \"title\": short title (string, max 100 chars)\n"
                + "   - \"body\": post content (string)\n"
                + "   - \"suggested_time\": UTC ISO 8601 datetime (e.g. \"2026-06-01T10:00:00Z\")\n"
                + "   - \"rationale\": one sentence explaining timing/approach (string)\n"
                + "5. Character limits per network:\n" + limitLines
                + "6. Primary \"body\" must fit the SHORTEST character limit among: " + networksStr + "\n"
                + "7. Tone: " + tone + ". Campaign goal: " + goal + ".\n"
                + "8. If you cannot produce valid JSON, return exactly: {\"error\": \"generation_failed\"}";

        return List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", "Create a " + count + "-post campaign calendar for: " + topic + "\nPlatforms: " + networksStr + "\nSchedule: " + startDate + " to " + endDate + " (" + timezone + ").")
        );
    }

    private List<Map<String, Object>> parsePlanResponse(String content) throws Exception {
        String cleaned = content.trim().replaceAll("(?i)^```(json)?\\s*", "").replaceAll("\\s*```$", "");
        JsonNode decoded = objectMapper.readTree(cleaned);
        if (decoded.path("error").asText(null) != null) {
            throw new RuntimeException("AI failed to generate the plan. Please refine your brief.");
        }
        JsonNode postsNode = decoded.path("posts");
        if (!postsNode.isArray()) {
            throw new RuntimeException("AI returned malformed JSON. Please try again.");
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode p : postsNode) {
            if (p.path("body").asText("").isBlank()) {
                throw new RuntimeException("AI response is missing body content for a post.");
            }
            Map<String, Object> post = new LinkedHashMap<>();
            post.put("title", p.path("title").asText(""));
            post.put("body", p.path("body").asText());
            post.put("suggested_time", p.path("suggested_time").isMissingNode() ? null : p.path("suggested_time").asText());
            post.put("rationale", p.path("rationale").asText(""));
            result.add(post);
        }
        return result;
    }

    private void assertAccountsOwned(Long workspaceId, List<Long> ids) {
        long ownedCount = ids.stream().filter(id -> accountRepository.findByIdAndWorkspaceId(id, workspaceId).isPresent()).count();
        if (ownedCount != ids.size()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "One or more selected accounts do not belong to your workspace.");
        }
    }

    private void linkTargetAccounts(Long postId, List<Long> accountIds) {
        for (Long accountId : accountIds) {
            SocialPostAccount link = new SocialPostAccount();
            link.setSocialPostId(postId);
            link.setSocialAccountId(accountId);
            link.setStatus("pending");
            postAccountRepository.save(link);
        }
    }

    private boolean postTargetsNetwork(SocialPost post, String network) {
        return postAccountRepository.findBySocialPostId(post.getId()).stream()
                .anyMatch(link -> accountRepository.findById(link.getSocialAccountId())
                        .map(a -> network.equals(a.getProvider())).orElse(false));
    }

    private List<Map<String, Object>> activeAccountRows(Long workspaceId) {
        return accountRepository.findByWorkspaceIdAndActiveTrue(workspaceId).stream().map(this::accountSummary).toList();
    }

    private Map<String, Object> accountSummary(SocialAccount a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.getId());
        row.put("network", a.getProvider());
        row.put("name", a.getName());
        row.put("picture_url", a.getPictureUrl());
        return row;
    }

    private Map<String, Object> postRow(SocialPost post) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", post.getId());
        row.put("title", post.getTitle());
        row.put("body", post.getContent());
        row.put("media_urls", post.getMediaUrls());
        row.put("status", post.getStatus());
        row.put("scheduled_at", post.getScheduledAt());
        row.put("published_at", post.getPublishedAt());
        row.put("timezone", post.getTimezone());
        row.put("created_at", post.getCreatedAt());
        List<Long> targetIds = postAccountRepository.findBySocialPostId(post.getId()).stream()
                .map(SocialPostAccount::getSocialAccountId).toList();
        row.put("target_accounts", targetIds);
        return row;
    }

    private List<String> cleanMediaUrls(Object raw) {
        return toStringList(raw).stream().filter(u -> u != null && !u.isBlank()).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object raw) {
        if (!(raw instanceof List)) return List.of();
        List<Object> list = (List<Object>) raw;
        List<String> result = new ArrayList<>();
        for (Object o : list) {
            if (o != null) result.add(String.valueOf(o));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Long> toLongList(Object raw) {
        if (!(raw instanceof List)) return List.of();
        List<Object> list = (List<Object>) raw;
        List<Long> result = new ArrayList<>();
        for (Object o : list) {
            try {
                result.add(Long.valueOf(String.valueOf(o)));
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private LocalDateTime parseIso(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            try {
                return java.time.OffsetDateTime.parse(value).toLocalDateTime();
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }
}
