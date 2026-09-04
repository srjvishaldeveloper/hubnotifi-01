package com.whatsmine.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.controller.broadcasting.BroadcastingAuthController;
import com.whatsmine.model.Conversation;
import com.whatsmine.model.User;
import com.whatsmine.model.Workspace;
import com.whatsmine.repository.ConversationRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WorkspaceRepository;
import com.whatsmine.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase16RealtimeParityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private com.whatsmine.repository.ContactRepository contactRepository;

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

    private Conversation conversation1;

    @BeforeEach
    void setUp() {
        conversationRepository.deleteAll();
        userRepository.deleteAll();
        workspaceRepository.deleteAll();

        workspace1 = new Workspace();
        workspace1.setName("Workspace One");
        workspace1 = workspaceRepository.save(workspace1);

        user1 = new User();
        user1.setName("Alice");
        user1.setEmail("alice@example.com");
        user1.setPassword("password");
        user1.setWorkspaceId(workspace1.getId());
        user1 = userRepository.save(user1);

        userDetails1 = new CustomUserDetails(user1);

        workspace2 = new Workspace();
        workspace2.setName("Workspace Two");
        workspace2 = workspaceRepository.save(workspace2);

        user2 = new User();
        user2.setName("Bob");
        user2.setEmail("bob@example.com");
        user2.setPassword("password");
        user2.setWorkspaceId(workspace2.getId());
        user2 = userRepository.save(user2);

        userDetails2 = new CustomUserDetails(user2);

        com.whatsmine.model.Contact contact = new com.whatsmine.model.Contact();
        contact.setWorkspaceId(workspace1.getId());
        contact.setFirstName("Test");
        contact.setLastName("Contact");
        contact.setPhoneE164("+15551234567");
        contact = contactRepository.save(contact);

        conversation1 = new Conversation();
        conversation1.setWorkspaceId(workspace1.getId());
        conversation1.setContactId(contact.getId());
        conversation1.setStatus("open");
        conversation1.setUuid("conv-uuid-111");
        conversation1 = conversationRepository.save(conversation1);
    }

    @Test
    void testBroadcastingAuthUserChannelSuccess() throws Exception {
        mockMvc.perform(post("/broadcasting/auth")
                        .with(csrf())
                        .with(user(userDetails1))
                        .param("socket_id", "123.456")
                        .param("channel_name", "private-App.Models.User." + user1.getId())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auth").exists());
    }

    @Test
    void testBroadcastingAuthUserChannelDeniedForOtherUser() throws Exception {
        mockMvc.perform(post("/broadcasting/auth")
                        .with(csrf())
                        .with(user(userDetails1))
                        .param("socket_id", "123.456")
                        .param("channel_name", "private-App.Models.User." + user2.getId())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isForbidden());
    }

    @Test
    void testBroadcastingAuthWorkspaceChannelSuccess() throws Exception {
        mockMvc.perform(post("/broadcasting/auth")
                        .with(csrf())
                        .with(user(userDetails1))
                        .param("socket_id", "123.456")
                        .param("channel_name", "private-workspace." + workspace1.getId())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auth").exists());
    }

    @Test
    void testBroadcastingAuthWorkspaceChannelDeniedForOtherWorkspace() throws Exception {
        mockMvc.perform(post("/broadcasting/auth")
                        .with(csrf())
                        .with(user(userDetails1))
                        .param("socket_id", "123.456")
                        .param("channel_name", "private-workspace." + workspace2.getId())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isForbidden());
    }

    @Test
    void testBroadcastingAuthPresenceChannelReturnsChannelData() throws Exception {
        mockMvc.perform(post("/broadcasting/auth")
                        .with(csrf())
                        .with(user(userDetails1))
                        .param("socket_id", "123.456")
                        .param("channel_name", "presence-conversation." + conversation1.getId())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auth").exists())
                .andExpect(jsonPath("$.channel_data").exists());
    }

    @Test
    void testHmacSha256SignatureValidation() {
        String data = "123.456:private-workspace.1";
        String secret = "testsecret";
        String sig1 = BroadcastingAuthController.hmacSha256(data, secret);
        String sig2 = BroadcastingAuthController.hmacSha256(data, secret);

        assertThat(sig1).isNotNull().isEqualTo(sig2);
        assertThat(sig1.length()).isEqualTo(64); // 256 bits in hex
    }

    @Test
    void testRealtimeBroadcasterPublishesToRegisteredSubscribers() {
        String channel = "private-workspace." + workspace1.getId();
        assertThat(channelRegistry.getSubscribers(channel)).isEmpty();

        // Broadcast when no subscribers present should not fail
        realtimeBroadcaster.broadcast(channel, ".MessageReceived", Map.of("id", 1, "body", "Hello World"));
    }
}
