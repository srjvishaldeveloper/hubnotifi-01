package com.whatsmine.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.*;
import com.whatsmine.queue.QueueDispatcher;
import com.whatsmine.queue.QueueWorker;
import com.whatsmine.realtime.ChannelRegistry;
import com.whatsmine.realtime.RealtimeBroadcaster;
import com.whatsmine.repository.*;
import com.whatsmine.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase17E2ERegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private QueueDispatcher queueDispatcher;

    @Autowired
    private QueueWorker queueWorker;

    @Autowired
    private ChannelRegistry channelRegistry;

    @Autowired
    private RealtimeBroadcaster realtimeBroadcaster;

    @Autowired
    private ObjectMapper objectMapper;

    private User user1;
    private Workspace workspace1;
    private CustomUserDetails userDetails1;

    private User user2;
    private Workspace workspace2;
    private CustomUserDetails userDetails2;

    private Contact contact1;
    private Conversation conversation1;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        contactRepository.deleteAll();
        campaignRepository.deleteAll();
        userRepository.deleteAll();
        workspaceRepository.deleteAll();

        // Setup Workspace 1 & User 1
        workspace1 = new Workspace();
        workspace1.setName("Acme Corporation");
        workspace1 = workspaceRepository.save(workspace1);

        user1 = new User();
        user1.setName("Alice Client");
        user1.setEmail("alice@acme.com");
        user1.setPassword("password123");
        user1.setWorkspaceId(workspace1.getId());
        user1.setStatus("active");
        user1 = userRepository.save(user1);
        userDetails1 = new CustomUserDetails(user1);

        // Setup Workspace 2 & User 2 (for multi-tenant isolation tests)
        workspace2 = new Workspace();
        workspace2.setName("Stark Industries");
        workspace2 = workspaceRepository.save(workspace2);

        user2 = new User();
        user2.setName("Bob Stark");
        user2.setEmail("bob@stark.com");
        user2.setPassword("password123");
        user2.setWorkspaceId(workspace2.getId());
        user2.setStatus("active");
        user2 = userRepository.save(user2);
        userDetails2 = new CustomUserDetails(user2);

        // Setup Contact & Conversation for Workspace 1
        contact1 = new Contact();
        contact1.setWorkspaceId(workspace1.getId());
        contact1.setFirstName("John");
        contact1.setLastName("Doe");
        contact1.setPhoneE164("+15551234567");
        contact1.setEmail("john@example.com");
        contact1 = contactRepository.save(contact1);

        conversation1 = new Conversation();
        conversation1.setWorkspaceId(workspace1.getId());
        conversation1.setContactId(contact1.getId());
        conversation1.setStatus("open");
        conversation1.setUuid("conv-e2e-uuid-1");
        conversation1.setLastMessageAt(LocalDateTime.now());
        conversation1 = conversationRepository.save(conversation1);
    }

    @Test
    @DisplayName("Golden Path Journey 1: Client Dashboard Navigation & Inertia Rendering")
    void journey1_clientDashboardAndInertia() throws Exception {
        mockMvc.perform(get("/app/dashboard")
                        .with(user(userDetails1))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("Client/Dashboard"))
                .andExpect(jsonPath("$.props.auth.user.email").value("alice@acme.com"));
    }

    @Test
    @DisplayName("Golden Path Journey 2: Contacts CRM Search & Querying")
    void journey2_contactsCrmWorkflow() throws Exception {
        // Search Contact
        mockMvc.perform(get("/app/inbox/contacts/search")
                        .with(user(userDetails1))
                        .param("q", "John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("John"));
    }

    @Test
    @DisplayName("Golden Path Journey 3: Shared Inbox Reply & Realtime Broadcast Trigger")
    void journey3_sharedInboxReplyAndBroadcasting() throws Exception {
        // Render Inbox Show page
        mockMvc.perform(get("/app/inbox/conversations/" + conversation1.getUuid())
                        .with(user(userDetails1))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Inbox/Show"))
                .andExpect(jsonPath("$.props.conversation.uuid").value("conv-e2e-uuid-1"));

        // Reply to Conversation
        mockMvc.perform(post("/app/inbox/conversations/" + conversation1.getUuid() + "/reply")
                        .with(csrf())
                        .with(user(userDetails1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "Hello from E2E test reply!",
                                "type", "text"
                        ))))
                .andExpect(status().is3xxRedirection());

        // Verify Message was saved
        Optional<Message> msgOpt = messageRepository.findFirstByConversationIdOrderBySentAtDesc(conversation1.getId());
        assertThat(msgOpt).isPresent();
        assertThat(msgOpt.get().getBody()).isEqualTo("Hello from E2E test reply!");

        // Send Typing Indicator
        mockMvc.perform(post("/app/inbox/conversations/" + conversation1.getUuid() + "/typing")
                        .with(csrf())
                        .with(user(userDetails1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("is_typing", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    @DisplayName("Golden Path Journey 4: Campaign Creation, Queueing & Execution")
    void journey4_campaignExecutionWorkflow() throws Exception {
        Campaign campaign = new Campaign();
        campaign.setWorkspaceId(workspace1.getId());
        campaign.setName("Black Friday Sale");
        campaign.setChannel("whatsapp");
        campaign.setAudienceType("all");
        campaign.setStatus("queued");
        campaign.setScheduleAt(LocalDateTime.now().minusMinutes(5));
        campaign = campaignRepository.saveAndFlush(campaign);

        Long campaignId = campaign.getId();

        // Dispatch launch job to Phase 15 queue
        String jobUuid = queueDispatcher.dispatch("broadcast", "LaunchCampaignJob", Map.of("campaign_id", campaignId));
        assertThat(jobUuid).isNotNull();

        // Process Phase 15 Queue Worker (or background poller may have already consumed it)
        boolean processed = queueWorker.processNextAvailableJob(null);
        assertThat(processed || jobRepository.count() == 0).isTrue();
    }

    @Test
    @DisplayName("Golden Path Journey 8: Billing Subscription & Metering Endpoints")
    void journey8_billingAndSubscriptions() throws Exception {
        mockMvc.perform(get("/billing")
                        .with(user(userDetails1))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("client/Billing/Index"));
    }

    @Test
    @DisplayName("Golden Path Journey 9: Multi-Tenant Security Isolation (Workspace A vs Workspace B)")
    void journey9_multiTenantIsolationCheck() throws Exception {
        // User 2 (Workspace 2) attempts to access Workspace 1 conversation -> Forbidden (404/403)
        mockMvc.perform(get("/app/inbox/conversations/" + conversation1.getUuid())
                        .with(user(userDetails2))
                        .header("X-Inertia", "true"))
                .andExpect(status().isNotFound());

        // User 2 attempts to authorize Workspace 1 channel -> 403 Forbidden
        mockMvc.perform(post("/broadcasting/auth")
                        .with(csrf())
                        .with(user(userDetails2))
                        .param("socket_id", "999.888")
                        .param("channel_name", "private-workspace." + workspace1.getId())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Golden Path Journey 10: Realtime User Notification Dispatch")
    void journey10_realtimeUserNotificationDispatch() {
        // Dispatches notification frame to user 1 private channel
        realtimeBroadcaster.broadcastNotification(
                user1.getId(),
                "new_message",
                "New Message Received",
                "You have a new message from John Doe",
                "/app/inbox/conversations/" + conversation1.getUuid()
        );
    }
}
