package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.AdminUser;
import com.whatsmine.model.SupportReply;
import com.whatsmine.model.SupportTicket;
import com.whatsmine.repository.SupportReplyRepository;
import com.whatsmine.repository.SupportTicketRepository;
import com.whatsmine.security.AdminUserDetails;
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
@RequestMapping("/admin/support")
public class AdminSupportTicketController {

    private final SupportTicketRepository supportTicketRepository;
    private final SupportReplyRepository supportReplyRepository;

    public AdminSupportTicketController(SupportTicketRepository supportTicketRepository, SupportReplyRepository supportReplyRepository) {
        this.supportTicketRepository = supportTicketRepository;
        this.supportReplyRepository = supportReplyRepository;
    }

    private Map<String, Object> ticketToArray(SupportTicket t) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", t.getId());
        map.put("user_id", t.getUserId());
        map.put("name", t.getName());
        map.put("email", t.getEmail());
        map.put("subject", t.getSubject());
        map.put("message", t.getMessage());
        map.put("status", t.getStatus());
        map.put("priority", t.getPriority());
        map.put("created_at", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
        return map;
    }

    @GetMapping
    public Object index(@RequestParam(defaultValue = "1") int page) {
        Page<SupportTicket> pageResult = supportTicketRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page - 1, 20));

        List<Map<String, Object>> list = new ArrayList<>();
        for (SupportTicket t : pageResult.getContent()) {
            list.add(ticketToArray(t));
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("tickets", list);
        props.put("total", pageResult.getTotalElements());

        return Inertia.render("Admin/Support/Index", props);
    }

    @GetMapping("/{id}")
    public Object show(@PathVariable Long id) {
        SupportTicket ticket = supportTicketRepository.findById(id).orElse(null);
        if (ticket == null) {
            return Inertia.redirect("/admin/support");
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

        return Inertia.render("Admin/Support/Show", props);
    }

    @PostMapping("/{id}/reply")
    public Object reply(@AuthenticationPrincipal AdminUserDetails adminDetails,
                        @PathVariable Long id,
                        @RequestBody Map<String, Object> payload,
                        HttpSession session) {
        SupportTicket ticket = supportTicketRepository.findById(id).orElse(null);
        if (ticket == null) {
            return Inertia.redirect("/admin/support");
        }

        AdminUser admin = adminDetails.getAdminUser();

        SupportReply reply = new SupportReply();
        reply.setTicketId(id);
        reply.setUserId(admin.getId());
        reply.setAuthorName(admin.getName() != null ? admin.getName() : "Support Agent");
        reply.setIsStaff(true);
        reply.setMessage((String) payload.get("message"));
        supportReplyRepository.save(reply);

        ticket.setStatus("replied");
        supportTicketRepository.save(ticket);

        Inertia.flashSuccess(session, "Reply posted successfully.");
        return Inertia.redirect("/admin/support/" + id);
    }

    @PostMapping("/{id}/status")
    public Object updateStatus(@PathVariable Long id,
                               @RequestBody Map<String, Object> payload,
                               HttpSession session) {
        SupportTicket ticket = supportTicketRepository.findById(id).orElse(null);
        if (ticket == null) {
            return Inertia.redirect("/admin/support");
        }

        if (payload.get("status") != null) {
            ticket.setStatus((String) payload.get("status"));
            supportTicketRepository.save(ticket);
        }

        Inertia.flashSuccess(session, "Ticket status updated.");
        return Inertia.redirect("/admin/support/" + id);
    }
}
