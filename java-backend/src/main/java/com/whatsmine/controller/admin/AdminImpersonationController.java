package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.User;
import com.whatsmine.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/impersonate")
public class AdminImpersonationController {

    private final UserRepository userRepository;

    public AdminImpersonationController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{userId}")
    public Object start(@PathVariable Long userId, HttpSession session) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            session.setAttribute("impersonated_user_id", user.getId());
            Inertia.flashSuccess(session, "Impersonating " + user.getName());
            return Inertia.redirect("/dashboard");
        }
        return Inertia.redirect("/admin/users");
    }

    @PostMapping("/stop")
    public Object stop(HttpSession session) {
        session.removeAttribute("impersonated_user_id");
        Inertia.flashSuccess(session, "Stopped impersonation.");
        return Inertia.redirect("/admin/users");
    }
}
