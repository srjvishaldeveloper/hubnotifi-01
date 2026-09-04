package com.whatsmine.controller;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.security.WorkspaceContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class InertiaDemoController {

    @GetMapping("/app/dashboard")
    public InertiaResponse dashboard(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> props = new HashMap<>();
        props.put("title", "Workspace Dashboard");
        props.put("unreadCount", 5);
        if (userDetails != null) {
            props.put("user_id", userDetails.getId());
            props.put("active_workspace_id", WorkspaceContext.getWorkspaceId());
            props.put("active_workspace_role", WorkspaceContext.getWorkspaceRole());
        }

        return Inertia.render("Client/Dashboard", props);
    }

    @PostMapping("/app/settings")
    public Object updateSettings(HttpSession session) {
        Inertia.flashSuccess(session, "Settings updated successfully!");
        return Inertia.redirect("/app/dashboard");
    }

    @GetMapping("/admin/dashboard")
    public InertiaResponse adminDashboard() {
        Map<String, Object> props = new HashMap<>();
        props.put("title", "Admin Control Panel");
        props.put("stats", Map.of("users", 120, "revenue", 5400));

        return Inertia.render("Admin/Dashboard", props);
    }

    @GetMapping("/api/v1/user")
    public Map<String, Object> apiUser(@AuthenticationPrincipal Object principal) {
        Map<String, Object> res = new HashMap<>();
        res.put("authenticated", true);
        if (principal instanceof CustomUserDetails userDetails) {
            res.put("id", userDetails.getId());
            res.put("email", userDetails.getUsername());
            res.put("type", "client");
        } else {
            res.put("type", "admin_or_token");
        }
        return res;
    }
}
