package com.whatsmine.config;

import com.whatsmine.model.AdminUser;
import com.whatsmine.model.Client;
import com.whatsmine.model.Contact;
import com.whatsmine.model.Plan;
import com.whatsmine.model.User;
import com.whatsmine.model.Workspace;
import com.whatsmine.repository.AdminUserRepository;
import com.whatsmine.repository.ClientRepository;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AdminUserRepository adminUserRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PlanRepository planRepository;
    private final ContactRepository contactRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
            AdminUserRepository adminUserRepository,
            ClientRepository clientRepository,
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            PlanRepository planRepository,
            ContactRepository contactRepository,
            PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.planRepository = planRepository;
        this.contactRepository = contactRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedAdminUser();
        seedClientAndUser();
        seedPlans();
    }

    private void seedAdminUser() {
        String adminEmail = "admin@example.com";
        if (!adminUserRepository.existsByEmail(adminEmail)) {
            AdminUser admin = new AdminUser();
            admin.setName("Super Admin");
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode("password"));
            admin.setStatus("ACTIVE");
            adminUserRepository.save(admin);
            log.info("[DataSeeder] Default Admin User created: {} / password", adminEmail);
        }
    }

    private void seedClientAndUser() {
        String clientEmail = "client@example.com";
        if (!userRepository.existsByEmail(clientEmail)) {
            Client client = new Client();
            client.setName("SpaGreen Wellness");
            client.setEmail(clientEmail);
            client.setStatus("active");
            client.setBaseCurrency("USD");
            client.setCurrencySymbol("$");
            client.setCurrencyPosition("before");
            client = clientRepository.save(client);

            Workspace workspace = new Workspace();
            workspace.setName("Main Workspace");
            workspace.setClientId(client.getId());
            workspace.setDefaultLocale("en");
            workspace.setCurrencyCode("USD");
            workspace = workspaceRepository.save(workspace);

            User user = new User();
            user.setName("Client User");
            user.setEmail(clientEmail);
            user.setPassword(passwordEncoder.encode("password"));
            user.setRole("client");
            user.setStatus("active");
            user.setClientRole("ADMINISTRATOR");
            user.setClientId(client.getId());
            user.setWorkspaceId(workspace.getId());
            user.setEmailVerifiedAt(LocalDateTime.now());
            user = userRepository.save(user);

            workspace.setOwnerId(user.getId());
            workspaceRepository.save(workspace);

            // Seed initial contact
            Contact contact = new Contact();
            contact.setWorkspaceId(workspace.getId());
            contact.setFirstName("Demo");
            contact.setLastName("Contact");
            contact.setPhoneE164("+15555550100");
            contact.setOptInWhatsapp(true);
            contact.setSource("seed");
            contactRepository.save(contact);

            log.info("[DataSeeder] Default Client User created: {} / password", clientEmail);
        }
    }

    private void seedPlans() {
        if (planRepository.count() == 0) {
            Plan proPlan = new Plan();
            proPlan.setName("Pro Plan");
            proPlan.setSlug("pro");
            proPlan.setDescription("For growing businesses that need automation and broadcasting.");
            proPlan.setPriceCents(2900L);
            proPlan.setMonthlyPriceCents(2900L);
            proPlan.setYearlyPriceCents(27840L);
            proPlan.setCurrencyCode("USD");
            proPlan.setInterval("month");
            proPlan.setTrialDays(14);
            proPlan.setPopular(true);
            proPlan.setSortOrder(1);
            proPlan.setEnabled(true);
            Map<String, Object> proFeatures = new LinkedHashMap<>();
            proFeatures.put("Up to 5,000 contacts", true);
            proFeatures.put("Unlimited broadcast campaigns", true);
            proFeatures.put("WhatsApp, SMS & email channels", true);
            proFeatures.put("Automation workflows", true);
            proFeatures.put("5 team members", true);
            proFeatures.put("Email support", true);
            proPlan.setFeatures(proFeatures);
            Map<String, Object> proLimits = new LinkedHashMap<>();
            proLimits.put("contacts", 5000);
            proLimits.put("team_members", 5);
            proLimits.put("storage_gb", 10);
            proPlan.setLimits(proLimits);
            planRepository.save(proPlan);

            Plan enterprisePlan = new Plan();
            enterprisePlan.setName("Enterprise Plan");
            enterprisePlan.setSlug("enterprise");
            enterprisePlan.setDescription("For high-volume teams that need white-label branding and priority support.");
            enterprisePlan.setPriceCents(9900L);
            enterprisePlan.setMonthlyPriceCents(9900L);
            enterprisePlan.setYearlyPriceCents(95040L);
            enterprisePlan.setCurrencyCode("USD");
            enterprisePlan.setInterval("month");
            enterprisePlan.setTrialDays(14);
            enterprisePlan.setFeatured(true);
            enterprisePlan.setWhiteLabelEnabled(true);
            enterprisePlan.setSortOrder(2);
            enterprisePlan.setEnabled(true);
            Map<String, Object> enterpriseFeatures = new LinkedHashMap<>();
            enterpriseFeatures.put("Unlimited contacts", true);
            enterpriseFeatures.put("Unlimited broadcast campaigns", true);
            enterpriseFeatures.put("All messaging channels", true);
            enterpriseFeatures.put("Advanced automation & AI replies", true);
            enterpriseFeatures.put("Unlimited team members", true);
            enterpriseFeatures.put("White-label branding", true);
            enterpriseFeatures.put("Priority 24/7 support", true);
            enterpriseFeatures.put("Dedicated account manager", true);
            enterprisePlan.setFeatures(enterpriseFeatures);
            Map<String, Object> enterpriseLimits = new LinkedHashMap<>();
            enterpriseLimits.put("contacts", null);
            enterpriseLimits.put("team_members", null);
            enterpriseLimits.put("storage_gb", 100);
            enterprisePlan.setLimits(enterpriseLimits);
            planRepository.save(enterprisePlan);

            log.info("[DataSeeder] Default Plans created");
        }
    }
}
