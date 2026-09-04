package com.whatsmine.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.*;
import com.whatsmine.repository.*;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.automation.AutomationEngine;
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

import java.util.List;
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
public class AutomationParityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private ContactTagRepository contactTagRepository;
    @Autowired private AutomationRepository automationRepository;
    @Autowired private AutomationRunRepository automationRunRepository;
    @Autowired private AutomationRunLogRepository automationRunLogRepository;
    @Autowired private AutomationEngine automationEngine;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user1;
    private User user2;
    private CustomUserDetails user1Details;
    private CustomUserDetails user2Details;
    private Workspace workspace1;
    private Workspace workspace2;
    private Contact contact1;
    private Automation automation1;

    @BeforeEach
    void setUp() {
        workspace1 = new Workspace();
        workspace1.setName("Automation Workspace 1");
        workspace1 = workspaceRepository.save(workspace1);

        workspace2 = new Workspace();
        workspace2.setName("Automation Workspace 2");
        workspace2 = workspaceRepository.save(workspace2);

        user1 = new User();
        user1.setName("Automation Manager 1");
        user1.setEmail("am1@example.com");
        user1.setPassword(passwordEncoder.encode("Password123!"));
        user1.setWorkspaceId(workspace1.getId());
        user1 = userRepository.save(user1);
        user1Details = new CustomUserDetails(user1);

        user2 = new User();
        user2.setName("Automation Manager 2");
        user2.setEmail("am2@example.com");
        user2.setPassword(passwordEncoder.encode("Password123!"));
        user2.setWorkspaceId(workspace2.getId());
        user2 = userRepository.save(user2);
        user2Details = new CustomUserDetails(user2);

        contact1 = new Contact();
        contact1.setWorkspaceId(workspace1.getId());
        contact1.setFirstName("Alice");
        contact1.setLastName("Smith");
        contact1.setEmail("alice@example.com");
        contact1.setPhoneE164("+15555550199");
        contact1 = contactRepository.save(contact1);

        automation1 = new Automation();
        automation1.setWorkspaceId(workspace1.getId());
        automation1.setName("Welcome Flow");
        automation1.setStatus("active");
        automation1.setTriggerType("message.received");
        automation1.setTriggerToken("token-test-123");

        Map<String, Object> triggerNode = Map.of("id", "node_1", "type", "trigger", "data", Map.of("triggerType", "message.received"));
        Map<String, Object> tagNode = Map.of("id", "node_2", "type", "add_tag", "data", Map.of("tag", "VIP"));
        Map<String, Object> edge1 = Map.of("id", "edge_1", "source", "node_1", "target", "node_2");

        automation1.setNodes(List.of(triggerNode, tagNode));
        automation1.setEdges(List.of(edge1));
        automation1 = automationRepository.save(automation1);
    }

    @Test
    void test1_AutomationIndexRendersCorrectly() throws Exception {
        mockMvc.perform(get("/app/automations")
                        .header("X-Inertia", "true")
                        .with(user(user1Details)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"));
    }

    @Test
    void test2_CreateAutomation() throws Exception {
        Map<String, Object> body = Map.of("name", "New Lead Flow", "trigger_type", "contact.created");

        mockMvc.perform(post("/app/automations")
                        .with(user(user1Details)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isSeeOther())
                .andExpect(header().exists("Location"));

        List<Automation> automations = automationRepository.findByWorkspaceIdOrderByIdDesc(workspace1.getId());
        assertTrue(automations.stream().anyMatch(a -> "New Lead Flow".equals(a.getName())));
    }

    @Test
    void test3_EditPageRendersBuilderResources() throws Exception {
        mockMvc.perform(get("/app/automations/" + automation1.getUuid() + "/edit")
                        .header("X-Inertia", "true")
                        .with(user(user1Details)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"));
    }

    @Test
    void test4_UpdateAutomation() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Updated Flow Name",
                "status", "paused"
        );

        mockMvc.perform(put("/app/automations/" + automation1.getUuid())
                        .with(user(user1Details)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Flow Name"))
                .andExpect(jsonPath("$.status").value("paused"));
    }

    @Test
    void test5_DeleteAutomation() throws Exception {
        mockMvc.perform(delete("/app/automations/" + automation1.getUuid())
                        .with(user(user1Details)).with(csrf()))
                .andExpect(status().isSeeOther());

        assertTrue(automationRepository.findByWorkspaceIdAndUuid(workspace1.getId(), automation1.getUuid()).isEmpty());
    }

    @Test
    void test6_ActivateAutomation() throws Exception {
        automation1.setStatus("draft");
        automationRepository.save(automation1);

        Map<String, Object> body = Map.of("status", "active");

        mockMvc.perform(put("/app/automations/" + automation1.getUuid())
                        .with(user(user1Details)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    void test7_GenerateToken() throws Exception {
        mockMvc.perform(post("/app/automations/" + automation1.getUuid() + "/token")
                        .with(user(user1Details)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trigger_token").exists());
    }

    @Test
    void test8_TestDryRun() throws Exception {
        Map<String, Object> body = Map.of(
                "nodes", automation1.getNodes(),
                "edges", automation1.getEdges()
        );

        mockMvc.perform(post("/app/automations/" + automation1.getUuid() + "/test")
                        .with(user(user1Details)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.steps").isArray());
    }

    @Test
    void test9_ConditionEvaluation() {
        Map<String, Object> condDataEquals = Map.of("field", "contact.first_name", "operator", "equals", "value", "Alice");
        assertTrue(automationEngine.evaluateCondition(condDataEquals, contact1, Map.of()));

        Map<String, Object> condDataContains = Map.of("field", "contact.email", "operator", "contains", "value", "example.com");
        assertTrue(automationEngine.evaluateCondition(condDataContains, contact1, Map.of()));

        Map<String, Object> condDataExists = Map.of("field", "contact.phone", "operator", "exists", "value", "");
        assertTrue(automationEngine.evaluateCondition(condDataExists, contact1, Map.of()));
    }

    @Test
    void test10_TagActionExecution() {
        automationEngine.triggerForContact(automation1, contact1.getId(), Map.of());

        List<AutomationRun> runs = automationRunRepository.findByAutomationIdOrderByStartedAtDesc(automation1.getId());
        assertFalse(runs.isEmpty());
        assertEquals("completed", runs.get(0).getStatus());

        List<AutomationRunLog> logs = automationRunLogRepository.findByRunIdOrderByIdAsc(runs.get(0).getId());
        assertFalse(logs.isEmpty());
        assertTrue(logs.stream().anyMatch(l -> "add_tag".equals(l.getNodeType())));
    }

    @Test
    void test11_WaitNodeExecution() {
        Map<String, Object> triggerNode = Map.of("id", "n1", "type", "trigger", "data", Map.of("triggerType", "message.received"));
        Map<String, Object> waitNode = Map.of("id", "n2", "type", "wait", "data", Map.of("amount", 5, "unit", "minutes"));
        Map<String, Object> tagNode = Map.of("id", "n3", "type", "add_tag", "data", Map.of("tag", "Delayed"));

        Automation autoWait = new Automation();
        autoWait.setWorkspaceId(workspace1.getId());
        autoWait.setName("Wait Test");
        autoWait.setStatus("active");
        autoWait.setNodes(List.of(triggerNode, waitNode, tagNode));
        autoWait.setEdges(List.of(
                Map.of("id", "e1", "source", "n1", "target", "n2"),
                Map.of("id", "e2", "source", "n2", "target", "n3")
        ));
        autoWait = automationRepository.save(autoWait);

        automationEngine.triggerForContact(autoWait, contact1.getId(), Map.of());

        List<AutomationRun> runs = automationRunRepository.findByAutomationIdOrderByStartedAtDesc(autoWait.getId());
        assertFalse(runs.isEmpty());
        assertEquals("waiting", runs.get(0).getStatus());
        assertEquals("n3", runs.get(0).getResumeNodeId());
    }

    @Test
    void test12_WebhookInbound() throws Exception {
        Map<String, Object> payload = Map.of("email", "alice@example.com", "event", "order.created");

        mockMvc.perform(post("/webhooks/automation/" + automation1.getTriggerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void test13_ExecutionLogging() {
        automationEngine.triggerForContact(automation1, contact1.getId(), Map.of("source", "unit_test"));

        List<AutomationRun> runs = automationRunRepository.findByAutomationIdOrderByStartedAtDesc(automation1.getId());
        assertEquals(1, runs.size());
        assertEquals("completed", runs.get(0).getStatus());

        List<AutomationRunLog> logs = automationRunLogRepository.findByRunIdOrderByIdAsc(runs.get(0).getId());
        assertEquals(1, logs.size());
        assertEquals("add_tag", logs.get(0).getNodeType());
        assertEquals("ok", logs.get(0).getResult());
    }

    @Test
    void test14_WorkspaceIsolation() throws Exception {
        mockMvc.perform(get("/app/automations/" + automation1.getUuid() + "/edit")
                        .header("X-Inertia", "true")
                        .with(user(user2Details)))
                .andExpect(status().isNotFound());
    }

    @Test
    void test15_ValidationFailure() throws Exception {
        Map<String, Object> body = Map.of("name", "   ");

        mockMvc.perform(post("/app/automations")
                        .with(user(user1Details)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void test16_ApiListAutomations() throws Exception {
        mockMvc.perform(get("/api/v1/automations").with(user(user1Details)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Welcome Flow"));
    }

    @Test
    void test17_AskQuestionNode() {
        Map<String, Object> triggerNode = Map.of("id", "n1", "type", "trigger", "data", Map.of("triggerType", "message.received"));
        Map<String, Object> askNode = Map.of("id", "n2", "type", "ask_question", "data", Map.of("question", "What is your favorite color?", "variable", "color"));
        Map<String, Object> tagNode = Map.of("id", "n3", "type", "add_tag", "data", Map.of("tag", "Answered"));

        Automation autoAsk = new Automation();
        autoAsk.setWorkspaceId(workspace1.getId());
        autoAsk.setName("Ask Flow");
        autoAsk.setStatus("active");
        autoAsk.setNodes(List.of(triggerNode, askNode, tagNode));
        autoAsk.setEdges(List.of(
                Map.of("id", "e1", "source", "n1", "target", "n2"),
                Map.of("id", "e2", "source", "n2", "target", "n3")
        ));
        autoAsk = automationRepository.save(autoAsk);

        automationEngine.triggerForContact(autoAsk, contact1.getId(), Map.of());

        List<AutomationRun> runs = automationRunRepository.findByAutomationIdOrderByStartedAtDesc(autoAsk.getId());
        assertFalse(runs.isEmpty());
        assertEquals("waiting", runs.get(0).getStatus());

        // Now simulate user reply
        automationEngine.resumeAwaitingReplies(workspace1.getId(), contact1.getId(), "Blue");

        AutomationRun resumedRun = automationRunRepository.findById(runs.get(0).getId()).orElseThrow();
        assertEquals("completed", resumedRun.getStatus());
        assertEquals("Blue", resumedRun.getContext().get("color"));
    }

    @Test
    void test18_AutomationReport() throws Exception {
        mockMvc.perform(get("/app/reports/automations")
                        .header("X-Inertia", "true")
                        .with(user(user1Details)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"));
    }
}
