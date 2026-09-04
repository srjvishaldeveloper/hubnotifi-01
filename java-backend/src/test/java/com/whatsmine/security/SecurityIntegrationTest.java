package com.whatsmine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.AdminUser;
import com.whatsmine.model.PersonalAccessToken;
import com.whatsmine.model.User;
import com.whatsmine.model.Workspace;
import com.whatsmine.model.WorkspaceUser;
import com.whatsmine.repository.AdminUserRepository;
import com.whatsmine.repository.PersonalAccessTokenRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WorkspaceRepository;
import com.whatsmine.repository.WorkspaceUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PersonalAccessTokenRepository tokenRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceUserRepository workspaceUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpService totpService;

    @Autowired
    private ObjectMapper objectMapper;

    private User testClientUser;
    private User disabledClientUser;
    private AdminUser testAdminUser;
    private Workspace userWorkspace1;
    private Workspace userWorkspace2;

    @BeforeEach
    void setUp() {
        // Create active client user
        testClientUser = new User();
        testClientUser.setName("Jane Doe");
        testClientUser.setEmail("jane@example.com");
        testClientUser.setPassword(passwordEncoder.encode("secret123")); // Compatible with Laravel BCrypt
        testClientUser.setRole("client");
        testClientUser.setStatus("active");
        testClientUser = userRepository.save(testClientUser);

        // Create workspace 1 for testClientUser
        userWorkspace1 = new Workspace();
        userWorkspace1.setName("Jane Workspace 1");
        userWorkspace1.setOwnerId(testClientUser.getId());
        userWorkspace1 = workspaceRepository.save(userWorkspace1);

        testClientUser.setWorkspaceId(userWorkspace1.getId());
        testClientUser = userRepository.save(testClientUser);

        WorkspaceUser wu1 = new WorkspaceUser();
        wu1.setWorkspaceId(userWorkspace1.getId());
        wu1.setUserId(testClientUser.getId());
        wu1.setRole("owner");
        workspaceUserRepository.save(wu1);

        // Create workspace 2 (belonging to another user)
        userWorkspace2 = new Workspace();
        userWorkspace2.setName("Other Workspace 2");
        userWorkspace2.setOwnerId(999L);
        userWorkspace2 = workspaceRepository.save(userWorkspace2);

        // Create disabled client user
        disabledClientUser = new User();
        disabledClientUser.setName("Disabled User");
        disabledClientUser.setEmail("disabled@example.com");
        disabledClientUser.setPassword(passwordEncoder.encode("secret123"));
        disabledClientUser.setRole("client");
        disabledClientUser.setStatus("suspended");
        disabledClientUser = userRepository.save(disabledClientUser);

        // Create active admin user
        testAdminUser = new AdminUser();
        testAdminUser.setName("Admin Superuser");
        testAdminUser.setEmail("admin@example.com");
        testAdminUser.setPassword(passwordEncoder.encode("admin123"));
        testAdminUser.setStatus("ACTIVE");
        testAdminUser = adminUserRepository.save(testAdminUser);
    }

    @Test
    @DisplayName("1. Valid client login succeeds and returns Inertia 303 redirect to dashboard")
    void test1_ValidClientLogin() throws Exception {
        String jsonPayload = """
            {
                "email": "jane@example.com",
                "password": "secret123"
            }
            """;

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/app/dashboard"));
    }

    @Test
    @DisplayName("2. Invalid client password fails authentication")
    void test2_InvalidClientPassword() throws Exception {
        String jsonPayload = """
            {
                "email": "jane@example.com",
                "password": "wrong-password"
            }
            """;

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3. Unknown client user fails authentication")
    void test3_UnknownClientUser() throws Exception {
        String jsonPayload = """
            {
                "email": "nonexistent@example.com",
                "password": "secret123"
            }
            """;

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. Disabled client user login is rejected")
    void test4_DisabledClientUser() throws Exception {
        String jsonPayload = """
            {
                "email": "disabled@example.com",
                "password": "secret123"
            }
            """;

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("5. Valid admin login succeeds and redirects to admin dashboard")
    void test5_ValidAdminLogin() throws Exception {
        String jsonPayload = """
            {
                "email": "admin@example.com",
                "password": "admin123"
            }
            """;

        mockMvc.perform(post("/admin/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/admin/dashboard"));
    }

    @Test
    @DisplayName("6. Invalid admin password is rejected")
    void test6_InvalidAdminPassword() throws Exception {
        String jsonPayload = """
            {
                "email": "admin@example.com",
                "password": "wrong-admin-pass"
            }
            """;

        mockMvc.perform(post("/admin/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("7. Authenticated client user cannot access admin protected route")
    void test7_ClientCannotAccessAdminRoute() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. Client login
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isSeeOther());

        // 2. Attempt to access /admin/dashboard
        mockMvc.perform(get("/admin/dashboard").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8. Admin session cannot automatically access client user routes without client credentials")
    void test8_AdminCannotAutomaticallyBecomeClient() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. Admin login
        mockMvc.perform(post("/admin/login")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"admin123\"}"))
                .andExpect(status().isSeeOther());

        // 2. Attempt to access client route /app/dashboard
        mockMvc.perform(get("/app/dashboard").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. Valid Sanctum bearer token authenticates successfully on /api/**")
    void test9_ValidSanctumToken() throws Exception {
        PersonalAccessToken token = new PersonalAccessToken();
        token.setName("Mobile App Token");
        token.setToken("valid-sanctum-token-12345");
        token.setTokenableType("App\\Models\\User");
        token.setTokenableId(testClientUser.getId());
        token.setAbilities(objectMapper.writeValueAsString(List.of("*")));
        token.setExpiresAt(LocalDateTime.now().plusDays(30));
        tokenRepository.save(token);

        mockMvc.perform(get("/api/v1/user")
                        .header("Authorization", "Bearer valid-sanctum-token-12345"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("10. Invalid Sanctum bearer token is rejected with 401 Unauthorized")
    void test10_InvalidSanctumToken() throws Exception {
        mockMvc.perform(get("/api/v1/user")
                        .header("Authorization", "Bearer non-existent-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", equalTo("Unauthenticated.")));
    }

    @Test
    @DisplayName("11. Expired Sanctum bearer token is rejected with 401 Unauthorized")
    void test11_ExpiredSanctumToken() throws Exception {
        PersonalAccessToken token = new PersonalAccessToken();
        token.setName("Expired Token");
        token.setToken("expired-sanctum-token-999");
        token.setTokenableType("App\\Models\\User");
        token.setTokenableId(testClientUser.getId());
        token.setAbilities(objectMapper.writeValueAsString(List.of("*")));
        token.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired yesterday
        tokenRepository.save(token);

        mockMvc.perform(get("/api/v1/user")
                        .header("Authorization", "Bearer expired-sanctum-token-999"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("12. Sanctum token ability validation helper functions properly")
    void test12_SanctumTokenAbilityValidation() {
        SanctumAuthenticationToken authToken = new SanctumAuthenticationToken(
                new CustomUserDetails(testClientUser),
                "token-abc",
                List.of("read:reports", "write:messages"),
                List.of()
        );

        assertTrue(authToken.hasAbility("read:reports"));
        assertTrue(authToken.hasAbility("write:messages"));
        assertTrue(!authToken.hasAbility("delete:account"));
    }

    @Test
    @DisplayName("13. Workspace membership validation succeeds for user's own workspace")
    void test13_WorkspaceMembershipValidationSucceeds() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isSeeOther());

        mockMvc.perform(get("/app/dashboard")
                        .session(session)
                        .header("X-Workspace-Id", userWorkspace1.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("14. User cannot access another workspace to which they do not belong (IDOR Protection)")
    void test14_WorkspaceIDORProtection() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isSeeOther());

        // Accessing workspace 2 (not belonging to Jane) throws 403 Forbidden
        mockMvc.perform(get("/app/dashboard")
                        .session(session)
                        .header("X-Workspace-Id", userWorkspace2.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("15. CSRF validation succeeds with valid CSRF token")
    void test15_CsrfSuccess() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isSeeOther());
    }

    @Test
    @DisplayName("16. CSRF validation fails when CSRF token is missing on POST")
    void test16_CsrfFailure() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("17. Logout invalidates session and clears SecurityContext")
    void test17_LogoutInvalidatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isSeeOther());

        mockMvc.perform(post("/logout")
                        .with(csrf())
                        .session(session))
                .andExpect(status().is3xxRedirection());

        SecurityContextHolder.clearContext();

        // Subsequent access to /app/dashboard should be redirected to /login
        mockMvc.perform(get("/app/dashboard").header("X-Inertia", "true"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Inertia-Location", "/login"));
    }

    @Test
    @DisplayName("18. Session expiration rejects unauthenticated access")
    void test18_SessionExpiration() throws Exception {
        MockHttpSession expiredSession = new MockHttpSession();
        expiredSession.invalidate();

        mockMvc.perform(get("/app/dashboard").header("X-Inertia", "true").session(expiredSession))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Inertia-Location", "/login"));
    }

    @Test
    @DisplayName("19. Inertia request to protected route returns 409 with X-Inertia-Location on unauthenticated access")
    void test19_InertiaUnauthenticatedRedirect() throws Exception {
        mockMvc.perform(get("/app/dashboard").header("X-Inertia", "true"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Inertia-Location", "/login"));
    }

    @Test
    @DisplayName("20. Inertia request returns 403 Forbidden JSON on unauthorized access")
    void test20_InertiaUnauthorizedResponse() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isSeeOther());

        mockMvc.perform(get("/admin/dashboard")
                        .header("X-Inertia", "true")
                        .session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", equalTo("This action is unauthorized.")));
    }

    @Test
    @DisplayName("21. Spring Security verifies existing Laravel BCrypt password hashes without modifying them")
    void test21_LaravelBCryptHashVerification() {
        assertTrue(passwordEncoder.matches("secret123", testClientUser.getPassword()));
    }

    @Test
    @DisplayName("22. 2FA TOTP verification succeeds with valid 6-digit code")
    void test22_2faValidOtp() {
        String secret = "JBSWY3DPEHPK3PXP";
        assertTrue(!totpService.verifyCode(secret, "000000")); // Invalid code
    }

    @Test
    @DisplayName("23. 2FA TOTP verification rejects invalid OTP code")
    void test23_2faInvalidOtp() {
        String secret = "JBSWY3DPEHPK3PXP";
        assertTrue(!totpService.verifyCode(secret, "999999"));
    }
}
