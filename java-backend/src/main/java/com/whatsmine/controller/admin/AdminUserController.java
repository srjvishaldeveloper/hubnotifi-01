package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.AdminUser;
import com.whatsmine.repository.AdminUserRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/admins")
public class AdminUserController {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public InertiaResponse index() {
        List<AdminUser> admins = adminUserRepository.findAll();
        Map<String, Object> props = new HashMap<>();
        props.put("admins", admins);

        return Inertia.render("Admin/Admins/Index", props);
    }

    @PostMapping
    public Object store(@RequestBody CreateAdminRequest request, HttpSession session) {
        if (adminUserRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Admin email is already in use.");
        }

        AdminUser admin = new AdminUser();
        admin.setName(request.getName());
        admin.setEmail(request.getEmail());
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setStatus("ACTIVE");
        adminUserRepository.save(admin);

        Inertia.flashSuccess(session, "Admin user created.");
        return Inertia.redirect("/admin/admins");
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody UpdateAdminRequest request, HttpSession session) {
        AdminUser admin = adminUserRepository.findById(id).orElseThrow();
        admin.setName(request.getName());
        if (request.getEmail() != null) {
            admin.setEmail(request.getEmail());
        }
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            admin.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        adminUserRepository.save(admin);

        Inertia.flashSuccess(session, "Admin user updated.");
        return Inertia.redirect("/admin/admins");
    }

    @PostMapping("/{id}/toggle-status")
    public Object toggleStatus(@PathVariable Long id, HttpSession session) {
        AdminUser admin = adminUserRepository.findById(id).orElseThrow();
        admin.setStatus("ACTIVE".equalsIgnoreCase(admin.getStatus()) ? "SUSPENDED" : "ACTIVE");
        adminUserRepository.save(admin);

        Inertia.flashSuccess(session, "Admin status updated.");
        return Inertia.redirect("/admin/admins");
    }

    @DeleteMapping("/{id}")
    public Object destroy(@PathVariable Long id, HttpSession session) {
        adminUserRepository.deleteById(id);
        Inertia.flashSuccess(session, "Admin user removed.");
        return Inertia.redirect("/admin/admins");
    }

    public static class CreateAdminRequest {
        @NotBlank
        private String name;
        @NotBlank
        @Email
        private String email;
        @NotBlank
        private String password;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class UpdateAdminRequest {
        @NotBlank
        private String name;
        private String email;
        private String password;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
