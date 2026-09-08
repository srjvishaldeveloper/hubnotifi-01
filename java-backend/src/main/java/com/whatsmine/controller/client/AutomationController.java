package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.Automation;
import com.whatsmine.model.AutomationRun;
import com.whatsmine.model.WhatsappTemplate;
import com.whatsmine.model.Campaign;
import com.whatsmine.model.User;
import com.whatsmine.repository.*;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.automation.AutomationEngine;
import com.whatsmine.service.automation.WorkflowGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/app/automations")
public class AutomationController {

    @Autowired private InertiaRenderer inertiaRenderer;
    @Autowired private AutomationRepository automationRepository;
    @Autowired private AutomationRunRepository automationRunRepository;
    @Autowired private AutomationRunLogRepository automationRunLogRepository;
    @Autowired private AutomationEngine automationEngine;
    @Autowired private WorkflowGenerator workflowGenerator;
    @Autowired private WhatsappTemplateRepository whatsappTemplateRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private UserRepository userRepository;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<Automation> automations = automationRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);
        Map<String, Object> props = Map.of("automations", automations);
        return inertiaRenderer.render("Automation/Index", props, request);
    }

    @PostMapping
    public Object store(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails,
                         @RequestBody(required = false) Map<String, Object> body, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        String name = body != null ? (String) body.get("name") : null;
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Automation name is required.");
        }

        Automation automation = new Automation();
        automation.setWorkspaceId(workspaceId);
        automation.setName(name);
        automation.setStatus("draft");
        automation.setTriggerType(body != null && body.get("trigger_type") != null ? (String) body.get("trigger_type") : "message.received");
        
        // Default trigger node
        Map<String, Object> triggerNode = Map.of(
                "id", "trigger_1",
                "type", "trigger",
                "position", Map.of("x", 250, "y", 50),
                "data", Map.of("triggerType", automation.getTriggerType(), "label", "Trigger")
        );
        automation.setNodes(List.of(triggerNode));
        automation.setEdges(List.of());

        automation = automationRepository.save(automation);

        Inertia.flashSuccess(session, "Automation created.");
        return Inertia.redirect("/app/automations/" + automation.getUuid() + "/edit");
    }

    @PostMapping("/generate")
    public Object generate(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody Map<String, Object> body) {
        String prompt = body != null ? (String) body.get("prompt") : "";
        try {
            return ResponseEntity.ok(workflowGenerator.generate(getWorkspaceId(userDetails), prompt));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{uuid}/edit")
    public Object edit(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid) {
        Long workspaceId = getWorkspaceId(userDetails);
        Automation automation = automationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Map<String, Object> resources = getBuilderResources(workspaceId, automation.getId());
        Map<String, Object> props = Map.of(
                "automation", automation,
                "resources", resources
        );
        return inertiaRenderer.render("Automation/Builder", props, request);
    }

    @PutMapping("/{uuid}")
    @SuppressWarnings("unchecked")
    public Object update(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid,
                          @RequestBody Map<String, Object> body, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        Automation automation = automationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (body.containsKey("name")) automation.setName((String) body.get("name"));
        if (body.containsKey("status")) automation.setStatus((String) body.get("status"));
        if (body.containsKey("trigger_type")) automation.setTriggerType((String) body.get("trigger_type"));
        if (body.containsKey("trigger_config") && body.get("trigger_config") instanceof Map) {
            automation.setTriggerConfig((Map<String, Object>) body.get("trigger_config"));
        }
        if (body.containsKey("nodes") && body.get("nodes") instanceof List) {
            automation.setNodes((List<Map<String, Object>>) body.get("nodes"));
        }
        if (body.containsKey("edges") && body.get("edges") instanceof List) {
            automation.setEdges((List<Map<String, Object>>) body.get("edges"));
        }

        automationRepository.save(automation);
        Inertia.flashSuccess(session, "Automation saved.");
        return Inertia.redirect("/app/automations/" + uuid + "/edit");
    }

    @DeleteMapping("/{uuid}")
    public Object destroy(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid, HttpSession session) {
        Long workspaceId = getWorkspaceId(userDetails);
        Automation automation = automationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        automationRepository.delete(automation);
        Inertia.flashSuccess(session, "Automation deleted.");
        return Inertia.redirect("/app/automations");
    }

    @GetMapping("/{uuid}/runs")
    public Object runs(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid) {
        Long workspaceId = getWorkspaceId(userDetails);
        Automation automation = automationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<AutomationRun> runs = automationRunRepository.findByAutomationIdOrderByStartedAtDesc(automation.getId());
        runs.forEach(run -> run.setLogs(automationRunLogRepository.findByRunIdOrderByIdAsc(run.getId())));
        Map<String, Object> props = Map.of(
                "automation", automation,
                "runs", Map.of("data", runs)
        );
        return inertiaRenderer.render("Automation/Runs", props, request);
    }

    @PostMapping("/{uuid}/test")
    @SuppressWarnings("unchecked")
    public Object test(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid, @RequestBody Map<String, Object> body) {
        Long workspaceId = getWorkspaceId(userDetails);
        Automation automation = automationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<Map<String, Object>> nodes = body != null && body.containsKey("nodes") ? (List<Map<String, Object>>) body.get("nodes") : automation.getNodes();
        List<Map<String, Object>> edges = body != null && body.containsKey("edges") ? (List<Map<String, Object>>) body.get("edges") : automation.getEdges();
        Map<String, Object> context = body != null && body.containsKey("context") ? (Map<String, Object>) body.get("context") : Map.of();

        Map<String, Object> result = automationEngine.testRun(automation, nodes, edges, context);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{uuid}/token")
    public Object generateToken(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String uuid) {
        Long workspaceId = getWorkspaceId(userDetails);
        Automation automation = automationRepository.findByWorkspaceIdAndUuid(workspaceId, uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String token = UUID.randomUUID().toString();
        automation.setTriggerToken(token);
        automationRepository.save(automation);

        return ResponseEntity.ok(Map.of("trigger_token", token));
    }

    private Map<String, Object> getBuilderResources(Long workspaceId, Long currentAutomationId) {
        List<WhatsappTemplate> templates = whatsappTemplateRepository.findByWorkspaceId(workspaceId);
        List<Automation> subflows = automationRepository.findByWorkspaceIdOrderByIdDesc(workspaceId)
                .stream().filter(a -> !a.getId().equals(currentAutomationId)).toList();

        Map<String, Object> resources = new HashMap<>();
        resources.put("templates", templates);
        resources.put("campaigns", List.of());
        resources.put("chatbots", List.of());
        resources.put("subflows", subflows);
        resources.put("agents", List.of());
        resources.put("stores", List.of());
        resources.put("integrations", Map.of("google", false));
        return resources;
    }
}
