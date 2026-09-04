package com.whatsmine.security;

import com.whatsmine.model.WorkspaceUser;
import com.whatsmine.repository.WorkspaceUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class WorkspaceSecurityFilter extends OncePerRequestFilter {

    private final WorkspaceUserRepository workspaceUserRepository;
    private final InertiaAccessDeniedHandler accessDeniedHandler;

    public WorkspaceSecurityFilter(
            WorkspaceUserRepository workspaceUserRepository,
            InertiaAccessDeniedHandler accessDeniedHandler) {
        this.workspaceUserRepository = workspaceUserRepository;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {

                String workspaceHeader = request.getHeader("X-Workspace-Id");
                Long requestedWorkspaceId = null;

                if (workspaceHeader != null && !workspaceHeader.trim().isEmpty()) {
                    try {
                        requestedWorkspaceId = Long.parseLong(workspaceHeader.trim());
                    } catch (NumberFormatException ignored) {}
                }

                if (requestedWorkspaceId == null) {
                    requestedWorkspaceId = userDetails.getWorkspaceId();
                }

                if (requestedWorkspaceId != null) {
                    // Validate workspace membership
                    Long userId = userDetails.getId();
                    Optional<WorkspaceUser> membership = workspaceUserRepository.findByWorkspaceIdAndUserId(requestedWorkspaceId, userId);

                    if (membership.isPresent()) {
                        WorkspaceContext.setWorkspace(requestedWorkspaceId, membership.get().getRole());
                    } else if (userDetails.getWorkspaceId() != null && userDetails.getWorkspaceId().equals(requestedWorkspaceId)) {
                        WorkspaceContext.setWorkspace(requestedWorkspaceId, "owner");
                    } else {
                        // User does not belong to the requested workspace — IDOR protection!
                        accessDeniedHandler.handle(request, response, new AccessDeniedException("Access denied to requested workspace: " + requestedWorkspaceId));
                        return;
                    }
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            WorkspaceContext.clear();
        }
    }
}
