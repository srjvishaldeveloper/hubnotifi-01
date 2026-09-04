package com.whatsmine.inertia;

import com.whatsmine.model.User;
import com.whatsmine.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class InertiaProtocolTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InertiaRenderer inertiaRenderer;

    private CustomUserDetails customUserDetails;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setRole("client");
        user.setStatus("active");
        user.setWorkspaceId(1L);
        customUserDetails = new CustomUserDetails(user);
    }

    @Test
    @DisplayName("1. Initial page load (no X-Inertia header) should return HTML bootstrap template with data-page attribute")
    void testInitialPageLoadReturnsHtmlBootstrap() throws Exception {
        mockMvc.perform(get("/app/dashboard").with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/html")))
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    assert html.contains("<div id=\"app\" data-page=\"");
                    assert html.contains("Client/Dashboard");
                    assert html.contains("/app/dashboard");
                });
    }

    @Test
    @DisplayName("2. Inertia AJAX request (X-Inertia: true) should return JSON payload with X-Inertia response header")
    void testInertiaAjaxRequestReturnsJson() throws Exception {
        mockMvc.perform(get("/app/dashboard")
                        .with(user(customUserDetails))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component", equalTo("Client/Dashboard")))
                .andExpect(jsonPath("$.url", equalTo("/app/dashboard")))
                .andExpect(jsonPath("$.version", equalTo(inertiaRenderer.getVersion())))
                .andExpect(jsonPath("$.props.title", equalTo("Workspace Dashboard")))
                .andExpect(jsonPath("$.props.unreadCount", equalTo(5)))
                .andExpect(jsonPath("$.props.flash", notNullValue()))
                .andExpect(jsonPath("$.props.branding.app_name", equalTo("WhatsMine")));
    }

    @Test
    @DisplayName("3. Asset version mismatch on GET should return 409 Conflict with X-Inertia-Location header")
    void testAssetVersionMismatchReturns409() throws Exception {
        mockMvc.perform(get("/app/dashboard")
                        .with(user(customUserDetails))
                        .header("X-Inertia", "true")
                        .header("X-Inertia-Version", "0.0.0-outdated-version"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Inertia-Location", "/app/dashboard"));
    }

    @Test
    @DisplayName("4. Non-GET Inertia redirect should convert HTTP 302 to 303 See Other")
    void testPostRedirectConverts302To303() throws Exception {
        mockMvc.perform(post("/app/settings")
                        .with(user(customUserDetails))
                        .with(csrf())
                        .header("X-Inertia", "true"))
                .andExpect(status().isSeeOther()) // 303 See Other
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(header().string("Location", "/app/dashboard"));
    }

    @Test
    @DisplayName("5. Partial reload request should filter props according to X-Inertia-Partial-Data header")
    void testPartialReloadFiltersProps() throws Exception {
        mockMvc.perform(get("/app/dashboard")
                        .with(user(customUserDetails))
                        .header("X-Inertia", "true")
                        .header("X-Inertia-Partial-Component", "Client/Dashboard")
                        .header("X-Inertia-Partial-Data", "unreadCount"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component", equalTo("Client/Dashboard")))
                .andExpect(jsonPath("$.props.unreadCount", equalTo(5)))
                .andExpect(jsonPath("$.props.title").doesNotExist());
    }

    @Test
    @DisplayName("6. Flash message should be passed in shared props and consumed after initial load")
    void testFlashMessageLifecycle() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. Perform POST request setting flash message
        mockMvc.perform(post("/app/settings")
                        .with(user(customUserDetails))
                        .with(csrf())
                        .session(session)
                        .header("X-Inertia", "true"))
                .andExpect(status().isSeeOther());

        // 2. Perform GET request following redirect with same session
        mockMvc.perform(get("/app/dashboard")
                        .with(user(customUserDetails))
                        .session(session)
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.props.flash.success", equalTo("Settings updated successfully!")));

        // 3. Subsequent GET request should have empty flash message (consumed)
        mockMvc.perform(get("/app/dashboard")
                        .with(user(customUserDetails))
                        .session(session)
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.props.flash.success").doesNotExist());
    }
}
