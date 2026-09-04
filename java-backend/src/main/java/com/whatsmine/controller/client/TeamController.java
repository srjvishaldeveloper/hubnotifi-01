package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.User;
import com.whatsmine.model.WorkspaceUser;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WorkspaceUserRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/app/team")
public class TeamController {

    private final WorkspaceUserRepository workspaceUserRepository;
    private final UserRepository userRepository;

    public TeamController(WorkspaceUserRepository workspaceUserRepository, UserRepository userRepository) {
        this.workspaceUserRepository = workspaceUserRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public InertiaResponse index(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = userDetails.getWorkspaceId();
        List<WorkspaceUser> memberships = workspaceUserRepository.findByWorkspaceId(workspaceId);

        List<Map<String, Object>> members = new ArrayList<>();
        for (WorkspaceUser wu : memberships) {
            Optional<User> uOpt = userRepository.findById(wu.getUserId());
            if (uOpt.isPresent()) {
                User u = uOpt.get();
                Map<String, Object> m = new HashMap<>();
                m.put("id", u.getId());
                m.put("name", u.getName());
                m.put("email", u.getEmail());
                m.put("role", wu.getRole());
                m.put("joined_at", wu.getCreatedAt());
                members.add(m);
            }
        }

        Map<String, Object> props = new HashMap<>();
        props.put("members", members);
        props.put("workspaceId", workspaceId);

        return Inertia.render("Team/Index", props);
    }

    @PostMapping("/members")
    @Transactional
    public Object addMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AddMemberRequest request,
            HttpSession session) {

        Long workspaceId = userDetails.getWorkspaceId();
        Optional<User> targetUserOpt = userRepository.findByEmail(request.getEmail());

        if (targetUserOpt.isEmpty()) {
            throw new IllegalArgumentException("No user found with email: " + request.getEmail());
        }

        User targetUser = targetUserOpt.get();
        Optional<WorkspaceUser> existing = workspaceUserRepository.findByWorkspaceIdAndUserId(workspaceId, targetUser.getId());

        if (existing.isPresent()) {
            throw new IllegalArgumentException("User is already a member of this workspace.");
        }

        WorkspaceUser wu = new WorkspaceUser();
        wu.setWorkspaceId(workspaceId);
        wu.setUserId(targetUser.getId());
        wu.setRole(request.getRole() != null ? request.getRole() : "member");
        workspaceUserRepository.save(wu);

        Inertia.flashSuccess(session, "Team member added successfully.");
        return Inertia.redirect("/app/team");
    }

    @DeleteMapping("/members/{userId}")
    @Transactional
    public Object removeMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long userId,
            HttpSession session) {

        Long workspaceId = userDetails.getWorkspaceId();
        Optional<WorkspaceUser> existing = workspaceUserRepository.findByWorkspaceIdAndUserId(workspaceId, userId);

        if (existing.isPresent()) {
            workspaceUserRepository.delete(existing.get());
        }

        Inertia.flashSuccess(session, "Team member removed.");
        return Inertia.redirect("/app/team");
    }

    public static class AddMemberRequest {
        @NotBlank
        @Email
        private String email;

        private String role = "member";

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}
