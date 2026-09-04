package com.whatsmine.controller;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.CmsPage;
import com.whatsmine.repository.CmsPageRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PublicCmsPageController {

    private final CmsPageRepository cmsPageRepository;

    public PublicCmsPageController(CmsPageRepository cmsPageRepository) {
        this.cmsPageRepository = cmsPageRepository;
    }

    @GetMapping("/p/{slug}")
    public Object show(@PathVariable String slug) {
        CmsPage page = cmsPageRepository.findBySlug(slug).orElse(null);
        if (page == null || !Boolean.TRUE.equals(page.getPublished())) {
            return Inertia.redirect("/");
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("page", page);

        return Inertia.render("Public/Page", props);
    }
}
