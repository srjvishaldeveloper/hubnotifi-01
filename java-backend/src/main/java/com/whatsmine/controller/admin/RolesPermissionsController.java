package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.Permission;
import com.whatsmine.model.Role;
import com.whatsmine.repository.PermissionRepository;
import com.whatsmine.repository.RoleRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/admin")
public class RolesPermissionsController {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RolesPermissionsController(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @GetMapping("/roles-permissions")
    public InertiaResponse index() {
        List<Role> roles = roleRepository.findAll();
        List<Permission> permissions = permissionRepository.findAll();

        Map<String, Object> props = new HashMap<>();
        props.put("roles", roles);
        props.put("permissions", permissions);

        return Inertia.render("Admin/RolesPermissions/Index", props);
    }

    @PostMapping("/roles")
    public Object storeRole(@RequestBody CreateRoleRequest request, HttpSession session) {
        Role role = new Role();
        role.setName(request.getName());
        role.setKey(request.getKey());
        role.setDescription(request.getDescription());
        role.setIsSystem(false);
        roleRepository.save(role);

        Inertia.flashSuccess(session, "Role created successfully.");
        return Inertia.redirect("/admin/roles-permissions");
    }

    @PutMapping("/roles/{id}")
    public Object updateRole(@PathVariable Long id, @RequestBody CreateRoleRequest request, HttpSession session) {
        Role role = roleRepository.findById(id).orElseThrow();
        role.setName(request.getName());
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        roleRepository.save(role);

        Inertia.flashSuccess(session, "Role updated.");
        return Inertia.redirect("/admin/roles-permissions");
    }

    @DeleteMapping("/roles/{id}")
    public Object destroyRole(@PathVariable Long id, HttpSession session) {
        Role role = roleRepository.findById(id).orElseThrow();
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new IllegalArgumentException("System roles cannot be deleted.");
        }
        roleRepository.delete(role);

        Inertia.flashSuccess(session, "Role deleted.");
        return Inertia.redirect("/admin/roles-permissions");
    }

    public static class CreateRoleRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String key;
        private String description;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
