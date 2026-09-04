package com.whatsmine.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.whatsmine.model.ChannelAccount;
import com.whatsmine.model.Contact;
import com.whatsmine.model.Conversation;
import com.whatsmine.model.InboxLabel;
import com.whatsmine.model.Message;
import com.whatsmine.model.User;
import com.whatsmine.model.Workspace;

import com.whatsmine.repository.ChannelAccountRepository;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.repository.InboxLabelRepository;
import com.whatsmine.repository.MessageRepository;
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

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
public class InboxParityIntegrationTest {

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
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChannelAccountRepository channelAccountRepository;

    @Autowired
    private InboxLabelRepository inboxLabelRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user1;
    private User user2;
    private CustomUserDetails user1Details;
    private CustomUserDetails user2Details;
    private Workspace workspace1;
    private Workspace workspace2;
    private Contact contact1;
    private ChannelAccount channelAccount1;
    private Conversation conversation1;

    @BeforeEach
    void setUp() {
        workspace1 = new Workspace();
        workspace1.setName("Inbox Workspace 1");
        workspace1 = workspaceRepository.save(workspace1);

        workspace2 = new Workspace();
        workspace2.setName("Inbox Workspace 2");
        workspace2 = workspaceRepository.save(workspace2);

        user1 = new User();
        user1.setName("Agent One");
        user1.setEmail("agent1@example.com");
        user1.setPassword(passwordEncoder.encode("Password123!"));
        user1.setWorkspaceId(workspace1.getId());
        user1 = userRepository.save(user1);
        user1Details = new CustomUserDetails(user1);

        user2 = new User();
        user2.setName("Agent Two");
        user2.setEmail("agent2@example.com");
        user2.setPassword(passwordEncoder.encode("Password123!"));
        user2.setWorkspaceId(workspace2.getId());
        user2 = userRepository.save(user2);
        user2Details = new CustomUserDetails(user2);

        contact1 = new Contact();
        contact1.setWorkspaceId(workspace1.getId());
        contact1.setFirstName("Alice");
        contact1.setLastName("Smith");
        contact1.setPhoneE164("+1234567890");
        contact1 = contactRepository.save(contact1);

        channelAccount1 = new ChannelAccount();
        channelAccount1.setWorkspaceId(workspace1.getId());
        channelAccount1.setChannel("whatsapp");
        channelAccount1.setDisplayName("Main WhatsApp");
        channelAccount1.setPhoneNumberId("100200300");
        channelAccount1.setStatus("active");
        channelAccount1 = channelAccountRepository.save(channelAccount1);

        conversation1 = new Conversation();
        conversation1.setWorkspaceId(workspace1.getId());
        conversation1.setContactId(contact1.getId());
        conversation1.setChannelAccountId(channelAccount1.getId());
        conversation1.setStatus("open");
        conversation1.setUnreadCount(1);
        conversation1.setLastMessageAt(LocalDateTime.now());
        conversation1 = conversationRepository.save(conversation1);

        Message msg = new Message();
        msg.setConversationId(conversation1.getId());
        msg.setDirection("in");
        msg.setChannel("whatsapp");
        msg.setType("text");
        msg.setBody("Hello from customer!");
        msg.setStatus("delivered");
        messageRepository.save(msg);
    }

    @Test
    void test1_InboxIndexInertiaPage() throws Exception {
        mockMvc.perform(get("/app/inbox")
                        .with(user(user1Details))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("Inbox/Index"))
                .andExpect(jsonPath("$.props.conversations.content").isArray());
    }

    @Test
    void test2_ConversationShowPageMarksAsRead() throws Exception {
        mockMvc.perform(get("/app/inbox/conversations/" + conversation1.getUuid())
                        .with(user(user1Details))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("Inbox/Show"))
                .andExpect(jsonPath("$.props.conversation.uuid").value(conversation1.getUuid()))
                .andExpect(jsonPath("$.props.messages").isArray());

        Conversation updated = conversationRepository.findById(conversation1.getId()).orElseThrow();
        assertEquals(0, updated.getUnreadCount());
    }

    @Test
    void test3_SendOutboundReply() throws Exception {
        Map<String, Object> body = Map.of(
                "body", "Hello back from support agent!",
                "type", "text"
        );

        mockMvc.perform(post("/app/inbox/conversations/" + conversation1.getUuid() + "/reply")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-Inertia", "true"))
                .andExpect(status().is(303));

        assertEquals(2, messageRepository.findByConversationId(conversation1.getId()).size());
    }

    @Test
    void test4_AssignConversation() throws Exception {
        Map<String, Object> body = Map.of("user_id", user1.getId());

        mockMvc.perform(post("/app/inbox/conversations/" + conversation1.getUuid() + "/assign")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-Inertia", "true"))
                .andExpect(status().is(303));

        Conversation updated = conversationRepository.findById(conversation1.getId()).orElseThrow();
        assertEquals(user1.getId(), updated.getAssignedUserId());
    }

    @Test
    void test5_UpdateConversationStatus() throws Exception {
        Map<String, String> body = Map.of("status", "resolved");

        mockMvc.perform(post("/app/inbox/conversations/" + conversation1.getUuid() + "/status")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-Inertia", "true"))
                .andExpect(status().is(303));

        Conversation updated = conversationRepository.findById(conversation1.getId()).orElseThrow();
        assertEquals("resolved", updated.getStatus());
        assertNotNull(updated.getResolvedAt());
    }

    @Test
    void test6_InternalNotesLifecycle() throws Exception {
        Map<String, String> body = Map.of("body", "Internal note for agent team");

        mockMvc.perform(post("/app/inbox/conversations/" + conversation1.getUuid() + "/notes")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Internal note for agent team"));

        mockMvc.perform(get("/app/inbox/conversations/" + conversation1.getUuid() + "/notes")
                        .with(user(user1Details)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Internal note for agent team"));
    }

    @Test
    void test7_InboxLabelsLifecycle() throws Exception {
        Map<String, String> labelBody = Map.of("name", "VIP", "color", "#FF0000");

        mockMvc.perform(post("/app/inbox/labels")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(labelBody))
                        .header("X-Inertia", "true"))
                .andExpect(status().is(303));

        InboxLabel label = inboxLabelRepository.findByWorkspaceIdAndName(workspace1.getId(), "VIP").orElseThrow();

        Map<String, Object> attachBody = Map.of("label_id", label.getId());
        mockMvc.perform(post("/app/inbox/conversations/" + conversation1.getUuid() + "/labels")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attachBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void test8_WorkspaceIsolationPreventCrossWorkspaceAccess() throws Exception {
        // User 2 (Workspace 2) attempts to access User 1's conversation (Workspace 1)
        mockMvc.perform(get("/app/inbox/conversations/" + conversation1.getUuid())
                        .with(user(user2Details))
                        .header("X-Inertia", "true"))
                .andExpect(status().isNotFound());
    }
}
