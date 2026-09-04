package com.whatsmine.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.*;
import com.whatsmine.repository.*;
import com.whatsmine.security.AdminUserDetails;
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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class Phase13BillingParityIntegrationTest {

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
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ClientSubscriptionRepository clientSubscriptionRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PaymentGatewayConfigRepository gatewayConfigRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private TaxRateRepository taxRateRepository;

    @Autowired
    private BillingEventRepository billingEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private CustomUserDetails userDetails;

    private AdminUser adminUser;
    private AdminUserDetails adminUserDetails;

    private Plan testPlan;

    @BeforeEach
    public void setup() {
        Workspace workspace = new Workspace();
        workspace.setName("Billing Workspace");
        workspace = workspaceRepository.save(workspace);

        testUser = new User();
        testUser.setName("Billing User");
        testUser.setEmail("billinguser_" + System.currentTimeMillis() + "@example.com");
        testUser.setPassword(passwordEncoder.encode("password"));
        testUser.setRole("client");
        testUser.setWorkspaceId(workspace.getId());
        testUser = userRepository.save(testUser);

        userDetails = new CustomUserDetails(testUser);

        adminUser = new AdminUser();
        adminUser.setName("Admin Billing User");
        adminUser.setEmail("adminbilling_" + System.currentTimeMillis() + "@example.com");
        adminUser.setPassword(passwordEncoder.encode("password"));
        adminUser.setStatus("ACTIVE");
        adminUser = adminUserRepository.save(adminUser);

        adminUserDetails = new AdminUserDetails(adminUser);

        testPlan = new Plan();
        testPlan.setName("Pro Plan");
        testPlan.setSlug("pro-plan-" + System.currentTimeMillis());
        testPlan.setDescription("Pro plan description");
        testPlan.setPriceCents(2900L);
        testPlan.setMonthlyPriceCents(2900L);
        testPlan.setYearlyPriceCents(29000L);
        testPlan.setCurrencyCode("USD");
        testPlan.setEnabled(true);
        testPlan.setSortOrder(1);
        testPlan = planRepository.save(testPlan);
    }

    @Test
    public void testClientSubscriptionShowInertiaResponse() throws Exception {
        Subscription sub = new Subscription();
        sub.setUserId(testUser.getId());
        sub.setPlanId(testPlan.getId());
        sub.setStatus("active");
        sub.setBillingCycle("month");
        sub.setGateway("stripe");
        sub.setStartsAt(LocalDateTime.now());
        subscriptionRepository.save(sub);

        mockMvc.perform(get("/subscription")
                        .header("X-Inertia", "true")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("client/Subscription/Show"))
                .andExpect(jsonPath("$.props.subscription.plan.name").value("Pro Plan"))
                .andExpect(jsonPath("$.props.canCancel").value(true))
                .andExpect(jsonPath("$.props.canUpgrade").value(true));
    }

    @Test
    public void testPricingPageInertiaResponse() throws Exception {
        mockMvc.perform(get("/pricing")
                        .header("X-Inertia", "true")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("client/Pricing"))
                .andExpect(jsonPath("$.props.plans[0].name").value("Pro Plan"))
                .andExpect(jsonPath("$.props.is_authenticated").value(true));
    }

    @Test
    public void testClientBillingIndexInertiaResponse() throws Exception {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setUserId(testUser.getId());
        tx.setGateway("stripe");
        tx.setAmountCents(2900);
        tx.setCurrencyCode("USD");
        tx.setStatus("paid");
        paymentTransactionRepository.save(tx);

        mockMvc.perform(get("/billing")
                        .header("X-Inertia", "true")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Inertia", "true"))
                .andExpect(jsonPath("$.component").value("client/Billing/Index"))
                .andExpect(jsonPath("$.props.transactions.data[0].amount_cents").value(2900));
    }

    @Test
    public void testCheckoutStoreHostedUrl() throws Exception {
        Map<String, Object> body = Map.of(
                "plan_id", testPlan.getId(),
                "billing_cycle", "month",
                "gateway", "stripe"
        );

        mockMvc.perform(post("/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().is(409))
                .andExpect(header().string("X-Inertia-Location", containsString("/billing")));
    }

    @Test
    public void testCouponCheckEndpoint() throws Exception {
        Coupon coupon = new Coupon();
        coupon.setCode("SAVE50");
        coupon.setKind("percent");
        coupon.setAmount(new BigDecimal("50.00"));
        coupon.setDuration("once");
        coupon.setEnabled(true);
        couponRepository.save(coupon);

        mockMvc.perform(post("/coupon/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "SAVE50")))
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.kind").value("percent"));

        mockMvc.perform(post("/coupon/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "INVALID")))
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    public void testClientSubscriptionChangePlanAndDestroy() throws Exception {
        Subscription sub = new Subscription();
        sub.setUserId(testUser.getId());
        sub.setPlanId(testPlan.getId());
        sub.setStatus("active");
        sub.setBillingCycle("month");
        sub.setGateway("stripe");
        sub.setStartsAt(LocalDateTime.now());
        sub = subscriptionRepository.save(sub);

        Plan newPlan = new Plan();
        newPlan.setName("Enterprise Plan");
        newPlan.setSlug("enterprise-" + System.currentTimeMillis());
        newPlan.setPriceCents(9900L);
        newPlan.setMonthlyPriceCents(9900L);
        newPlan.setCurrencyCode("USD");
        newPlan.setEnabled(true);
        newPlan = planRepository.save(newPlan);

        mockMvc.perform(post("/subscription/change-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("plan_id", newPlan.getId(), "billing_cycle", "month")))
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/subscription"));

        Subscription updated = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertEquals(newPlan.getId(), updated.getPlanId());

        mockMvc.perform(delete("/subscription")
                        .with(csrf())
                        .with(user(userDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/subscription"));

        Subscription cancelled = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertEquals("canceled", cancelled.getStatus());
    }

    @Test
    public void testInvoiceDownload() throws Exception {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setUserId(testUser.getId());
        tx.setGateway("stripe");
        tx.setAmountCents(2900);
        tx.setCurrencyCode("USD");
        tx.setStatus("paid");
        tx = paymentTransactionRepository.save(tx);

        mockMvc.perform(get("/subscription/invoice/" + tx.getId())
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    public void testAdminPlansCrud() throws Exception {
        mockMvc.perform(get("/admin/plans")
                        .header("X-Inertia", "true")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Admin/Plans/Index"));

        Map<String, Object> createBody = Map.of(
                "name", "Basic Plan",
                "slug", "basic-plan-" + System.currentTimeMillis(),
                "monthly_price_cents", 1500,
                "currency_code", "USD",
                "enabled", true
        );

        mockMvc.perform(post("/admin/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody))
                        .with(csrf())
                        .with(user(adminUserDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/admin/plans"));

        Plan basic = planRepository.findBySlug((String) createBody.get("slug")).orElseThrow();
        assertEquals("Basic Plan", basic.getName());

        mockMvc.perform(put("/admin/plans/" + basic.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Basic Plan Updated")))
                        .with(csrf())
                        .with(user(adminUserDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/admin/plans"));

        Plan updated = planRepository.findById(basic.getId()).orElseThrow();
        assertEquals("Basic Plan Updated", updated.getName());
    }

    @Test
    public void testAdminSubscriptionsAndExport() throws Exception {
        mockMvc.perform(get("/admin/subscriptions")
                        .header("X-Inertia", "true")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Admin/Subscriptions/Index"));

        mockMvc.perform(get("/admin/subscriptions/user-search?q=Billing")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].name").value("Billing User"));

        mockMvc.perform(get("/admin/subscriptions/export")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"));
    }

    @Test
    public void testAdminPaymentsAndGateways() throws Exception {
        mockMvc.perform(get("/admin/payments")
                        .header("X-Inertia", "true")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Admin/Payments/Index"));

        mockMvc.perform(get("/admin/payment-gateways")
                        .header("X-Inertia", "true")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Admin/PaymentGateways/Index"));

        mockMvc.perform(get("/admin/payment-gateways/stripe")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateway").value("stripe"));
    }

    @Test
    public void testAdminCouponsCrud() throws Exception {
        mockMvc.perform(get("/admin/coupons")
                        .header("X-Inertia", "true")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Admin/Coupons/Index"));

        Map<String, Object> createBody = Map.of(
                "code", "DISCOUNT20",
                "kind", "percent",
                "amount", 20.00,
                "duration", "once",
                "enabled", true
        );

        mockMvc.perform(post("/admin/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody))
                        .with(csrf())
                        .with(user(adminUserDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/admin/coupons"));

        Coupon coupon = couponRepository.findByCode("DISCOUNT20").orElseThrow();
        assertEquals("percent", coupon.getKind());
    }

    @Test
    public void testAdminTaxRatesCrud() throws Exception {
        mockMvc.perform(get("/admin/tax-rates")
                        .header("X-Inertia", "true")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("Admin/TaxRates/Index"));

        Map<String, Object> createBody = Map.of(
                "name", "VAT",
                "country", "US",
                "percentage", 10.00,
                "enabled", true
        );

        mockMvc.perform(post("/admin/tax-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody))
                        .with(csrf())
                        .with(user(adminUserDetails)))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", "/admin/tax-rates"));

        List<TaxRate> rates = taxRateRepository.findAll();
        assertFalse(rates.isEmpty());
    }

    @Test
    public void testWebhookHandlingAndIdempotency() throws Exception {
        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Event-Id", "evt_test_12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));

        assertTrue(billingEventRepository.existsByEventId("evt_test_12345"));

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Event-Id", "evt_test_12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("already_processed"));
    }
}
