package com.whatsmine.controller.client;

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
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

        return Inertia.render("Workspaces/Index", props);
    }

    @PostMapping
    @Transactional
    public Object store(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CreateWorkspaceRequest request,
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

        WorkspaceContext.setWorkspace(workspace.getId(), "owner");

        Inertia.flashSuccess(session, "Workspace created successfully.");
        return Inertia.redirect("/app/dashboard");
    }

    @PostMapping("/{workspaceId}/switch")
    @Transactional
    public Object switchWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            HttpSession session) {

        boolean belongsToWorkspace = workspaceUserRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId()).isPresent();

        if (!belongsToWorkspace) {
            throw new AccessDeniedException("You do not belong to the requested workspace.");
        }

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        user.setWorkspaceId(workspaceId);
        userRepository.save(user);

        WorkspaceContext.setWorkspace(workspaceId, "member");

        Inertia.flashSuccess(session, "Switched workspace.");
        return Inertia.redirect("/app/dashboard");
    }

    public static class CreateWorkspaceRequest {
        @NotBlank
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
