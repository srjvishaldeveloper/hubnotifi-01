package com.whatsmine.service.whatsapp;

import com.whatsmine.model.ChannelAccount;
import com.whatsmine.model.Conversation;
import com.whatsmine.model.Message;
import com.whatsmine.model.WhatsappAutoReply;
import com.whatsmine.repository.MessageRepository;
import com.whatsmine.repository.WhatsappAutoReplyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Rule-based (non-AI) WhatsApp auto-reply matching, porting PHP's
 * WhatsappAutoReply::matchesMessage() + AutoReplyListener::findMatchingAutoReply().
 * Selection: rules for this workspace (global, channelAccountId==null, or this
 * specific account), enabled, ordered by priority ascending; first rule whose
 * trigger condition matches wins.
 */
@Service
public class WhatsappAutoReplyMatcher {

    private static final Logger log = LoggerFactory.getLogger(WhatsappAutoReplyMatcher.class);

    private final WhatsappAutoReplyRepository autoReplyRepository;
    private final MessageRepository messageRepository;

    public WhatsappAutoReplyMatcher(WhatsappAutoReplyRepository autoReplyRepository, MessageRepository messageRepository) {
        this.autoReplyRepository = autoReplyRepository;
        this.messageRepository = messageRepository;
    }

    public Optional<WhatsappAutoReply> findMatch(ChannelAccount channelAccount, Conversation conversation, Message inboundMessage) {
        List<WhatsappAutoReply> rules = autoReplyRepository.findByWorkspaceIdAndEnabledTrueOrderByPriorityAsc(channelAccount.getWorkspaceId());

        String body = inboundMessage.getBody() != null ? inboundMessage.getBody() : "";

        for (WhatsappAutoReply rule : rules) {
            if (rule.getChannelAccountId() != null && !rule.getChannelAccountId().equals(channelAccount.getId())) {
                continue;
            }

            boolean matched = switch (rule.getTriggerType()) {
                case "keyword" -> matchesKeyword(rule, body);
                case "welcome" -> messageRepository.countByConversationId(conversation.getId()) == 1;
                case "away", "out_of_hours" -> isOutsideSchedule(rule.getScheduleJson());
                default -> false;
            };

            if (matched) {
                return Optional.of(rule);
            }
        }

        return Optional.empty();
    }

    private boolean matchesKeyword(WhatsappAutoReply rule, String body) {
        if (rule.getKeywords() == null || rule.getKeywords().isEmpty()) {
            return false;
        }
        String normalizedBody = body.toLowerCase().trim();

        for (String keyword : rule.getKeywords()) {
            if (keyword == null || keyword.isBlank()) continue;
            String k = keyword.toLowerCase().trim();

            boolean hit = switch (rule.getMatchMode()) {
                case "exact" -> normalizedBody.equals(k);
                case "regex" -> safeRegexMatch(keyword, body);
                default -> normalizedBody.contains(k); // contains
            };
            if (hit) return true;
        }
        return false;
    }

    private boolean safeRegexMatch(String pattern, String body) {
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(body).find();
        } catch (PatternSyntaxException e) {
            log.warn("Invalid auto-reply regex '{}': {}", pattern, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isOutsideSchedule(Map<String, Object> schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return false;
        }

        String tz = schedule.get("timezone") != null ? String.valueOf(schedule.get("timezone")) : "UTC";
        ZonedDateTime now;
        try {
            now = ZonedDateTime.now(ZoneId.of(tz));
        } catch (Exception e) {
            now = ZonedDateTime.now(ZoneId.of("UTC"));
        }

        Object daysObj = schedule.get("days");
        if (daysObj instanceof List<?> days && !days.isEmpty()) {
            int isoDay = now.getDayOfWeek().getValue(); // 1=Mon..7=Sun, matches PHP's ISO weekday
            boolean dayIncluded = days.stream().anyMatch(d -> {
                try {
                    return Integer.parseInt(String.valueOf(d)) == isoDay;
                } catch (NumberFormatException ex) {
                    return false;
                }
            });
            if (!dayIncluded) return true;
        }

        String start = schedule.get("start") != null ? String.valueOf(schedule.get("start")) : null;
        String end = schedule.get("end") != null ? String.valueOf(schedule.get("end")) : null;
        if (start != null && end != null) {
            try {
                LocalTime startTime = LocalTime.parse(start);
                LocalTime endTime = LocalTime.parse(end);
                LocalTime nowTime = now.toLocalTime();
                boolean withinWindow = !nowTime.isBefore(startTime) && nowTime.isBefore(endTime);
                return !withinWindow;
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }
}
