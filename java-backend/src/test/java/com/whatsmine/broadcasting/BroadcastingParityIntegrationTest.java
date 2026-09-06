package com.whatsmine.broadcasting;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.whatsmine.model.Campaign;
import com.whatsmine.model.Contact;
import com.whatsmine.model.User;
import com.whatsmine.model.Workspace;

import com.whatsmine.repository.CampaignRecipientRepository;
import com.whatsmine.repository.CampaignRepository;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WorkspaceRepository;

import com.whatsmine.security.CustomUserDetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.http.MediaType;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.whatsmine.queue.QueueWorker;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class BroadcastingParityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignRecipientRepository campaignRecipientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private QueueWorker queueWorker;

    private User user1;
    private User user2;
    private CustomUserDetails user1Details;
    private CustomUserDetails user2Details;
    private Workspace workspace1;
    private Workspace workspace2;
    private Contact contact1;
    private Campaign campaign1;

    @BeforeEach
    void setUp() {
        workspace1 = new Workspace();
        workspace1.setName("Broadcasting Workspace 1");
        workspace1 = workspaceRepository.save(workspace1);

        workspace2 = new Workspace();
        workspace2.setName("Broadcasting Workspace 2");
        workspace2 = workspaceRepository.save(workspace2);

        user1 = new User();
        user1.setName("Broadcast Manager 1");
        user1.setEmail("bm1@example.com");
        user1.setPassword(passwordEncoder.encode("Password123!"));
        user1.setWorkspaceId(workspace1.getId());
        user1 = userRepository.save(user1);
        user1Details = new CustomUserDetails(user1);

        user2 = new User();
        user2.setName("Broadcast Manager 2");
        user2.setEmail("bm2@example.com");
        user2.setPassword(passwordEncoder.encode("Password123!"));
        user2.setWorkspaceId(workspace2.getId());
        user2 = userRepository.save(user2);
        user2Details = new CustomUserDetails(user2);

        contact1 = new Contact();
        contact1.setWorkspaceId(workspace1.getId());
        contact1.setFirstName("Bob");
        contact1.setLastName("Jones");
        contact1.setPhoneE164("+1987654321");
        contact1.setEmail("bob@example.com");
        contact1 = contactRepository.save(contact1);

        campaign1 = new Campaign();
        campaign1.setWorkspaceId(workspace1.getId());
        campaign1.setName("Spring Promo Blast");
        campaign1.setChannel("whatsapp");
        campaign1.setAudienceType("contact_list");
        campaign1.setStatus("draft");
        campaign1.setCreatedBy(user1.getId());
        campaign1 = campaignRepository.save(campaign1);
    }

    @Test
    void test1_CampaignIndexInertiaPage() throws Exception {
        mockMvc.perform(get("/app/broadcasts/campaigns")
                        .with(user(user1Details))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("Broadcasting/Campaigns/Index"))
                .andExpect(jsonPath("$.props.campaigns.data").isArray());
    }

    @Test
    void test2_CreateCampaignWizard() throws Exception {
        mockMvc.perform(get("/app/broadcasts/campaigns/create")
                        .with(user(user1Details))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("Broadcasting/Campaigns/Wizard"))
                .andExpect(jsonPath("$.props.whatsappTemplates").isArray());
    }

    @Test
    void test3_StoreCampaignDraft() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Flash Sale Draft",
                "channel", "whatsapp",
                "audience_type", "contact_list"
        );

        mockMvc.perform(post("/app/broadcasts/campaigns/draft")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").exists());
    }

    @Test
    void test4_AudiencePreview() throws Exception {
        Map<String, Object> body = Map.of(
                "channel", "whatsapp",
                "audience_type", "contact_list"
        );

        mockMvc.perform(post("/app/broadcasts/campaigns/audience-preview")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(1))
                .andExpect(jsonPath("$.deliverable").value(1))
                .andExpect(jsonPath("$.sample").isArray());
    }

    @Test
    void test5_TestSendMessage() throws Exception {
        Map<String, String> body = Map.of("phone_e164", "+1987654321");

        mockMvc.perform(post("/app/broadcasts/campaigns/" + campaign1.getUuid() + "/test-send")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.channel").value("whatsapp"));
    }

    @Test
    void test6_LaunchCampaign() throws Exception {
        mockMvc.perform(post("/app/broadcasts/campaigns/" + campaign1.getUuid() + "/launch")
                        .with(user(user1Details))
                        .with(csrf())
                        .header("X-Inertia", "true"))
                .andExpect(status().is(303));

        // Launch now just flips the campaign to "queued" and dispatches a
        // LaunchCampaignJob — the real audience resolution and sends happen
        // asynchronously on the real queue (Launch -> Chunk -> Send ->
        // Finalize). Drive the same QueueWorker bean production uses,
        // synchronously and within this test's own transaction, so the real
        // handler code runs deterministically without waiting on the
        // background @Scheduled poll thread or committing test data.
        for (int i = 0; i < 20 && queueWorker.processNextAvailableJob(null); i++) {
            // drain the queue
        }

        Campaign updated = campaignRepository.findById(campaign1.getId()).orElseThrow();

        // No WhatsApp ChannelAccount is configured for this workspace, so the
        // real send genuinely fails validation ("No active WhatsApp channel
        // connected") rather than silently succeeding — that's the correct
        // outcome for a workspace with no channel wired up, and proves the
        // full pipeline (not just the launch endpoint) actually ran.
        assertEquals("failed", updated.getStatus());
        assertEquals(1, campaignRecipientRepository.countByCampaignId(campaign1.getId()));
        var recipient = campaignRecipientRepository.findByCampaignIdAndContactId(campaign1.getId(), contact1.getId()).orElseThrow();
        assertEquals("failed", recipient.getStatus());
        assertTrue(recipient.getFailedReason().contains("No active WhatsApp channel connected"));
    }

    @Test
    void test7_PauseCampaign() throws Exception {
        campaign1.setStatus("sending");
        campaignRepository.save(campaign1);

        mockMvc.perform(post("/app/broadcasts/campaigns/" + campaign1.getUuid() + "/pause")
                        .with(user(user1Details))
                        .with(csrf())
                        .header("X-Inertia", "true"))
                .andExpect(status().is(303));

        Campaign updated = campaignRepository.findById(campaign1.getId()).orElseThrow();
        assertEquals("paused", updated.getStatus());
    }

    @Test
    void test8_WorkspaceIsolationPreventCrossWorkspaceAccess() throws Exception {
        // User 2 (Workspace 2) attempts to access User 1's campaign (Workspace 1)
        mockMvc.perform(get("/app/broadcasts/campaigns/" + campaign1.getUuid())
                        .with(user(user2Details))
                        .header("X-Inertia", "true"))
                .andExpect(status().isNotFound());
    }
}
