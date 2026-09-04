package com.whatsmine.controller.client;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.SupportReply;
import com.whatsmine.model.SupportTicket;
import com.whatsmine.model.User;
import com.whatsmine.repository.SupportReplyRepository;
import com.whatsmine.repository.SupportTicketRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/support")
public class ClientSupportTicketController {

    private final SupportTicketRepository supportTicketRepository;
    private final SupportReplyRepository supportReplyRepository;

    public ClientSupportTicketController(SupportTicketRepository supportTicketRepository, SupportReplyRepository supportReplyRepository) {
        this.supportTicketRepository = supportTicketRepository;
        this.supportReplyRepository = supportReplyRepository;
    }

    private Map<String, Object> ticketToArray(SupportTicket t) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", t.getId());
        map.put("subject", t.getSubject());
        map.put("message", t.getMessage());
        map.put("status", t.getStatus());
        map.put("priority", t.getPriority());
        map.put("created_at", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
        return map;
    }

    @GetMapping
    public Object index(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "1") int page) {
        User user = userDetails.getUser();
        Page<SupportTicket> pageResult = supportTicketRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(page - 1, 15));
        
        List<Map<String, Object>> list = new ArrayList<>();
        for (SupportTicket t : pageResult.getContent()) {
            list.add(ticketToArray(t));
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("tickets", list);
        props.put("total", pageResult.getTotalElements());

        return Inertia.render("client/Support/Index", props);
    }

    @GetMapping("/create")
    public Object create() {
        return Inertia.render("client/Support/Create", Map.of());
    }

    @PostMapping
    public Object store(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestBody Map<String, Object> payload,
                        HttpSession session) {
        User user = userDetails.getUser();

        SupportTicket ticket = new SupportTicket();
        ticket.setUserId(user.getId());
        ticket.setName(user.getName());
        ticket.setEmail(user.getEmail());
        ticket.setSubject((String) payload.get("subject"));
        ticket.setMessage((String) payload.get("message"));
        ticket.setPriority(payload.get("priority") != null ? (String) payload.get("priority") : "medium");
        ticket.setStatus("open");

        supportTicketRepository.save(ticket);

        Inertia.flashSuccess(session, "Support ticket created successfully.");
        return Inertia.redirect("/support");
    }

    @GetMapping("/{id}")
    public Object show(@AuthenticationPrincipal CustomUserDetails userDetails,
                       @PathVariable Long id) {
        User user = userDetails.getUser();
        SupportTicket ticket = supportTicketRepository.findById(id).orElse(null);
        if (ticket == null || !ticket.getUserId().equals(user.getId())) {
            return Inertia.redirect("/support");
        }

        List<SupportReply> replies = supportReplyRepository.findByTicketIdOrderByCreatedAtAsc(id);
        List<Map<String, Object>> replyList = new ArrayList<>();
        for (SupportReply r : replies) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.getId());
            rm.put("author_name", r.getAuthorName());
            rm.put("is_staff", Boolean.TRUE.equals(r.getIsStaff()));
            rm.put("message", r.getMessage());
            rm.put("created_at", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            replyList.add(rm);
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("ticket", ticketToArray(ticket));
        props.put("replies", replyList);

        return Inertia.render("client/Support/Show", props);
    }

    @PostMapping("/{id}/reply")
    public Object reply(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable Long id,
                        @RequestBody Map<String, Object> payload,
                        HttpSession session) {
        User user = userDetails.getUser();
        SupportTicket ticket = supportTicketRepository.findById(id).orElse(null);
        if (ticket == null || !ticket.getUserId().equals(user.getId())) {
            return Inertia.redirect("/support");
        }

        SupportReply reply = new SupportReply();
        reply.setTicketId(id);
        reply.setUserId(user.getId());
        reply.setAuthorName(user.getName());
        reply.setIsStaff(false);
        reply.setMessage((String) payload.get("message"));
        supportReplyRepository.save(reply);

        ticket.setStatus("open");
        supportTicketRepository.save(ticket);

        Inertia.flashSuccess(session, "Reply added successfully.");
        return Inertia.redirect("/support/" + id);
    }
}
