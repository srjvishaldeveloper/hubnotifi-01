package com.whatsmine.controller.whatsapp;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.inertia.InertiaRenderer;
import com.whatsmine.model.WhatsappWidget;
import com.whatsmine.repository.WhatsappWidgetRepository;
import com.whatsmine.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * CRUD for embeddable "click to WhatsApp" website widgets, plus the public,
 * unauthenticated embed-script endpoint a third-party site's
 * &lt;script src=".../widgets/whatsapp/{key}.js"&gt; tag loads. Porting PHP's
 * WhatsappWidgetController — the script is generated per-request from the
 * widget's live config (domain whitelist + working hours baked in as JS
 * checks), same as PHP, rather than a static asset.
 */
@RestController
public class WhatsAppWidgetController {

    @Autowired
    private InertiaRenderer inertiaRenderer;

    @Autowired
    private WhatsappWidgetRepository widgetRepository;

    private Long getWorkspaceId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getWorkspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userDetails.getWorkspaceId();
    }

    @GetMapping("/app/whatsapp/widget")
    public Object index(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long workspaceId = getWorkspaceId(userDetails);
        List<WhatsappWidget> widgets = widgetRepository.findByWorkspaceIdOrderByIdDesc(workspaceId);
        return inertiaRenderer.render("Whatsapp/Widget/Index", Map.of("widgets", widgets), request);
    }

    @GetMapping("/app/whatsapp/widgets/create")
    public Object create(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        getWorkspaceId(userDetails);
        return inertiaRenderer.render("Whatsapp/Widget/Create", Map.of(), request);
    }

    @GetMapping("/app/whatsapp/widgets/{id}/edit")
    public Object edit(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        Long workspaceId = getWorkspaceId(userDetails);
        WhatsappWidget widget = widgetRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Widget not found."));
        return inertiaRenderer.render("Whatsapp/Widget/Edit", Map.of("widget", widget), request);
    }

    @PostMapping("/app/whatsapp/widgets")
    public Object store(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> body,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        WhatsappWidget widget = new WhatsappWidget();
        widget.setWorkspaceId(workspaceId);
        applyBody(widget, body);

        widgetRepository.save(widget);
        Inertia.flashSuccess(session, "Widget created.");
        return Inertia.redirect("/app/whatsapp/widget");
    }

    @PutMapping("/app/whatsapp/widgets/{id}")
    public Object update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        WhatsappWidget widget = widgetRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Widget not found."));

        applyBody(widget, body);
        widgetRepository.save(widget);
        Inertia.flashSuccess(session, "Widget updated.");
        return Inertia.redirect("/app/whatsapp/widget");
    }

    @DeleteMapping("/app/whatsapp/widgets/{id}")
    public Object destroy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            HttpSession session
    ) {
        Long workspaceId = getWorkspaceId(userDetails);
        WhatsappWidget widget = widgetRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Widget not found."));

        widgetRepository.delete(widget);
        Inertia.flashSuccess(session, "Widget deleted.");
        return Inertia.redirect("/app/whatsapp/widget");
    }

    /** Public, unauthenticated: the embeddable script a merchant's site loads. */
    @GetMapping(value = "/widgets/whatsapp/{key}.js", produces = "application/javascript")
    public ResponseEntity<String> embed(@PathVariable String key) {
        WhatsappWidget widget = widgetRepository.findByWidgetKey(key).orElse(null);
        if (widget == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.valueOf("application/javascript"))
                    .body("// widget not found");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/javascript"))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(buildScript(widget));
    }

    @SuppressWarnings("unchecked")
    private void applyBody(WhatsappWidget widget, Map<String, Object> body) {
        if (body.containsKey("name")) widget.setName(str(body.get("name")));
        if (body.containsKey("phone_number_id")) widget.setPhoneNumberId(str(body.get("phone_number_id")));
        if (body.containsKey("display_phone")) widget.setDisplayPhone(str(body.get("display_phone")));
        if (body.containsKey("prefilled_message")) widget.setPrefilledMessage(str(body.get("prefilled_message")));
        if (body.containsKey("greeting_message")) widget.setGreetingMessage(str(body.get("greeting_message")));
        if (body.containsKey("agent_name")) widget.setAgentName(str(body.getOrDefault("agent_name", "Support")));
        if (body.containsKey("agent_avatar_color")) widget.setAgentAvatarColor(str(body.getOrDefault("agent_avatar_color", "#25D366")));
        if (body.containsKey("button_color")) widget.setButtonColor(str(body.getOrDefault("button_color", "#25D366")));
        if (body.containsKey("position")) widget.setPosition(str(body.getOrDefault("position", "bottom_right")));
        if (body.get("allowed_domains") instanceof List) widget.setAllowedDomains((List<String>) body.get("allowed_domains"));
        if (body.get("working_hours_json") instanceof Map) widget.setWorkingHoursJson((Map<String, Object>) body.get("working_hours_json"));
    }

    private String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private String buildScript(WhatsappWidget widget) {
        String phone = widget.getDisplayPhone() != null ? widget.getDisplayPhone().replaceAll("[^0-9]", "") : "";
        String prefilled = widget.getPrefilledMessage() != null ? widget.getPrefilledMessage() : "";
        String waLink = "https://wa.me/" + phone + "?text=" + URLEncoder.encode(prefilled, StandardCharsets.UTF_8);
        String greeting = jsString(widget.getGreetingMessage());
        String agentName = jsString(widget.getAgentName() != null ? widget.getAgentName() : "Support");
        String buttonColor = widget.getButtonColor() != null ? widget.getButtonColor() : "#25D366";
        String position = "bottom_left".equals(widget.getPosition()) ? "left" : "right";
        String widgetKey = widget.getWidgetKey();
        String allowedDomainsJson = jsArray(widget.getAllowedDomains());
        String workingHoursJson = jsWorkingHours(widget.getWorkingHoursJson());

        return "(function(){\n" +
                "  if (document.getElementById('_wacw_root')) return;\n" +
                "  var allowed = " + allowedDomainsJson + ";\n" +
                "  if (allowed.length) {\n" +
                "    var host = location.hostname.replace(/^www\\./, '');\n" +
                "    if (allowed.indexOf(host) === -1) return;\n" +
                "  }\n" +
                "  var wh = " + workingHoursJson + ";\n" +
                "  if (wh && wh.enabled) {\n" +
                "    try {\n" +
                "      var now = new Date(new Date().toLocaleString('en-US', {timeZone: wh.timezone || 'UTC'}));\n" +
                "      var day = ['sun','mon','tue','wed','thu','fri','sat'][now.getDay()];\n" +
                "      var d = wh.schedule && wh.schedule[day];\n" +
                "      if (!d || !d.enabled) return;\n" +
                "      var mins = now.getHours()*60+now.getMinutes();\n" +
                "      var toMin = function(s){var p=s.split(':');return parseInt(p[0])*60+parseInt(p[1]);};\n" +
                "      if (mins < toMin(d.open) || mins >= toMin(d.close)) return;\n" +
                "    } catch(e) {}\n" +
                "  }\n" +
                "  var style = document.createElement('style');\n" +
                "  style.textContent = '#_wacw_btn{position:fixed;" + position + ":24px;bottom:24px;width:60px;height:60px;border-radius:50%;background:" + buttonColor + ";box-shadow:0 4px 16px rgba(0,0,0,.25);display:flex;align-items:center;justify-content:center;cursor:pointer;z-index:999999;text-decoration:none;}" +
                "#_wacw_btn svg{width:32px;height:32px;fill:#fff;}" +
                "#_wacw_greeting{position:fixed;" + position + ":24px;bottom:96px;max-width:260px;background:#fff;border-radius:12px;box-shadow:0 4px 20px rgba(0,0,0,.2);padding:12px 14px;font:14px sans-serif;color:#222;z-index:999998;}" +
                "#_wacw_greeting b{display:block;margin-bottom:4px;}';\n" +
                "  document.head.appendChild(style);\n" +
                "  var root = document.createElement('div');\n" +
                "  root.id = '_wacw_root';\n" +
                "  var btn = document.createElement('a');\n" +
                "  btn.id = '_wacw_btn';\n" +
                "  btn.href = '" + jsEscape(waLink) + "';\n" +
                "  btn.target = '_blank';\n" +
                "  btn.rel = 'noopener noreferrer';\n" +
                "  btn.innerHTML = '<svg viewBox=\"0 0 24 24\"><path d=\"M12 2C6.48 2 2 6.48 2 12c0 1.85.5 3.58 1.36 5.07L2 22l5.05-1.32A9.94 9.94 0 0 0 12 22c5.52 0 10-4.48 10-10S17.52 2 12 2zm0 18a7.9 7.9 0 0 1-4.03-1.1l-.29-.17-3 .79.8-2.92-.19-.3A7.95 7.95 0 1 1 12 20z\"/></svg>';\n" +
                "  root.appendChild(btn);\n" +
                "  var greetingText = " + greeting + ";\n" +
                "  if (greetingText) {\n" +
                "    var seenKey = '_wacw_seen_" + widgetKey + "';\n" +
                "    if (!sessionStorage.getItem(seenKey)) {\n" +
                "      setTimeout(function(){\n" +
                "        var g = document.createElement('div');\n" +
                "        g.id = '_wacw_greeting';\n" +
                "        g.innerHTML = '<b>' + " + agentName + " + '</b>' + greetingText;\n" +
                "        root.appendChild(g);\n" +
                "        sessionStorage.setItem(seenKey, '1');\n" +
                "      }, 3000);\n" +
                "    }\n" +
                "  }\n" +
                "  document.body.appendChild(root);\n" +
                "})();\n";
    }

    private String jsString(String value) {
        return value != null ? "'" + jsEscape(value) + "'" : "null";
    }

    private String jsEscape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    private String jsArray(List<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(jsString(values.get(i)));
        }
        return sb.append("]").toString();
    }

    @SuppressWarnings("unchecked")
    private String jsWorkingHours(Map<String, Object> workingHours) {
        if (workingHours == null || workingHours.isEmpty()) return "null";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(workingHours);
        } catch (Exception e) {
            return "null";
        }
    }
}
