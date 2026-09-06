package com.whatsmine.queue.handler;

import com.whatsmine.model.SocialPost;
import com.whatsmine.queue.JobHandler;
import com.whatsmine.queue.QueueDispatcher;
import com.whatsmine.repository.SocialPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/** Handles DispatchScheduledPostsJob — finds due scheduled posts and dispatches one PublishSocialPostJob each. */
@Component
public class DispatchScheduledPostsJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(DispatchScheduledPostsJobHandler.class);

    private final SocialPostRepository postRepository;
    private final QueueDispatcher queueDispatcher;

    public DispatchScheduledPostsJobHandler(SocialPostRepository postRepository, QueueDispatcher queueDispatcher) {
        this.postRepository = postRepository;
        this.queueDispatcher = queueDispatcher;
    }

    @Override
    public String getJobType() {
        return "DispatchScheduledPostsJob";
    }

    @Override
    public void handle(Map<String, Object> data) throws Exception {
        java.util.List<SocialPost> due = postRepository.findByStatusAndScheduledAtLessThanEqual("scheduled", LocalDateTime.now());
        for (SocialPost post : due) {
            post.setStatus("publishing");
            postRepository.save(post);
            queueDispatcher.dispatch("social", "PublishSocialPostJob", Map.of("postId", post.getId()));
        }
        if (!due.isEmpty()) {
            log.info("DispatchScheduledPostsJob: dispatched {} due post(s).", due.size());
        }
    }

    @Override
    public int getMaxTries() {
        return 1;
    }
}
