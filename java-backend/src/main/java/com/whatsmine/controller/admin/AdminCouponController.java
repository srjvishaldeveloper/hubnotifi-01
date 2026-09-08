package com.whatsmine.controller.admin;

import com.whatsmine.inertia.Inertia;
import com.whatsmine.model.Coupon;
import com.whatsmine.repository.CouponRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/coupons")
public class AdminCouponController {

    private final CouponRepository couponRepository;

    public AdminCouponController(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @GetMapping
    public Object index(@RequestParam(value = "page", defaultValue = "1") int page) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page - 1), 25, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Coupon> pageResult = couponRepository.findAll(pageRequest);

        Map<String, Object> paginated = new LinkedHashMap<>();
        paginated.put("data", pageResult.getContent());
        paginated.put("current_page", pageResult.getNumber() + 1);
        paginated.put("last_page", pageResult.getTotalPages());
        paginated.put("total", pageResult.getTotalElements());

        return Inertia.render("Admin/Coupons/Index", Map.of("coupons", paginated));
    }

    @PostMapping
    public Object store(@RequestBody Map<String, Object> payload, HttpSession session) {
        String code = (String) payload.get("code");
        if (code == null || code.isBlank() || couponRepository.existsByCode(code.trim())) {
            Inertia.flashError(session, "Coupon code must be unique.");
            return Inertia.redirect("/admin/coupons");
        }

        Coupon coupon = new Coupon();
        coupon.setCode(code.trim());
        coupon.setKind((String) payload.getOrDefault("kind", "percent"));
        coupon.setAmount(payload.get("amount") != null ? new BigDecimal(payload.get("amount").toString()) : BigDecimal.ZERO);
        coupon.setDuration((String) payload.getOrDefault("duration", "once"));

        if (payload.get("duration_in_months") != null) {
            coupon.setDurationInMonths(Integer.valueOf(payload.get("duration_in_months").toString()));
        }
        if (payload.get("applies_to_plan_ids") instanceof List<?> rawPlanIds) {
            coupon.setAppliesToPlanIds(rawPlanIds.stream().map(String::valueOf).toList());
        }
        if (payload.get("max_redemptions") != null) {
            coupon.setMaxRedemptions(Integer.valueOf(payload.get("max_redemptions").toString()));
        }
        coupon.setEnabled(payload.get("enabled") == null || Boolean.TRUE.equals(payload.get("enabled")));

        couponRepository.save(coupon);

        Inertia.flashSuccess(session, "Coupon created.");
        return Inertia.redirect("/admin/coupons");
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody Map<String, Object> payload, HttpSession session) {
        Coupon coupon = couponRepository.findById(id).orElse(null);
        if (coupon == null) {
            return Inertia.redirect("/admin/coupons");
        }

        String code = (String) payload.get("code");
        if (code != null && couponRepository.existsByCodeAndIdNot(code.trim(), id)) {
            Inertia.flashError(session, "Coupon code must be unique.");
            return Inertia.redirect("/admin/coupons");
        }

        if (code != null) {
            coupon.setCode(code.trim());
        }
        if (payload.get("kind") != null) {
            coupon.setKind((String) payload.get("kind"));
        }
        if (payload.get("amount") != null) {
            coupon.setAmount(new BigDecimal(payload.get("amount").toString()));
        }
        if (payload.get("duration") != null) {
            coupon.setDuration((String) payload.get("duration"));
        }
        if (payload.containsKey("duration_in_months")) {
            coupon.setDurationInMonths(payload.get("duration_in_months") != null ? Integer.valueOf(payload.get("duration_in_months").toString()) : null);
        }
        if (payload.get("applies_to_plan_ids") instanceof List<?> rawPlanIds) {
            coupon.setAppliesToPlanIds(rawPlanIds.stream().map(String::valueOf).toList());
        }
        if (payload.containsKey("max_redemptions")) {
            coupon.setMaxRedemptions(payload.get("max_redemptions") != null ? Integer.valueOf(payload.get("max_redemptions").toString()) : null);
        }
        if (payload.containsKey("enabled")) {
            coupon.setEnabled(Boolean.TRUE.equals(payload.get("enabled")));
        }

        couponRepository.save(coupon);

        Inertia.flashSuccess(session, "Coupon updated.");
        return Inertia.redirect("/admin/coupons");
    }

    @DeleteMapping("/{id}")
    public Object destroy(@PathVariable Long id, HttpSession session) {
        couponRepository.deleteById(id);
        Inertia.flashSuccess(session, "Coupon deleted.");
        return Inertia.redirect("/admin/coupons");
    }
}
