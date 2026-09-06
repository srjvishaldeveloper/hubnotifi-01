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
            proPlan.setPriceCents(2900L);
            proPlan.setCurrencyCode("USD");
            proPlan.setInterval("month");
            planRepository.save(proPlan);

            Plan enterprisePlan = new Plan();
            enterprisePlan.setName("Enterprise Plan");
            enterprisePlan.setSlug("enterprise");
            enterprisePlan.setPriceCents(9900L);
            enterprisePlan.setCurrencyCode("USD");
            enterprisePlan.setInterval("month");
            planRepository.save(enterprisePlan);

            log.info("[DataSeeder] Default Plans created");
        }
    }
}
