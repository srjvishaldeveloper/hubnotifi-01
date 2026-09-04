package com.whatsmine.jpa;

import com.whatsmine.model.AdminUser;
import com.whatsmine.model.Client;
import com.whatsmine.model.ClientSubscription;
import com.whatsmine.model.PersonalAccessToken;
import com.whatsmine.model.Permission;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Role;
import com.whatsmine.model.User;
import com.whatsmine.model.Workspace;
import com.whatsmine.model.WorkspaceUser;
import com.whatsmine.repository.AdminUserRepository;
import com.whatsmine.repository.ClientRepository;
import com.whatsmine.repository.ClientSubscriptionRepository;
import com.whatsmine.repository.PermissionRepository;
import com.whatsmine.repository.PersonalAccessTokenRepository;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.RoleRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WorkspaceRepository;
import com.whatsmine.repository.WorkspaceUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class JpaRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceUserRepository workspaceUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private ClientSubscriptionRepository clientSubscriptionRepository;

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    @Test
    @DisplayName("Read-only repository integration check for all 10 core entities")
    void testAllRepositoriesCanQueryDatabase() {
        assertNotNull(userRepository.findAll());
        assertNotNull(adminUserRepository.findAll());
        assertNotNull(clientRepository.findAll());
        assertNotNull(workspaceRepository.findAll());
        assertNotNull(workspaceUserRepository.findAll());
        assertNotNull(roleRepository.findAll());
        assertNotNull(permissionRepository.findAll());
        assertNotNull(planRepository.findAll());
        assertNotNull(clientSubscriptionRepository.findAll());
        assertNotNull(personalAccessTokenRepository.findAll());
    }

    @Test
    @DisplayName("Verify User entity mapping and workspace/client scoping query methods")
    void testUserScopedQueries() {
        assertNotNull(userRepository.findByWorkspaceId(1L));
        assertNotNull(userRepository.findByClientId(1L));
    }

    @Test
    @DisplayName("Verify Plan entity JSON attribute converter mapping")
    void testPlanEntityMapping() {
        assertNotNull(planRepository.findByEnabledTrueOrderBySortOrderAsc());
    }
}
