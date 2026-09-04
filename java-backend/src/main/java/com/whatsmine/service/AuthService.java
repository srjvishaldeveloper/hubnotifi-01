package com.whatsmine.service;

import com.whatsmine.dto.LoginRequest;
import com.whatsmine.dto.RegisterRequest;
import com.whatsmine.model.Client;
import com.whatsmine.model.User;
import com.whatsmine.model.Workspace;
import com.whatsmine.model.WorkspaceUser;
import com.whatsmine.repository.ClientRepository;
import com.whatsmine.repository.UserRepository;
import com.whatsmine.repository.WorkspaceRepository;
import com.whatsmine.repository.WorkspaceUserRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager clientAuthenticationManager;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceUserRepository workspaceUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            @Qualifier("clientAuthenticationManager") AuthenticationManager clientAuthenticationManager,
            UserRepository userRepository,
            ClientRepository clientRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceUserRepository workspaceUserRepository,
            PasswordEncoder passwordEncoder) {
        this.clientAuthenticationManager = clientAuthenticationManager;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceUserRepository = workspaceUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean login(LoginRequest request, HttpServletRequest httpRequest) {
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword());

        Authentication auth = clientAuthenticationManager.authenticate(token);
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        User user = userDetails.getUser();

        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("Account is disabled or inactive");
        }

        // Check if 2FA is enabled for user
        if (user.getTwoFactorSecret() != null && !user.getTwoFactorSecret().trim().isEmpty() && user.getTwoFactorConfirmedAt() != null) {
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("2fa_user_id", user.getId());
            return false; // Requires 2FA verification
        }

        // Establish session
        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());

        return true; // Fully authenticated
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email address is already registered");
        }

        // 1. Create Client Organization
        Client client = new Client();
        client.setName(request.getName());
        client.setEmail(request.getEmail());
        client.setStatus("active");
        client = clientRepository.save(client);

        // 2. Create User
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("client");
        user.setStatus("active");
        user.setClientId(client.getId());
        user.setClientRole("ADMINISTRATOR");
        user = userRepository.save(user);

        // 3. Create Default Workspace
        Workspace workspace = new Workspace();
        workspace.setName(request.getName() + "'s Workspace");
        workspace.setOwnerId(user.getId());
        workspace.setClientId(client.getId());
        workspace = workspaceRepository.save(workspace);

        // 4. Update User with Workspace ID
        user.setWorkspaceId(workspace.getId());
        user = userRepository.save(user);

        // 5. Create WorkspaceUser pivot
        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceId(workspace.getId());
        workspaceUser.setUserId(user.getId());
        workspaceUser.setRole("owner");
        workspaceUserRepository.save(workspaceUser);

        return user;
    }

    public void logout(HttpServletRequest httpRequest) {
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
