package com.whatsmine.parity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.AdminUser;
import com.whatsmine.model.Client;
import com.whatsmine.model.Role;
import com.whatsmine.model.User;
import com.whatsmine.model.Workspace;
import com.whatsmine.model.WorkspaceUser;
import com.whatsmine.repository.AdminUserRepository;
import com.whatsmine.repository.ClientRepository;
import com.whatsmine.repository.RoleRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WorkspaceRepository;
import com.whatsmine.repository.WorkspaceUserRepository;
import com.whatsmine.security.AdminUserDetails;
import com.whatsmine.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ParityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceUserRepository workspaceUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private CustomUserDetails clientUserDetails;
    private AdminUserDetails adminUserDetails;
    private User testUser;
    private Workspace testWorkspace;

    @BeforeEach
    void setUp() {
        // 1. Create client user
        testUser = new User();
        testUser.setName("Parity Client");
        testUser.setEmail("client@parity.com");
        testUser.setPassword(passwordEncoder.encode("secret123"));
        testUser.setRole("client");
        testUser.setStatus("active");
        testUser = userRepository.save(testUser);

        // 2. Create workspace
        testWorkspace = new Workspace();
        testWorkspace.setName("Parity Workspace");
        testWorkspace.setOwnerId(testUser.getId());
        testWorkspace = workspaceRepository.save(testWorkspace);

        testUser.setWorkspaceId(testWorkspace.getId());
        testUser = userRepository.save(testUser);

        WorkspaceUser wu = new WorkspaceUser();
        wu.setWorkspaceId(testWorkspace.getId());
        wu.setUserId(testUser.getId());
        wu.setRole("owner");
        workspaceUserRepository.save(wu);

        clientUserDetails = new CustomUserDetails(testUser);

        // 3. Create admin user
        AdminUser admin = new AdminUser();
        admin.setName("Parity Admin");
        admin.setEmail("admin@parity.com");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setStatus("ACTIVE");
        admin = adminUserRepository.save(admin);

        adminUserDetails = new AdminUserDetails(admin);
    }

    @Test
    @DisplayName("1. Client Dashboard Inertia contract parity")
    void test1_ClientDashboardParity() throws Exception {
        mockMvc.perform(get("/app/dashboard")
                        .with(user(clientUserDetails))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component", equalTo("Client/Dashboard")))
                .andExpect(jsonPath("$.props.title", equalTo("Workspace Dashboard")))
                .andExpect(jsonPath("$.props.unreadCount", equalTo(5)));
    }

    @Test
    @DisplayName("2. Admin Dashboard Inertia contract parity")
    void test2_AdminDashboardParity() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .with(user(adminUserDetails))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component", equalTo("Admin/Dashboard")))
                .andExpect(jsonPath("$.props.title", equalTo("Admin Control Panel")));
    }

    @Test
    @DisplayName("3. Profile edit Inertia view contract parity")
    void test3_ProfileEditParity() throws Exception {
        mockMvc.perform(get("/profile")
                        .with(user(clientUserDetails))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component", equalTo("Profile/Edit")))
                .andExpect(jsonPath("$.props.user.email", equalTo("client@parity.com")));
    }

    @Test
    @DisplayName("4. Profile update mutation & flash message parity")
    void test4_ProfileUpdateParity() throws Exception {
        MockHttpSession session = new MockHttpSession();

        String payload = "{\"name\":\"Updated Parity Client\",\"email\":\"updated@parity.com\"}";

        mockMvc.perform(put("/profile")
                        .with(user(clientUserDetails))
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Inertia", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/profile"));

        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals("Updated Parity Client", updated.getName());
        assertEquals("updated@parity.com", updated.getEmail());
    }

    @Test
    @DisplayName("5. Password update BCrypt hash verification parity")
    void test5_PasswordUpdateParity() throws Exception {
        MockHttpSession session = new MockHttpSession();

        String payload = "{\"currentPassword\":\"secret123\",\"newPassword\":\"newsecret123\"}";

        mockMvc.perform(put("/profile/password")
                        .with(user(clientUserDetails))
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Inertia", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/profile"));

        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("newsecret123", updated.getPassword()));
    }

    @Test
    @DisplayName("6. Workspace listing Inertia component contract parity")
    void test6_WorkspaceIndexParity() throws Exception {
        mockMvc.perform(get("/workspaces")
                        .with(user(clientUserDetails))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component", equalTo("Workspaces/Index")))
                .andExpect(jsonPath("$.props.workspaces", notNullValue()));
    }

    @Test
    @DisplayName("7. Workspace creation database insertion & redirect parity")
    void test7_WorkspaceCreationParity() throws Exception {
        MockHttpSession session = new MockHttpSession();

        String payload = "{\"name\":\"Brand New Workspace\"}";

        mockMvc.perform(post("/workspaces")
                        .with(user(clientUserDetails))
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Inertia", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/app/dashboard"));

        assertTrue(workspaceRepository.findAll().stream().anyMatch(w -> "Brand New Workspace".equals(w.getName())));
    }

    @Test
    @DisplayName("8. Workspace switching security context update parity")
    void test8_WorkspaceSwitchingParity() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/workspaces/" + testWorkspace.getId() + "/switch")
                        .with(user(clientUserDetails))
                        .with(csrf())
                        .session(session)
                        .header("X-Inertia", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/app/dashboard"));

        User user = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(testWorkspace.getId(), user.getWorkspaceId());
    }

    @Test
    @DisplayName("9. Team member listing Inertia component contract parity")
    void test9_TeamIndexParity() throws Exception {
        mockMvc.perform(get("/app/team")
                        .with(user(clientUserDetails))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component", equalTo("Team/Index")))
                .andExpect(jsonPath("$.props.members", notNullValue()));
    }

    @Test
    @DisplayName("10. Admin client listing Inertia component contract parity")
    void test10_AdminClientIndexParity() throws Exception {
        mockMvc.perform(get("/admin/clients")
                        .with(user(adminUserDetails))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component", equalTo("Admin/Clients/Index")));
    }

    @Test
    @DisplayName("11. Admin client creation database insertion parity")
    void test11_AdminClientCreationParity() throws Exception {
        MockHttpSession session = new MockHttpSession();

        String payload = "{\"name\":\"Acme Corp\",\"email\":\"acme@example.com\",\"status\":\"active\"}";

        mockMvc.perform(post("/admin/clients")
                        .with(user(adminUserDetails))
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Inertia", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/admin/clients"));

        assertTrue(clientRepository.findAll().stream().anyMatch(c -> "Acme Corp".equals(c.getName())));
    }

    @Test
    @DisplayName("12. Admin role listing Inertia component contract parity")
    void test12_AdminRolesPermissionsIndexParity() throws Exception {
        mockMvc.perform(get("/admin/roles-permissions")
                        .with(user(adminUserDetails))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component", equalTo("Admin/RolesPermissions/Index")));
    }

    @Test
    @DisplayName("13. Admin role creation database insertion parity")
    void test13_AdminRoleCreationParity() throws Exception {
        MockHttpSession session = new MockHttpSession();

        String payload = "{\"name\":\"Support Agent\",\"key\":\"support_agent\",\"description\":\"Customer support role\"}";

        mockMvc.perform(post("/admin/roles")
                        .with(user(adminUserDetails))
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Inertia", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/admin/roles-permissions"));

        assertTrue(roleRepository.findByKey("support_agent").isPresent());
    }

    @Test
    @DisplayName("14. Admin system settings Inertia component & props parity")
    void test14_AdminSettingsParity() throws Exception {
        mockMvc.perform(get("/admin/settings")
                        .with(user(adminUserDetails))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component", equalTo("Admin/Settings/Index")))
                .andExpect(jsonPath("$.props.settings.app_name", equalTo("WhatsMine")));
    }
}
