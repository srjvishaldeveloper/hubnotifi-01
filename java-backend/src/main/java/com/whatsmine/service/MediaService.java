package com.whatsmine.service;

import com.whatsmine.model.Media;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import com.whatsmine.repository.MediaRepository;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Service
public class MediaService {

    private final MediaRepository mediaRepository;
    private final StorageManagerService storageManagerService;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public MediaService(MediaRepository mediaRepository,
                        StorageManagerService storageManagerService,
                        SubscriptionRepository subscriptionRepository,
                        PlanRepository planRepository) {
        this.mediaRepository = mediaRepository;
        this.storageManagerService = storageManagerService;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    public Media store(MultipartFile file, User owner, String collection) throws IOException {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = "";
        int dotPos = originalFilename.lastIndexOf('.');
        if (dotPos > 0) {
            ext = originalFilename.substring(dotPos + 1);
        }

        String resolvedDisk = storageManagerService.diskName();
        String rawPath = "media/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        String path = storageManagerService.prefixedPath(rawPath);

        // Store file locally under storage/app/public/ if public disk.
        // MultipartFile#transferTo(File) resolves a RELATIVE destination against
        // the servlet container's own temp work directory, not this process's
        // working directory — must pass an absolute path or the write silently
        // lands (or fails) somewhere under Tomcat's temp dir instead of storage/.
        Path targetPath = Paths.get("storage/app/public", path).toAbsolutePath();
        Files.createDirectories(targetPath.getParent());
        file.transferTo(targetPath.toFile());

        Media media = new Media();
        media.setMediableType(owner.getClass().getName());
        media.setMediableId(owner.getId());
        media.setDisk(resolvedDisk);
        media.setPath(path);
        media.setFilename(originalFilename);
        media.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        media.setSizeBytes(file.getSize());
        media.setCollection(collection != null ? collection : "default");

        return mediaRepository.save(media);
    }

    public long usedBytes(User owner) {
        Long sum = mediaRepository.sumSizeBytesByMediable(owner.getClass().getName(), owner.getId());
        return sum != null ? sum : 0L;
    }

    public long quotaBytes(User owner) {
        Subscription sub = subscriptionRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(owner.getId(), java.util.List.of("active")).orElse(null);
        if (sub != null) {
            Plan plan = planRepository.findById(sub.getPlanId()).orElse(null);
            if (plan != null && plan.getLimits() != null && plan.getLimits().get("storage_gb") != null) {
                try {
                    long gb = Long.parseLong(plan.getLimits().get("storage_gb").toString());
                    return gb * 1024 * 1024 * 1024L;
                } catch (Exception ignored) {}
            }
        }
        return 1024 * 1024 * 1024L; // Default 1 GB
    }
}
