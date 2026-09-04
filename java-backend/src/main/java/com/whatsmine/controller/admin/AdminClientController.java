package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaResponse;
import com.whatsmine.model.Client;
import com.whatsmine.repository.ClientRepository;
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
@RequestMapping("/admin/clients")
public class AdminClientController {

    private final ClientRepository clientRepository;

    public AdminClientController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @GetMapping
    public InertiaResponse index() {
        List<Client> clients = clientRepository.findAll();
        Map<String, Object> props = new HashMap<>();
        props.put("clients", clients);
        props.put("total", clients.size());

        return Inertia.render("Admin/Clients/Index", props);
    }

    @PostMapping
    public Object store(@RequestBody CreateClientRequest request, HttpSession session) {
        Client client = new Client();
        client.setName(request.getName());
        client.setEmail(request.getEmail());
        client.setStatus(request.getStatus() != null ? request.getStatus() : "active");
        clientRepository.save(client);

        Inertia.flashSuccess(session, "Client organization created successfully.");
        return Inertia.redirect("/admin/clients");
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody UpdateClientRequest request, HttpSession session) {
        Client client = clientRepository.findById(id).orElseThrow();
        client.setName(request.getName());
        if (request.getEmail() != null) {
            client.setEmail(request.getEmail());
        }
        if (request.getStatus() != null) {
            client.setStatus(request.getStatus());
        }
        clientRepository.save(client);

        Inertia.flashSuccess(session, "Client details updated.");
        return Inertia.redirect("/admin/clients");
    }

    @DeleteMapping("/{id}")
    public Object destroy(@PathVariable Long id, HttpSession session) {
        clientRepository.deleteById(id);
        Inertia.flashSuccess(session, "Client deleted.");
        return Inertia.redirect("/admin/clients");
    }

    public static class CreateClientRequest {
        @NotBlank
        private String name;
        private String email;
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class UpdateClientRequest {
        @NotBlank
        private String name;
        private String email;
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
