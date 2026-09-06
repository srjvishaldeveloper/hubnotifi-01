package com.whatsmine.controller;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.security.WorkspaceContext;
import com.whatsmine.service.admin.AdminAnalyticsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class InertiaDemoController {

    @Autowired
    private AdminAnalyticsService analytics;

    @GetMapping({"/dashboard", "/app/dashboard"})
    public InertiaResponse dashboard(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> props = new HashMap<>();
        props.put("title", "Workspace Dashboard");
        props.put("unreadCount", 5);
        if (userDetails != null) {
            props.put("user_id", userDetails.getId());
            props.put("active_workspace_id", WorkspaceContext.getWorkspaceId());
            props.put("active_workspace_role", WorkspaceContext.getWorkspaceRole());
        }

        return Inertia.render("client/Dashboard", props);
    }

    @PostMapping("/app/settings")
    public Object updateSettings(HttpSession session) {
        Inertia.flashSuccess(session, "Settings updated successfully!");
        return Inertia.redirect("/app/dashboard");
    }

    @GetMapping("/admin/dashboard")
    public InertiaResponse adminDashboard(@RequestParam(required = false) Integer range) {
        Map<String, Object> props = analytics.dashboardProps(range != null ? range : 30);
        props.put("title", "Admin Control Panel");
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
