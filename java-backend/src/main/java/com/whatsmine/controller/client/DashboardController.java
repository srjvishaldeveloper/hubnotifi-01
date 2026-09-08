package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.Plan;
import com.whatsmine.model.User;
import com.whatsmine.model.Workspace;
import com.whatsmine.model.WorkspaceUser;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.WorkspaceRepository;
import com.whatsmine.repository.WorkspaceUserRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.client.ClientDashboardAnalyticsService;
import com.whatsmine.service.client.EffectiveSubscriptionResolver;
import com.whatsmine.service.client.OnboardingProgressService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real client Dashboard — Java port of PHP's Client\DashboardController.
 * Replaces the stub previously served by InertiaDemoController.dashboard(),
 * which never sent hasWorkspace/workspacesCount/usage/stats/charts/tables,
 * so the page's own default prop values ("No active workspace", 0 everywhere)
 * always rendered regardless of the user's actual workspace.
 */
@RestController
public class DashboardController {

    private static final List<Integer> RANGES = List.of(7, 30, 90);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceUserRepository workspaceUserRepository;
    private final PlanRepository planRepository;
    private final EffectiveSubscriptionResolver subscriptionResolver;
    private final OnboardingProgressService onboardingProgressService;
    private final ClientDashboardAnalyticsService analyticsService;
    private final com.whatsmine.repository.UserRepository userRepository;

    public DashboardController(WorkspaceRepository workspaceRepository,
                                WorkspaceUserRepository workspaceUserRepository,
                                PlanRepository planRepository,
                                EffectiveSubscriptionResolver subscriptionResolver,
                                OnboardingProgressService onboardingProgressService,
                                ClientDashboardAnalyticsService analyticsService,
                                com.whatsmine.repository.UserRepository userRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceUserRepository = workspaceUserRepository;
        this.planRepository = planRepository;
        this.subscriptionResolver = subscriptionResolver;
        this.onboardingProgressService = onboardingProgressService;
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
    }

    @GetMapping({"/dashboard", "/app/dashboard"})
    public InertiaResponse dashboard(@AuthenticationPrincipal CustomUserDetails userDetails,
                                      @RequestParam(required = false) Integer range) {
        User user = userDetails.getUser();

        int resolvedRange = (range != null && RANGES.contains(range)) ? range : 30;

        EffectiveSubscriptionResolver.Result effective = subscriptionResolver.resolve(user);
        Plan plan = effective != null ? planRepository.findById(effective.planId()).orElse(null) : null;

        Map<String, Object> currentPlan = null;
        String renewsAt = null;
        boolean managedByAdmin = false;
        if (effective != null && plan != null) {
            Map<String, Object> planMap = new LinkedHashMap<>();
            planMap.put("id", plan.getId());
            planMap.put("name", plan.getName());
            planMap.put("slug", plan.getSlug());
            planMap.put("status", "active".equals(effective.status()) || "trialing".equals(effective.status()) ? "active" : effective.status());
            currentPlan = planMap;
            renewsAt = effective.renewsOrEndsAt() != null ? effective.renewsOrEndsAt().toString() : null;
            managedByAdmin = effective.managedByAdmin();
        }

        long teamMembersCount = user.getClientId() != null ? userRepository.findByClientId(user.getClientId()).size() : 1;
        Long teamMembersLimit = (plan != null && plan.getLimits() != null && plan.getLimits().get("users") != null)
                ? ((Number) plan.getLimits().get("users")).longValue() : null;

        Set<Long> workspaceIds = new java.util.LinkedHashSet<>();
        workspaceRepository.findByOwnerId(user.getId()).forEach(w -> workspaceIds.add(w.getId()));
        workspaceUserRepository.findByUserId(user.getId()).forEach(wu -> workspaceIds.add(wu.getWorkspaceId()));
        long workspacesCount = workspaceIds.size();

        Long wsId = userDetails.getWorkspaceId();
        boolean hasWorkspace = wsId != null;

        Map<String, Object> stats = null;
        Map<String, Object> charts = Map.of();
        Map<String, Object> tables = Map.of();

        if (hasWorkspace) {
            LocalDateTime to = LocalDateTime.now();
            LocalDateTime from = to.minusDays(resolvedRange - 1).toLocalDate().atStartOfDay();
            LocalDateTime prevTo = from.minusSeconds(1);
            LocalDateTime prevFrom = prevTo.minusDays(resolvedRange - 1).toLocalDate().atStartOfDay();

            stats = analyticsService.buildStats(wsId, from, to, prevFrom, prevTo);
            charts = analyticsService.buildCharts(wsId, from, to);
            tables = analyticsService.buildTables(wsId, from, to);
        }

        Map<String, Object> onboardingProgress = onboardingProgressService.getProgress(user);

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("team_members_count", teamMembersCount);
        usage.put("team_members_limit", teamMembersLimit);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("range", resolvedRange);
        props.put("hasWorkspace", hasWorkspace);
        props.put("currentPlan", currentPlan);
        props.put("renewsAt", renewsAt);
        props.put("managedByAdmin", managedByAdmin);
        props.put("usage", usage);
        props.put("isClientAdministrator", "administrator".equalsIgnoreCase(user.getClientRole()));
        props.put("workspacesCount", workspacesCount);
        props.put("onboardingNextStep", onboardingProgress.get("next_step"));
        props.put("onboardingPercent", onboardingProgress.get("percent"));
        props.put("stats", stats);
        props.put("charts", charts);
        props.put("tables", tables);
        props.put("first_run", user.getCreatedAt() != null && user.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(5)));

        return Inertia.render("client/Dashboard", props);
    }
}
