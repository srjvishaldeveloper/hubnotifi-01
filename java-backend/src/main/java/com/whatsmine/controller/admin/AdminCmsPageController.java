package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.CmsPage;
import com.whatsmine.repository.CmsPageRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/cms-pages")
public class AdminCmsPageController {

    private final CmsPageRepository cmsPageRepository;

    public AdminCmsPageController(CmsPageRepository cmsPageRepository) {
        this.cmsPageRepository = cmsPageRepository;
    }

    @GetMapping
    public Object index() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("pages", cmsPageRepository.findAll());

        return Inertia.render("Admin/CmsPages/Index", props);
    }

    @PostMapping
    public Object store(@RequestBody Map<String, Object> payload, HttpSession session) {
        String title = (String) payload.get("title");
        String slug = (String) payload.get("slug");
        if (slug == null || slug.isBlank()) {
            slug = title != null ? title.toLowerCase().replaceAll("[^a-z0-9]+", "-") : "page-" + System.currentTimeMillis();
        }

        CmsPage page = new CmsPage();
        page.setTitle(title);
        page.setSlug(slug);
        page.setContent((String) payload.get("content"));
        page.setPublished(payload.get("published") == null || Boolean.TRUE.equals(payload.get("published")));
        cmsPageRepository.save(page);

        Inertia.flashSuccess(session, "CMS Page created.");
        return Inertia.redirect("/admin/cms-pages");
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody Map<String, Object> payload, HttpSession session) {
        CmsPage page = cmsPageRepository.findById(id).orElse(null);
        if (page != null) {
            if (payload.get("title") != null) page.setTitle((String) payload.get("title"));
            if (payload.get("slug") != null) page.setSlug((String) payload.get("slug"));
            if (payload.containsKey("content")) page.setContent((String) payload.get("content"));
            if (payload.containsKey("published")) page.setPublished(Boolean.TRUE.equals(payload.get("published")));
            cmsPageRepository.save(page);
        }

        Inertia.flashSuccess(session, "CMS Page updated.");
        return Inertia.redirect("/admin/cms-pages");
    }

    @DeleteMapping("/{id}")
    public Object destroy(@PathVariable Long id, HttpSession session) {
        cmsPageRepository.deleteById(id);
        Inertia.flashSuccess(session, "CMS Page deleted.");
        return Inertia.redirect("/admin/cms-pages");
    }
}
