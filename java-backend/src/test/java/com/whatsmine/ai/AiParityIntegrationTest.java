package com.whatsmine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.*;
import com.whatsmine.repository.*;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.ai.EmbeddingStore;
import com.whatsmine.service.ai.LlmGateway;
import com.whatsmine.service.ai.llm.LlmResponse;
import com.whatsmine.service.automation.AutomationEngine;
import com.whatsmine.service.automation.WorkflowGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.whatsmine.queue.QueueWorker;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AiParityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private AiProviderConfigRepository providerConfigRepository;
    @Autowired private AiKnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired private AiKbDocumentRepository documentRepository;
    @Autowired private AiKbChunkRepository chunkRepository;
    @Autowired private AiChatbotRepository chatbotRepository;
    @Autowired private AutomationRepository automationRepository;
    @Autowired private AutomationRunRepository automationRunRepository;
    @Autowired private AutomationEngine automationEngine;
    @Autowired private WorkflowGenerator workflowGenerator;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private QueueWorker queueWorker;

    @MockBean
    private LlmGateway llmGateway;

    private User user1;
    private User user2;
    private CustomUserDetails user1Details;
    private CustomUserDetails user2Details;
    private Workspace workspace1;
    private Workspace workspace2;
    private Contact contact1;
    private AiKnowledgeBase kb1;
    private AiChatbot chatbot1;

    @BeforeEach
    void setUp() {
        workspace1 = new Workspace();
        workspace1.setName("AI Workspace 1");
        workspace1 = workspaceRepository.save(workspace1);

        workspace2 = new Workspace();
        workspace2.setName("AI Workspace 2");
        workspace2 = workspaceRepository.save(workspace2);

        user1 = new User();
        user1.setName("AI Manager 1");
        user1.setEmail("aim1@example.com");
        user1.setPassword(passwordEncoder.encode("Password123!"));
        user1.setWorkspaceId(workspace1.getId());
        user1 = userRepository.save(user1);
        user1Details = new CustomUserDetails(user1);

        user2 = new User();
        user2.setName("AI Manager 2");
        user2.setEmail("aim2@example.com");
        user2.setPassword(passwordEncoder.encode("Password123!"));
        user2.setWorkspaceId(workspace2.getId());
        user2 = userRepository.save(user2);
        user2Details = new CustomUserDetails(user2);

        contact1 = new Contact();
        contact1.setWorkspaceId(workspace1.getId());
        contact1.setFirstName("Bob");
        contact1.setLastName("Jones");
        contact1.setEmail("bob@example.com");
        contact1.setPhoneE164("+15555550299");
        contact1 = contactRepository.save(contact1);

        kb1 = new AiKnowledgeBase();
        kb1.setWorkspaceId(workspace1.getId());
        kb1.setName("Customer Support KB");
        kb1 = knowledgeBaseRepository.save(kb1);

        chatbot1 = new AiChatbot();
        chatbot1.setWorkspaceId(workspace1.getId());
        chatbot1.setName("Support Bot");
        chatbot1.setAiKbId(kb1.getId());
        chatbot1.setSystemPrompt("You are a helpful customer support bot.");
        chatbot1 = chatbotRepository.save(chatbot1);

        // Setup mock LLM behavior
        when(llmGateway.chat(any(), any(), any(), any(), any()))
                .thenReturn(new LlmResponse("Hello from mock AI!", 10, 20, "gpt-4o-mini", 120));
        when(llmGateway.chat(any(), any(), any()))
                .thenReturn(new LlmResponse("Hello from mock AI!", 10, 20, "gpt-4o-mini", 120));
        when(llmGateway.embed(any(), any()))
                .thenReturn(List.of(List.of(0.1, 0.2, 0.3, 0.4)));
    }

    @Test
    void test1_GetAiProvidersList() throws Exception {
        mockMvc.perform(get("/app/ai/providers")
                        .header("X-Inertia", "true")
                        .with(user(user1Details)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"));
    }

    @Test
    void test2_UpdateAiProviderConfig() throws Exception {
        Map<String, Object> body = Map.of(
                "api_key", "sk-test-key-123456",
                "default_model_chat", "gpt-4o-mini",
                "enabled", true
        );

        mockMvc.perform(put("/app/ai/providers/openai")
                        .with(user(user1Details)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/app/ai/providers"));

        assertTrue(providerConfigRepository.findByWorkspaceIdAndProvider(workspace1.getId(), "openai").isPresent());
    }

    @Test
    void test3_KnowledgeBasesIndexAndStore() throws Exception {
        mockMvc.perform(get("/app/ai/knowledge-bases")
                        .header("X-Inertia", "true")
                        .with(user(user1Details)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"));

        Map<String, Object> body = Map.of("name", "Product Catalog KB");

        mockMvc.perform(post("/app/ai/knowledge-bases")
                        .with(user(user1Details)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isSeeOther());

        List<AiKnowledgeBase> kbs = knowledgeBaseRepository.findByWorkspaceIdOrderByIdDesc(workspace1.getId());
        assertTrue(kbs.stream().anyMatch(k -> "Product Catalog KB".equals(k.getName())));
    }

    @Test
    void test4_AddAndIndexDocument() throws Exception {
        // The real frontend always submits this as multipart/form-data (a file
        // input shares the form even for non-file source types), and
        // AiKnowledgeBaseController.addDocument() binds via @RequestParam
        // accordingly — not a JSON body.
        mockMvc.perform(multipart("/app/ai/knowledge-bases/" + kb1.getUuid() + "/documents")
                        .param("source_type", "text")
                        .param("title", "FAQ Document")
                        .param("source_ref", "Q: What are business hours?\nA: 9 AM to 5 PM Monday to Friday.")
                        .with(user(user1Details)).with(csrf()))
                .andExpect(status().isSeeOther());

        // Indexing now always happens off-request via the real IndexDocumentJob
        // queue (matching PHP), so the document is "pending" right after the
        // request returns. Drive the same QueueWorker bean production uses,
        // synchronously and within this test's own transaction, so the real
        // handler runs deterministically without a background poll thread.
        for (int i = 0; i < 10 && queueWorker.processNextAvailableJob(null); i++) {
            // drain the queue
        }

        List<AiKbDocument> docs = documentRepository.findByKbId(kb1.getId());
        assertFalse(docs.isEmpty());
        assertEquals("indexed", docs.get(0).getStatus());

        List<AiKbChunk> chunks = chunkRepository.findByKbId(kb1.getId());
        assertFalse(chunks.isEmpty());
    }

    @Test
    void test5_ChatbotsIndexAndStore() throws Exception {
        mockMvc.perform(get("/app/ai/chatbots")
                        .header("X-Inertia", "true")
                        .with(user(user1Details)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"));

        Map<String, Object> body = Map.of("name", "Sales Bot");

        mockMvc.perform(post("/app/ai/chatbots")
                        .with(user(user1Details)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isSeeOther());

        List<AiChatbot> chatbots = chatbotRepository.findByWorkspaceIdOrderByIdDesc(workspace1.getId());
        assertTrue(chatbots.stream().anyMatch(b -> "Sales Bot".equals(b.getName())));
    }

    @Test
    void test6_ChatbotPlayground() throws Exception {
        Map<String, Object> body = Map.of(
                "message", "Hello, what are your business hours?",
                "history", List.of()
        );

        mockMvc.perform(post("/app/ai/chatbots/" + chatbot1.getUuid() + "/playground")
                        .with(user(user1Details)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Hello from mock AI!"));
    }

    @Test
    void test7_WorkflowGenerator() {
        String mockJsonResponse = """
                {
                  "name": "Auto Support Flow",
                  "trigger_type": "message.received",
                  "nodes": [
                    { "id": "node_1", "type": "ai_reply", "data": { "prompt": "Reply politely" } }
                  ],
                  "edges": [
                    { "source": "trigger-1", "target": "node_1" }
                  ]
                }
                """;

        when(llmGateway.chat(eq(workspace1.getId()), any(), any()))
                .thenReturn(new LlmResponse(mockJsonResponse, 50, 100, "gpt-4o-mini", 200));

        Map<String, Object> result = workflowGenerator.generate(workspace1.getId(), "Create a support automation");
        assertNotNull(result);
        assertEquals("Auto Support Flow", result.get("name"));
        assertEquals("message.received", result.get("trigger_type"));

        List<?> nodes = (List<?>) result.get("nodes");
        assertFalse(nodes.isEmpty());
    }

    @Test
    void test8_AutomationAiReplyNode() {
        Map<String, Object> triggerNode = Map.of("id", "n1", "type", "trigger", "data", Map.of("triggerType", "message.received"));
        Map<String, Object> aiNode = Map.of("id", "n2", "type", "ai_reply", "data", Map.of("prompt", "Help customer {{contact.name}}"));

        Automation autoAi = new Automation();
        autoAi.setWorkspaceId(workspace1.getId());
        autoAi.setName("AI Reply Test");
        autoAi.setStatus("active");
        autoAi.setNodes(List.of(triggerNode, aiNode));
        autoAi.setEdges(List.of(Map.of("id", "e1", "source", "n1", "target", "n2")));
        autoAi = automationRepository.save(autoAi);

        automationEngine.triggerForContact(autoAi, contact1.getId(), Map.of("message_body", "Need help"));

        List<AutomationRun> runs = automationRunRepository.findByAutomationIdOrderByStartedAtDesc(autoAi.getId());
        assertFalse(runs.isEmpty());
        assertEquals("completed", runs.get(0).getStatus());
        assertEquals("Hello from mock AI!", runs.get(0).getContext().get("last_ai_reply"));
    }

    @Test
    void test9_AutomationRunChatbotNode() {
        Map<String, Object> triggerNode = Map.of("id", "n1", "type", "trigger", "data", Map.of("triggerType", "message.received"));
        Map<String, Object> botNode = Map.of("id", "n2", "type", "run_chatbot", "data", Map.of("chatbot_id", chatbot1.getId()));

        Automation autoBot = new Automation();
        autoBot.setWorkspaceId(workspace1.getId());
        autoBot.setName("Run Chatbot Test");
        autoBot.setStatus("active");
        autoBot.setNodes(List.of(triggerNode, botNode));
        autoBot.setEdges(List.of(Map.of("id", "e1", "source", "n1", "target", "n2")));
        autoBot = automationRepository.save(autoBot);

        automationEngine.triggerForContact(autoBot, contact1.getId(), Map.of("message_body", "Hi bot"));

        List<AutomationRun> runs = automationRunRepository.findByAutomationIdOrderByStartedAtDesc(autoBot.getId());
        assertFalse(runs.isEmpty());
        assertEquals("completed", runs.get(0).getStatus());
    }

    @Test
    void test10_WorkspaceIsolation() throws Exception {
        mockMvc.perform(get("/app/ai/knowledge-bases/" + kb1.getUuid())
                        .header("X-Inertia", "true")
                        .with(user(user2Details)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/app/ai/chatbots")
                        .header("X-Inertia", "true")
                        .with(user(user2Details)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.props.chatbots").isEmpty());
    }
}
