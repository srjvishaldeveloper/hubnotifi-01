package com.whatsmine.service.client;

import com.whatsmine.model.OnboardingStep;
import com.whatsmine.model.User;
import com.whatsmine.repository.AiChatbotRepository;
import com.whatsmine.repository.ChannelAccountRepository;
import com.whatsmine.repository.ContactRepository;
import com.whatsmine.repository.MessageRepository;
import com.whatsmine.repository.OnboardingStepRepository;
import com.whatsmine.repository.SocialAccountRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java port of PHP's OnboardingService — computes the client dashboard's
 * "next step" nudge. Steps already marked complete short-circuit; anything
 * not yet marked complete is checked live against real data and, if newly
 * satisfied, persisted immediately (matching PHP's write-on-detect behavior).
 */
@Service
public class OnboardingProgressService {

    public static final Map<String, String> STEPS = new LinkedHashMap<>();
    static {
        STEPS.put("verify_email", "Verify your email address");
        STEPS.put("choose_plan", "Choose a plan");
        STEPS.put("connect_first_channel", "Connect your first messaging channel");
        STEPS.put("import_first_contacts", "Import or add your first contacts");
        STEPS.put("send_first_message", "Send your first message");
        STEPS.put("train_first_chatbot", "Train an AI chatbot");
        STEPS.put("connect_first_social_account", "Connect a social media account");
    }

    private final OnboardingStepRepository onboardingStepRepository;
    private final EffectiveSubscriptionResolver subscriptionResolver;
    private final ChannelAccountRepository channelAccountRepository;
    private final ContactRepository contactRepository;
    private final MessageRepository messageRepository;
    private final AiChatbotRepository aiChatbotRepository;
    private final SocialAccountRepository socialAccountRepository;

    public OnboardingProgressService(OnboardingStepRepository onboardingStepRepository,
                                      EffectiveSubscriptionResolver subscriptionResolver,
                                      ChannelAccountRepository channelAccountRepository,
                                      ContactRepository contactRepository,
                                      MessageRepository messageRepository,
                                      AiChatbotRepository aiChatbotRepository,
                                      SocialAccountRepository socialAccountRepository) {
        this.onboardingStepRepository = onboardingStepRepository;
        this.subscriptionResolver = subscriptionResolver;
        this.channelAccountRepository = channelAccountRepository;
        this.contactRepository = contactRepository;
        this.messageRepository = messageRepository;
        this.aiChatbotRepository = aiChatbotRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    public Map<String, Object> getProgress(User user) {
        Long workspaceId = user.getWorkspaceId();

        Set<String> manuallyCompleted = onboardingStepRepository.findByUserId(user.getId()).stream()
                .filter(s -> Boolean.TRUE.equals(s.getCompleted()))
                .map(OnboardingStep::getStepKey)
                .collect(java.util.stream.Collectors.toSet());

        List<Map<String, Object>> steps = new java.util.ArrayList<>();
        Map<String, Object> nextStep = null;
        int doneCount = 0;

        for (Map.Entry<String, String> entry : STEPS.entrySet()) {
            String key = entry.getKey();
            boolean completed = manuallyCompleted.contains(key) || isCompleted(user, workspaceId, key);

            if (completed && !manuallyCompleted.contains(key)) {
                persistCompletion(user.getId(), key);
            }

            Map<String, Object> step = new LinkedHashMap<>();
            step.put("key", key);
            step.put("label", entry.getValue());
            step.put("completed", completed);
            steps.add(step);

            if (completed) {
                doneCount++;
            } else if (nextStep == null) {
                nextStep = Map.of("key", key, "label", entry.getValue());
            }
        }

        int total = STEPS.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("steps", steps);
        result.put("percent", total > 0 ? Math.round((doneCount * 100.0) / total) : 0);
        result.put("done", doneCount);
        result.put("total", total);
        result.put("is_complete", doneCount == total);
        result.put("next_step", nextStep);
        return result;
    }

    private boolean isCompleted(User user, Long workspaceId, String step) {
        return switch (step) {
            case "verify_email" -> user.getEmailVerifiedAt() != null;
            case "choose_plan" -> subscriptionResolver.resolve(user) != null;
            case "connect_first_channel" -> workspaceId != null && !channelAccountRepository.findByWorkspaceId(workspaceId).isEmpty();
            case "import_first_contacts" -> workspaceId != null && !contactRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId).isEmpty();
            case "send_first_message" -> workspaceId != null && messageRepository.existsByWorkspaceAndDirection(workspaceId, "out");
            case "train_first_chatbot" -> workspaceId != null && aiChatbotRepository.findByWorkspaceIdOrderByIdDesc(workspaceId).stream().anyMatch(c -> c.isEnabled());
            case "connect_first_social_account" -> workspaceId != null && !socialAccountRepository.findByWorkspaceId(workspaceId).isEmpty();
            default -> false;
        };
    }

    private void persistCompletion(Long userId, String stepKey) {
        OnboardingStep step = onboardingStepRepository.findByUserIdAndStepKey(userId, stepKey)
                .orElseGet(() -> {
                    OnboardingStep s = new OnboardingStep();
                    s.setUserId(userId);
                    s.setStepKey(stepKey);
                    return s;
                });
        step.setCompleted(true);
        step.setCompletedAt(LocalDateTime.now());
        onboardingStepRepository.save(step);
    }
}
