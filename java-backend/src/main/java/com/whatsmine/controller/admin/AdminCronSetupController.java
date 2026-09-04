package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/cron-setup")
public class AdminCronSetupController {

    @GetMapping
    public Object index() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("cronCommand", "* * * * * cd /var/www/app && php artisan schedule:run >> /dev/null 2>&1");
        props.put("status", "active");

        return Inertia.render("Admin/CronSetup/Index", props);
    }
}
