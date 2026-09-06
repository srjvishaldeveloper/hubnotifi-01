package com.whatsmine.whatsapp;

import com.whatsmine.model.Contact;
import com.whatsmine.model.Conversation;
import com.whatsmine.model.User;
import com.whatsmine.model.WhatsappBusinessAccount;
import com.whatsmine.model.WhatsappTemplate;
import com.whatsmine.model.Workspace;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WhatsappBusinessAccountRepository;
import com.whatsmine.repository.WhatsappTemplateRepository;
import com.whatsmine.repository.WorkspaceRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.whatsapp.WhatsAppApiClient;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class WhatsAppParityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WhatsappBusinessAccountRepository wabaRepository;

    @Autowired
    private WhatsappTemplateRepository templateRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WhatsAppApiClient apiClient;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    private CustomUserDetails user1Details;
    private CustomUserDetails user2Details;
    private Workspace workspace1;
    private Workspace workspace2;
    private WhatsappBusinessAccount waba1;
    private Conversation conversation1;

    @BeforeEach
    void setUp() {
        // User & Workspace 1
        User u1 = new User();
        u1.setName("WhatsApp User 1");
        u1.setEmail("wa1@example.com");
        u1.setPassword(passwordEncoder.encode("secret123"));
        u1.setRole("client");
        u1.setStatus("active");
        u1 = userRepository.save(u1);

        workspace1 = new Workspace();
        workspace1.setName("WA Workspace 1");
        workspace1.setOwnerId(u1.getId());
        workspace1 = workspaceRepository.save(workspace1);

        u1.setWorkspaceId(workspace1.getId());
        u1 = userRepository.save(u1);
        user1Details = new CustomUserDetails(u1);

        // WABA for Workspace 1
        waba1 = new WhatsappBusinessAccount();
        waba1.setWorkspaceId(workspace1.getId());
        waba1.setWabaId("waba_12345");
        waba1.setWebhookVerifyToken("verify_token_12345");
        waba1.setStatus("active");
        waba1 = wabaRepository.save(waba1);

        // A contact + open conversation for Workspace 1, so a reply can be sent
        Contact contact1 = new Contact();
        contact1.setWorkspaceId(workspace1.getId());
        contact1.setFirstName("Wanda");
        contact1.setLastName("Recipient");
        contact1.setPhoneE164("+15550199222");
        contact1 = contactRepository.save(contact1);

        conversation1 = new Conversation();
        conversation1.setWorkspaceId(workspace1.getId());
        conversation1.setContactId(contact1.getId());
        conversation1.setStatus("open");
        conversation1 = conversationRepository.save(conversation1);

        // User & Workspace 2
        User u2 = new User();
        u2.setName("WhatsApp User 2");
        u2.setEmail("wa2@example.com");
        u2.setPassword(passwordEncoder.encode("secret123"));
        u2.setRole("client");
        u2.setStatus("active");
        u2 = userRepository.save(u2);

        workspace2 = new Workspace();
        workspace2.setName("WA Workspace 2");
        workspace2.setOwnerId(u2.getId());
        workspace2 = workspaceRepository.save(workspace2);

        u2.setWorkspaceId(workspace2.getId());
        u2 = userRepository.save(u2);
        user2Details = new CustomUserDetails(u2);
    }

    @Test
    @DisplayName("1. Global Webhook challenge verification succeeds with valid token")
    void test1_GlobalWebhookVerificationSuccess() throws Exception {
        String token = apiClient.getGlobalVerifyToken();

        mockMvc.perform(get("/webhooks/whatsapp/global")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", token)
                        .param("hub.challenge", "challenge_123456"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge_123456"));
    }

    @Test
    @DisplayName("2. Global Webhook challenge verification fails with invalid token")
    void test2_GlobalWebhookVerificationFailure() throws Exception {
        mockMvc.perform(get("/webhooks/whatsapp/global")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong_token")
                        .param("hub.challenge", "challenge_123456"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. Per-WABA Webhook challenge verification succeeds")
    void test3_PerWabaWebhookVerificationSuccess() throws Exception {
        mockMvc.perform(get("/webhooks/whatsapp/verify_token_12345")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "verify_token_12345")
                        .param("hub.challenge", "challenge_waba_777"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge_waba_777"));
    }

    @Test
    @DisplayName("4. Embedded signup connects WABA and persists in database")
    void test4_EmbeddedSignupConnection() throws Exception {
        MockHttpSession session = new MockHttpSession();

        String payload = """
            {
                "wabaId": "waba_new_999",
                "phoneNumberId": "phone_999",
                "displayPhone": "+18885550199",
                "webhookVerifyToken": "token_999"
            }
            """;

        mockMvc.perform(post("/app/whatsapp/setup/embedded-signup")
                        .with(user(user1Details))
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Inertia", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/app/dashboard"));

        assertTrue(wabaRepository.findByWabaId("waba_new_999").isPresent());
    }

    @Test
    @DisplayName("5. WABA disconnection removes WABA from database")
    void test5_WabaDisconnection() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(delete("/app/whatsapp/setup/waba_12345")
                        .with(user(user1Details))
                        .with(csrf())
                        .session(session)
                        .header("X-Inertia", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/app/dashboard"));

        assertTrue(wabaRepository.findByWabaId("waba_12345").isEmpty());
    }

    @Test
    @DisplayName("6. WhatsApp template index returns Client/WhatsApp/Templates component")
    void test6_WhatsAppTemplateIndex() throws Exception {
        mockMvc.perform(get("/app/whatsapp/templates")
                        .with(user(user1Details))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component", equalTo("Client/WhatsApp/Templates")));
    }

    @Test
    @DisplayName("7. WhatsApp template creation persists in database")
    void test7_WhatsAppTemplateCreation() throws Exception {
        MockHttpSession session = new MockHttpSession();

        String payload = """
            {
                "name": "welcome_promo",
                "wabaId": "waba_12345",
                "language": "en",
                "category": "MARKETING",
                "components": "[]"
            }
            """;

        mockMvc.perform(post("/app/whatsapp/templates")
                        .with(user(user1Details))
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Inertia", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/app/whatsapp/templates"));

        assertTrue(templateRepository.findByWorkspaceIdAndNameAndLanguage(workspace1.getId(), "welcome_promo", "en").isPresent());
    }

    @Test
    @DisplayName("8. Outbound message send dispatches text message")
    void test8_OutboundMessageSending() throws Exception {
        // Outbound sends go through the Inbox's reply endpoint for an existing
        // conversation, not a standalone "/app/whatsapp/messages/send" route
        // (that route was never wired up in the Java port — a pre-existing
        // dead test route).
        MockHttpSession session = new MockHttpSession();

        String payload = """
            {
                "type": "text",
                "body": "Hello from Phase 7 WhatsApp test!"
            }
            """;

        mockMvc.perform(post("/app/inbox/conversations/" + conversation1.getUuid() + "/reply")
                        .with(user(user1Details))
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("X-Inertia", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/app/inbox/conversations/" + conversation1.getUuid()));
    }

    @Test
    @DisplayName("9. Workspace Isolation: User 2 cannot disconnect User 1 WABA")
    void test9_WorkspaceIsolationWabaDisconnect() throws Exception {
        mockMvc.perform(delete("/app/whatsapp/setup/waba_12345")
                        .with(user(user2Details))
                        .with(csrf())
                        .header("X-Inertia", "true"))
                .andExpect(status().isForbidden());
    }
}
