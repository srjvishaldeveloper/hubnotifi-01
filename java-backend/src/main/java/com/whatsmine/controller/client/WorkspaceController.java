package com.whatsmine.controller.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.User;
import com.whatsmine.model.Workspace;
import com.whatsmine.model.WorkspaceUser;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WorkspaceRepository;
import com.whatsmine.repository.WorkspaceUserRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.security.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceUserRepository workspaceUserRepository;
    private final UserRepository userRepository;

    public WorkspaceController(
            WorkspaceRepository workspaceRepository,
            WorkspaceUserRepository workspaceUserRepository,
            UserRepository userRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceUserRepository = workspaceUserRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public InertiaResponse index(@AuthenticationPrincipal CustomUserDetails userDetails) {
        List<WorkspaceUser> memberships = workspaceUserRepository.findByUserId(userDetails.getId());
        List<Long> workspaceIds = memberships.stream().map(WorkspaceUser::getWorkspaceId).toList();

        List<Workspace> workspaces = workspaceRepository.findAllById(workspaceIds);

        Map<String, Object> props = new HashMap<>();
        props.put("workspaces", workspaces);
        props.put("currentWorkspaceId", userDetails.getWorkspaceId());

        return Inertia.render("client/Workspaces/Index", props);
    }

    @PostMapping
    @Transactional
    public Object store(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CreateWorkspaceRequest request,
            HttpServletRequest httpRequest,
            HttpSession session) {

        Workspace workspace = new Workspace();
        workspace.setName(request.getName());
        workspace.setOwnerId(userDetails.getId());
        workspace.setClientId(userDetails.getClientId());
        workspace = workspaceRepository.save(workspace);

        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceId(workspace.getId());
        workspaceUser.setUserId(userDetails.getId());
        workspaceUser.setRole("owner");
        workspaceUserRepository.save(workspaceUser);

        // Switch to newly created workspace
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        user.setWorkspaceId(workspace.getId());
        userRepository.save(user);
        refreshSessionPrincipal(user, httpRequest, session);

        WorkspaceContext.setWorkspace(workspace.getId(), "owner");

        Inertia.flashSuccess(session, "Workspace created successfully.");
        return Inertia.redirect("/app/dashboard");
    }

    @PostMapping("/{workspaceId}/switch")
    @Transactional
    public Object switchWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            HttpServletRequest httpRequest,
            HttpSession session) {
        return doSwitch(userDetails, workspaceId, httpRequest, session);
    }

    // Matches the Topbar/Workspaces-index contract, which POSTs a fixed URL with
    // { workspace_id } in the body rather than the id as a path segment.
    @PostMapping("/switch")
    @Transactional
    public Object switchWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody SwitchWorkspaceRequest request,
            HttpServletRequest httpRequest,
            HttpSession session) {
        return doSwitch(userDetails, request.getWorkspaceId(), httpRequest, session);
    }

    private Object doSwitch(CustomUserDetails userDetails, Long workspaceId, HttpServletRequest httpRequest, HttpSession session) {
        boolean belongsToWorkspace = workspaceUserRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId()).isPresent();

        if (!belongsToWorkspace) {
            throw new AccessDeniedException("You do not belong to the requested workspace.");
        }

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        user.setWorkspaceId(workspaceId);
        userRepository.save(user);
        refreshSessionPrincipal(user, httpRequest, session);

        WorkspaceContext.setWorkspace(workspaceId, "member");

        Inertia.flashSuccess(session, "Switched workspace.");
        return Inertia.redirect("/app/dashboard");
    }

    // The authenticated principal held in the HttpSession is a snapshot of the User
    // row taken at login time — saving a new workspace_id to the database does not,
    // by itself, update it. Without this, the freshly-switched workspace only takes
    // effect after the next login, since every subsequent request in this session
    // (including the redirect target's own global Inertia props) would keep reading
    // the stale in-memory value via @AuthenticationPrincipal.
    private void refreshSessionPrincipal(User user, HttpServletRequest httpRequest, HttpSession session) {
        CustomUserDetails refreshed = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken newAuth =
                new UsernamePasswordAuthenticationToken(refreshed, null, refreshed.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(newAuth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
        new HttpSessionSecurityContextRepository().saveContext(SecurityContextHolder.getContext(), httpRequest, null);
    }

    public static class SwitchWorkspaceRequest {
        @NotNull
        @JsonProperty("workspace_id")
        private Long workspaceId;

        public Long getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    }

    public static class CreateWorkspaceRequest {
        @NotBlank
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
