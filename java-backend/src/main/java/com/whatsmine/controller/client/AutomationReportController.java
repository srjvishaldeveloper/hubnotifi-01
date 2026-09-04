package com.whatsmine.controller.client;

import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.Automation;
import com.whatsmine.model.AutomationRun;
import com.whatsmine.repository.AutomationRepository;
import com.whatsmine.repository.AutomationRunRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/app/reports/automations")
public class AutomationReportController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private AutomationRepository automationRepository;

    @Autowired
    private AutomationRunRepository automationRunRepository;

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        Long workspaceId = userDetails.getWorkspaceId();

        List<Automation> automations = automationRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);
        List<Long> automationIds = automations.stream().map(Automation::getId).toList();

        Map<String, Long> runsByStatus = new HashMap<>();
        Map<String, Long> runsPerAutomation = new HashMap<>();

        if (!automationIds.isEmpty()) {
            List<AutomationRun> allRuns = automationRunRepository.findByAutomationIdIn(automationIds);
            runsByStatus = allRuns.stream().collect(Collectors.groupingBy(AutomationRun::getStatus, Collectors.counting()));

            Map<Long, String> nameMap = automations.stream().collect(Collectors.toMap(Automation::getId, Automation::getName));
            runsPerAutomation = allRuns.stream()
                    .collect(Collectors.groupingBy(r -> nameMap.getOrDefault(r.getAutomationId(), "Unknown"), Collectors.counting()));
        }

        Map<String, Object> props = Map.of(
                "runsByStatus", runsByStatus,
                "runsPerAutomation", runsPerAutomation,
                "totalAutomations", automations.size()
        );

        return inertiaRenderer.render("client/Reports/Automation/Index", props, request);
    }
}
