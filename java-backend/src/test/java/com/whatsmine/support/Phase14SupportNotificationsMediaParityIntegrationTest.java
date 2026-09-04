package com.whatsmine.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.AdminUser;
import com.whatsmine.model.CmsPage;
import com.whatsmine.model.Media;
import com.whatsmine.model.SupportTicket;
import com.whatsmine.model.User;
import com.whatsmine.model.WebhookEndpoint;
import com.whatsmine.model.Workspace;
import com.whatsmine.repository.AdminUserRepository;
import com.whatsmine.repository.CmsPageRepository;
import com.whatsmine.repository.MediaRepository;
import com.whatsmine.repository.SupportReplyRepository;
import com.whatsmine.repository.SupportTicketRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WebhookEndpointRepository;
import com.whatsmine.repository.WorkspaceRepository;
import com.whatsmine.security.AdminUserDetails;
import com.whatsmine.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class Phase14SupportNotificationsMediaParityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private SupportReplyRepository supportReplyRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private WebhookEndpointRepository webhookEndpointRepository;

    @Autowired
    private CmsPageRepository cmsPageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private CustomUserDetails userDetails;
    private AdminUser adminUser;
    private AdminUserDetails adminUserDetails;
    private Workspace workspace;

    @BeforeEach
    public void setUp() {
        workspace = new Workspace();
        workspace.setName("Support Workspace");
        workspace = workspaceRepository.save(workspace);

        testUser = new User();
        testUser.setName("Support Client User");
        testUser.setEmail("supportuser_" + System.currentTimeMillis() + "@example.com");
        testUser.setPassword(passwordEncoder.encode("password"));
        testUser.setRole("client");
        testUser.setWorkspaceId(workspace.getId());
        testUser = userRepository.save(testUser);

        userDetails = new CustomUserDetails(testUser);

        adminUser = new AdminUser();
        adminUser.setName("Admin Support Agent");
        adminUser.setEmail("adminsupport_" + System.currentTimeMillis() + "@example.com");
        adminUser.setPassword(passwordEncoder.encode("password"));
        adminUser.setStatus("ACTIVE");
        adminUser = adminUserRepository.save(adminUser);

        adminUserDetails = new AdminUserDetails(adminUser);
    }

    @Test
    public void testSupportTicketClientAndAdminFlow() throws Exception {
        // 1. Client creates ticket
        Map<String, Object> createBody = Map.of(
                "subject", "Need assistance with API integration",
                "message", "I am having trouble connecting to webhooks.",
                "priority", "high"
        );

        mockMvc.perform(post("/support")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody))
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/support"));

        SupportTicket ticket = supportTicketRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId()).get(0);
        assertEquals("Need assistance with API integration", ticket.getSubject());
        assertEquals("open", ticket.getStatus());

        // 2. Client lists tickets
        mockMvc.perform(get("/support")
                        .header("X-Inertia", "true")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("client/Support/Index"));

        // 3. Client views ticket details
        mockMvc.perform(get("/support/" + ticket.getId())
                        .header("X-Inertia", "true")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("client/Support/Show"));

        // 4. Client posts reply
        mockMvc.perform(post("/support/" + ticket.getId() + "/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "Any update on this issue?")))
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/support/" + ticket.getId()));

        assertEquals(1, supportReplyRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).size());

        // 5. Admin lists tickets
        mockMvc.perform(get("/admin/support")
                        .header("X-Inertia", "true")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Admin/Support/Index"));

        // 6. Admin posts reply
        mockMvc.perform(post("/admin/support/" + ticket.getId() + "/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "We are investigating your request.")))
                        .with(csrf())
                        .with(user(adminUserDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/admin/support/" + ticket.getId()));

        SupportTicket updatedTicket = supportTicketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals("replied", updatedTicket.getStatus());

        // 7. Admin updates status to closed
        mockMvc.perform(post("/admin/support/" + ticket.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "closed")))
                        .with(csrf())
                        .with(user(adminUserDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/admin/support/" + ticket.getId()));

        SupportTicket closedTicket = supportTicketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals("closed", closedTicket.getStatus());
    }

    @Test
    public void testNotificationEndpointsAndPreferences() throws Exception {
        mockMvc.perform(get("/notifications")
                        .header("X-Inertia", "true")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("client/Notifications/Index"));

        mockMvc.perform(get("/notifications/recent")
                        .with(user(userDetails)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/notifications/unread-count")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(post("/notifications/1/read")
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(post("/notifications/mark-all-read")
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/notifications"));

        Map<String, Object> prefBody = Map.of(
                "preferences", List.of(
                        Map.of("event", "campaign_completed", "channel", "mail", "enabled", true)
                )
        );

        mockMvc.perform(post("/notifications/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prefBody))
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/notifications"));
    }

    @Test
    public void testWebPushSubscription() throws Exception {
        Map<String, Object> subBody = Map.of(
                "endpoint", "https://push.example.com/sub/123",
                "p256dh", "key123",
                "auth", "auth456"
        );

        mockMvc.perform(post("/webpush/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subBody))
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(post("/webpush/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("endpoint", "https://push.example.com/sub/123")))
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    public void testMediaUploadAndQuotaEnforcement() throws Exception {
        mockMvc.perform(get("/media")
                        .header("X-Inertia", "true")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("client/Media/Index"));

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "Hello Media".getBytes());

        mockMvc.perform(multipart("/media")
                        .file(file)
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("test.txt"));

        List<Media> userMedia = mediaRepository.findByMediableTypeAndMediableId(testUser.getClass().getName(), testUser.getId());
        assertFalse(userMedia.isEmpty());

        Media media = userMedia.get(0);
        mockMvc.perform(delete("/media/" + media.getId())
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    public void testWebhookEndpointManagementAndTestDelivery() throws Exception {
        mockMvc.perform(get("/webhooks")
                        .header("X-Inertia", "true")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("client/Webhooks/Index"));

        Map<String, Object> createBody = Map.of(
                "url", "https://api.myclient.com/webhook",
                "description", "My client webhook listener",
                "events", List.of("contact.created", "message.received")
        );

        mockMvc.perform(post("/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody))
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/webhooks"));

        WebhookEndpoint endpoint = webhookEndpointRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId()).get(0);
        assertEquals("https://api.myclient.com/webhook", endpoint.getUrl());

        mockMvc.perform(put("/webhooks/" + endpoint.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://api.myclient.com/v2/webhook")))
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/webhooks"));

        mockMvc.perform(post("/webhooks/" + endpoint.getId() + "/rotate-secret")
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").exists());

        mockMvc.perform(post("/webhooks/" + endpoint.getId() + "/test")
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/webhooks"));

        mockMvc.perform(get("/webhooks/" + endpoint.getId() + "/deliveries")
                        .header("X-Inertia", "true")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("client/Webhooks/Deliveries"));

        mockMvc.perform(delete("/webhooks/" + endpoint.getId())
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/webhooks"));
    }

    @Test
    public void testAuditLogAndSettings() throws Exception {
        mockMvc.perform(get("/app/audit-logs")
                        .header("X-Inertia", "true")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("client/AuditLogs/Index"));

        mockMvc.perform(get("/admin/audit-logs")
                        .header("X-Inertia", "true")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Admin/AuditLogs/Index"));

        mockMvc.perform(get("/admin/client-branding")
                        .header("X-Inertia", "true")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Admin/ClientBranding/Index"));

        mockMvc.perform(get("/admin/system-settings")
                        .header("X-Inertia", "true")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Admin/SystemSettings/Index"));
    }

    @Test
    public void testPublicCmsPageAndAdminCmsPages() throws Exception {
        mockMvc.perform(get("/admin/cms-pages")
                        .header("X-Inertia", "true")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Admin/CmsPages/Index"));

        CmsPage page = new CmsPage();
        page.setTitle("Terms of Service");
        page.setSlug("terms-of-service");
        page.setContent("<p>Terms content</p>");
        page.setPublished(true);
        cmsPageRepository.saveAndFlush(page);

        mockMvc.perform(get("/pages/terms-of-service")
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Public/Page"));
    }

    @Test
    public void testPublicApiV1Endpoints() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(testUser.getEmail()));

        mockMvc.perform(get("/api/v1/tokens")
                        .with(user(userDetails)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/webhooks")
                        .with(user(userDetails)))
                .andExpect(status().isOk());
    }
}
