package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.Media;
import com.whatsmine.model.User;
import com.whatsmine.repository.MediaRepository;
import com.whatsmine.security.CustomUserDetails;
import com.whatsmine.service.MediaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/media")
public class ClientMediaController {

    private final MediaService mediaService;
    private final MediaRepository mediaRepository;

    public ClientMediaController(MediaService mediaService, MediaRepository mediaRepository) {
        this.mediaService = mediaService;
        this.mediaRepository = mediaRepository;
    }

    @GetMapping
    public Object index(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "1") int page) {
        User user = userDetails.getUser();
        long usedBytes = mediaService.usedBytes(user);
        long quotaBytes = mediaService.quotaBytes(user);

        Page<Media> pageResult = mediaRepository.findByMediableTypeAndMediableIdOrderByCreatedAtDesc(user.getClass().getName(), user.getId(), PageRequest.of(page - 1, 24));

        List<Map<String, Object>> files = new ArrayList<>();
        for (Media m : pageResult.getContent()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("filename", m.getFilename());
            map.put("mime_type", m.getMimeType());
            map.put("size_bytes", m.getSizeBytes());
            map.put("url", "/" + m.getPath());
            map.put("collection", m.getCollection());
            map.put("created_at", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            files.add(map);
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("files", files);
        props.put("usedBytes", usedBytes);
        props.put("quotaBytes", quotaBytes);

        return Inertia.render("client/Media/Index", props);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                     @RequestParam("file") MultipartFile file,
                                                     @RequestParam(value = "collection", required = false) String collection) {
        User user = userDetails.getUser();
        long usedBytes = mediaService.usedBytes(user);
        long quotaBytes = mediaService.quotaBytes(user);

        if (usedBytes + file.getSize() > quotaBytes) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "Storage quota exceeded."));
        }

        try {
            Media media = mediaService.store(file, user, collection);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("id", media.getId());
            res.put("filename", media.getFilename());
            res.put("url", "/" + media.getPath());
            res.put("size_bytes", media.getSizeBytes());
            return ResponseEntity.status(HttpStatus.CREATED).body(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> destroy(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                       @PathVariable Long id) {
        User user = userDetails.getUser();
        Media media = mediaRepository.findById(id).orElse(null);
        if (media != null && media.getMediableId().equals(user.getId())) {
            mediaRepository.delete(media);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
