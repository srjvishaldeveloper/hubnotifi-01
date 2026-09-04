package com.whatsmine.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.*;
import com.whatsmine.repository.*;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
public class Phase12EcommerceLeadsParityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private EcommerceStoreRepository storeRepository;

    @Autowired
    private EcommerceProductRepository productRepository;

    @Autowired
    private EcommerceOrderRepository orderRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private LeadScrapeJobRepository scrapeJobRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user1;
    private User user2;
    private CustomUserDetails user1Details;
    private CustomUserDetails user2Details;
    private Workspace workspace1;
    private Workspace workspace2;
    private EcommerceStore store1;
    private EcommerceProduct product1;
    private Contact contact1;
    private EcommerceOrder order1;
    private Lead lead1;

    @BeforeEach
    void setUp() {
        workspace1 = new Workspace();
        workspace1.setName("Ecommerce Workspace 1");
        workspace1 = workspaceRepository.save(workspace1);

        workspace2 = new Workspace();
        workspace2.setName("Ecommerce Workspace 2");
        workspace2 = workspaceRepository.save(workspace2);

        user1 = new User();
        user1.setName("Merchant User 1");
        user1.setEmail("merchant1@example.com");
        user1.setPassword(passwordEncoder.encode("Password123!"));
        user1.setWorkspaceId(workspace1.getId());
        user1.setStatus("active");
        user1 = userRepository.save(user1);
        user1Details = new CustomUserDetails(user1);

        user2 = new User();
        user2.setName("Merchant User 2");
        user2.setEmail("merchant2@example.com");
        user2.setPassword(passwordEncoder.encode("Password123!"));
        user2.setWorkspaceId(workspace2.getId());
        user2.setStatus("active");
        user2 = userRepository.save(user2);
        user2Details = new CustomUserDetails(user2);

        store1 = new EcommerceStore();
        store1.setWorkspaceId(workspace1.getId());
        store1.setPlatform("woocommerce");
        store1.setName("My WooCommerce Store");
        store1.setDomain("https://myshop.example.com");
        store1.setStatus("connected");
        store1.setUuid("store-uuid-12345");
        store1.setWebhookSecret("secret12345");
        store1 = storeRepository.save(store1);

        product1 = new EcommerceProduct();
        product1.setWorkspaceId(workspace1.getId());
        product1.setStoreId(store1.getId());
        product1.setExternalId("prod-101");
        product1.setPlatform("woocommerce");
        product1.setName("Wireless Headphones");
        product1.setSku("WH-101");
        product1.setPrice(new BigDecimal("99.99"));
        product1.setInventoryQuantity(15);
        product1.setStatus("publish");
        product1 = productRepository.save(product1);

        contact1 = new Contact();
        contact1.setWorkspaceId(workspace1.getId());
        contact1.setFirstName("Jane");
        contact1.setLastName("Doe");
        contact1.setEmail("jane.doe@example.com");
        contact1.setPhoneE164("+15559876543");
        contact1 = contactRepository.save(contact1);

        order1 = new EcommerceOrder();
        order1.setWorkspaceId(workspace1.getId());
        order1.setStoreId(store1.getId());
        order1.setContactId(contact1.getId());
        order1.setExternalOrderId("ord-5001");
        order1.setPlatform("woocommerce");
        order1.setNumber("#5001");
        order1.setStatus("processing");
        order1.setFinancialStatus("paid");
        order1.setFulfillmentStatus("unfulfilled");
        order1.setCurrency("USD");
        order1.setTotal(new BigDecimal("99.99"));
        order1.setPlacedAt(LocalDateTime.now());
        order1 = orderRepository.save(order1);

        lead1 = new Lead();
        lead1.setWorkspaceId(workspace1.getId());
        lead1.setName("Target Business Corp");
        lead1.setPhone("+15550001122");
        lead1.setEmail("info@targetbiz.example.com");
        lead1.setWebsite("https://targetbiz.example.com");
        lead1.setAddress("456 Market St");
        lead1.setCity("San Francisco");
        lead1.setCountry("USA");
        lead1.setCategory("Software");
        lead1.setRating(new BigDecimal("4.8"));
        lead1.setReviewCount(42);
        lead1.setGooglePlaceId("ChIJN1t_tDeuEmsRUsoyG83frY4");
        lead1.setWhatsappStatus("unknown");
        lead1.setPushedToContacts(false);
        lead1 = leadRepository.save(lead1);
    }

    @Test
    void test1_StoresIndexInertiaPage() throws Exception {
        mockMvc.perform(get("/app/ecommerce/stores")
                        .with(user(user1Details))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("Ecommerce/Stores/Index"))
                .andExpect(jsonPath("$.props.stores").isArray())
                .andExpect(jsonPath("$.props.platforms").isArray())
                .andExpect(jsonPath("$.props.oauth").isMap());
    }

    @Test
    void test2_StoreConnectAndTest() throws Exception {
        Map<String, Object> body = Map.of(
                "platform", "woocommerce",
                "name", "New Woo Store",
                "domain", "https://example.com",
                "credentials", Map.of("consumer_key", "ck_test", "consumer_secret", "cs_test")
        );

        mockMvc.perform(post("/app/ecommerce/stores")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-Inertia", "true"))
                .andExpect(status().is(303));

        List<EcommerceStore> stores = storeRepository.findByWorkspaceIdOrderByIdDesc(workspace1.getId());
        assertEquals(2, stores.size());

        // Test connection
        mockMvc.perform(post("/app/ecommerce/stores/" + store1.getId() + "/test")
                        .with(user(user1Details))
                        .with(csrf())
                        .header("X-Inertia", "true"))
                .andExpect(status().is(303));
    }

    @Test
    void test3_ProductsIndexAndSearch() throws Exception {
        mockMvc.perform(get("/app/ecommerce/products")
                        .with(user(user1Details))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("Ecommerce/Products/Index"))
                .andExpect(jsonPath("$.props.products.data").isArray())
                .andExpect(jsonPath("$.props.stats.total").value(1));

        mockMvc.perform(get("/app/ecommerce/products/search?q=Headphones")
                        .with(user(user1Details)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Wireless Headphones"));
    }

    @Test
    void test4_OrdersIndexAndShow() throws Exception {
        mockMvc.perform(get("/app/ecommerce/orders")
                        .with(user(user1Details))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("Ecommerce/Orders/Index"))
                .andExpect(jsonPath("$.props.orders.data").isArray());

        mockMvc.perform(get("/app/ecommerce/orders/" + order1.getId())
                        .with(user(user1Details))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("Ecommerce/Orders/Show"))
                .andExpect(jsonPath("$.props.order.number").value("#5001"));
    }

    @Test
    void test5_OrderFulfill() throws Exception {
        Map<String, String> body = Map.of(
                "tracking_number", "TRACK123456",
                "tracking_url", "https://shipping.example.com/track/123456"
        );

        mockMvc.perform(post("/app/ecommerce/orders/" + order1.getId() + "/fulfill")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-Inertia", "true"))
                .andExpect(status().is(303));

        EcommerceOrder updated = orderRepository.findById(order1.getId()).orElseThrow();
        assertEquals("fulfilled", updated.getFulfillmentStatus());
        assertEquals("TRACK123456", updated.getTrackingNumber());
    }

    @Test
    void test6_InboxOrderContextApi() throws Exception {
        mockMvc.perform(get("/app/ecommerce/contacts/" + contact1.getId() + "/orders")
                        .with(user(user1Details)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value("#5001"))
                .andExpect(jsonPath("$[0].total").value(99.99));
    }

    @Test
    void test7_LeadsIndexAndScrapeJob() throws Exception {
        mockMvc.perform(get("/app/leads")
                        .with(user(user1Details))
                        .header("X-Inertia", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("Leads/Index"))
                .andExpect(jsonPath("$.props.leads").exists());

        Map<String, Object> body = Map.of(
                "keyword", "Dentists",
                "location", "New York",
                "radius_meters", 5000
        );

        mockMvc.perform(post("/app/leads/scrape")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-Inertia", "true"))
                .andExpect(status().is(303));

        List<LeadScrapeJob> jobs = scrapeJobRepository.findByWorkspaceIdOrderByIdDesc(workspace1.getId());
        assertFalse(jobs.isEmpty());
        assertEquals("Dentists", jobs.get(0).getKeyword());
    }

    @Test
    void test8_LeadsPushToContactsAndWorkspaceIsolation() throws Exception {
        Map<String, Object> body = Map.of("ids", List.of(lead1.getId()));

        mockMvc.perform(post("/app/leads/push-to-contacts")
                        .with(user(user1Details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("X-Inertia", "true"))
                .andExpect(status().is(303));

        Lead updatedLead = leadRepository.findById(lead1.getId()).orElseThrow();
        assertTrue(updatedLead.isPushedToContacts());

        // Cross-workspace isolation test: User 2 cannot see User 1's lead or order context
        mockMvc.perform(get("/app/ecommerce/contacts/" + contact1.getId() + "/orders")
                        .with(user(user2Details)))
                .andExpect(status().isForbidden());
    }
}
