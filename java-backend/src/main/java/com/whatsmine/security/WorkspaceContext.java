package com.whatsmine.security;

public class WorkspaceContext {

    private static final ThreadLocal<Long> currentWorkspaceId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentWorkspaceRole = new ThreadLocal<>();

    public static void setWorkspace(Long workspaceId, String role) {
        currentWorkspaceId.set(workspaceId);
        currentWorkspaceRole.set(role);
    }

    public static Long getWorkspaceId() {
        return currentWorkspaceId.get();
    }

    public static String getWorkspaceRole() {
        return currentWorkspaceRole.get();
    }

    public static void clear() {
        currentWorkspaceId.remove();
        currentWorkspaceRole.remove();
    }
}
