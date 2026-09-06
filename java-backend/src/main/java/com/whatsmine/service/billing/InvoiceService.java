package com.whatsmine.service.billing;

import com.whatsmine.model.Coupon;
import com.whatsmine.model.PaymentTransaction;
import com.whatsmine.model.Plan;
import com.whatsmine.model.Subscription;
import com.whatsmine.model.User;
import com.whatsmine.repository.CouponRepository;
import com.whatsmine.repository.PaymentTransactionRepository;
import com.whatsmine.repository.PlanRepository;
import com.whatsmine.repository.SubscriptionRepository;
import com.whatsmine.repository.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Ports PHP's Services\Billing\InvoiceService: a real, rendered PDF invoice
 * (previously a hand-built fake PDF byte string with no actual content
 * stream). PHP renders resources/views/pdf/invoice.blade.php via DomPDF;
 * Java draws the same information directly with Apache PDFBox (already a
 * dependency for AI document ingestion) rather than adding an HTML-to-PDF
 * library for a single-page document.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final DateTimeFormatter ISSUED_AT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final CouponRepository couponRepository;

    @Value("${app.name:Hub Notification}")
    private String appName;

    public InvoiceService(PaymentTransactionRepository paymentTransactionRepository,
                           UserRepository userRepository,
                           SubscriptionRepository subscriptionRepository,
                           PlanRepository planRepository,
                           CouponRepository couponRepository) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.couponRepository = couponRepository;
    }

    @Transactional
    public byte[] generate(PaymentTransaction transaction) {
        if (transaction == null) {
            return null;
        }

        try {
            User user = userRepository.findById(transaction.getUserId()).orElse(null);
            Subscription subscription = transaction.getSubscriptionId() != null
                    ? subscriptionRepository.findById(transaction.getSubscriptionId()).orElse(null) : null;
            Plan plan = subscription != null && subscription.getPlanId() != null
                    ? planRepository.findById(subscription.getPlanId()).orElse(null) : null;
            Coupon coupon = transaction.getCouponId() != null
                    ? couponRepository.findById(transaction.getCouponId()).orElse(null) : null;

            String invoiceNumber = "INV-" + String.format("%06d", transaction.getId());
            String issuedAt = transaction.getCreatedAt() != null ? transaction.getCreatedAt().format(ISSUED_AT) : "";
            String currency = (transaction.getCurrency() != null ? transaction.getCurrency()
                    : transaction.getCurrencyCode() != null ? transaction.getCurrencyCode() : "USD").toUpperCase();

            byte[] content = render(transaction, user, plan, coupon, invoiceNumber, issuedAt, currency);

            String path = "invoices/" + transaction.getId() + ".pdf";
            try {
                Path targetPath = Paths.get("storage/app/public", path).toAbsolutePath();
                Files.createDirectories(targetPath.getParent());
                Files.write(targetPath, content);
            } catch (IOException e) {
                log.warn("InvoiceService: could not persist invoice PDF to disk for transaction {}: {}", transaction.getId(), e.getMessage());
            }

            transaction.setInvoicePath(path);
            paymentTransactionRepository.save(transaction);

            return content;
        } catch (Exception e) {
            log.error("InvoiceService::generate failed for transaction {}: {}", transaction.getId(), e.getMessage(), e);
            return null;
        }
    }

    private byte[] render(PaymentTransaction tx, User user, Plan plan, Coupon coupon,
                           String invoiceNumber, String issuedAt, String currency) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            PDType1Font bold = PDType1Font.HELVETICA_BOLD;
            PDType1Font regular = PDType1Font.HELVETICA;

            float left = 56f;
            float right = PDRectangle.LETTER.getWidth() - 56f;
            float y = PDRectangle.LETTER.getHeight() - 60f;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Header
                drawText(cs, bold, 18, left, y, appName);
                drawTextRight(cs, bold, 22, right, y, "INVOICE");
                y -= 20;
                drawTextRight(cs, regular, 10, right, y, invoiceNumber);
                y -= 14;
                drawTextRight(cs, regular, 10, right, y, "Issued: " + issuedAt);
                y -= 40;

                // Billed to
                drawText(cs, bold, 9, left, y, "BILLED TO");
                y -= 16;
                drawText(cs, regular, 12, left, y, user != null && user.getName() != null ? user.getName() : "");
                y -= 14;
                drawText(cs, regular, 12, left, y, user != null && user.getEmail() != null ? user.getEmail() : "");
                y -= 36;

                // Line item table header
                cs.setNonStrokingColor(0.95f, 0.95f, 0.96f);
                cs.addRect(left, y - 6, right - left, 26);
                cs.fill();
                cs.setNonStrokingColor(0f, 0f, 0f);
                drawText(cs, bold, 9, left + 8, y + 4, "DESCRIPTION");
                drawTextRight(cs, bold, 9, right - 90, y + 4, "AMOUNT");
                drawTextRight(cs, bold, 9, right - 8, y + 4, "STATUS");
                y -= 34;

                String description = (plan != null ? plan.getName() + " Plan" : "Subscription");
                BigDecimal amount = centsToAmount(tx.getAmountCents());
                String status = "refunded".equalsIgnoreCase(tx.getStatus()) ? "Refunded" : "Paid";

                drawText(cs, regular, 11, left + 8, y, description);
                drawTextRight(cs, regular, 11, right - 90, y, currency + " " + amount);
                drawTextRight(cs, regular, 11, right - 8, y, status);
                y -= 16;

                if (coupon != null) {
                    drawText(cs, regular, 9, left + 8, y, "Coupon: " + coupon.getCode());
                    y -= 16;
                }

                y -= 20;
                cs.setLineWidth(0.5f);
                cs.moveTo(left, y);
                cs.lineTo(right, y);
                cs.stroke();
                y -= 24;

                // Totals
                Integer taxCents = tx.getTaxAmountCents();
                if (taxCents != null && taxCents > 0) {
                    BigDecimal subtotal = centsToAmount(tx.getAmountCents() - taxCents);
                    drawTextRight(cs, regular, 10, right - 90, y, "Subtotal");
                    drawTextRight(cs, regular, 10, right - 8, y, subtotal.toString());
                    y -= 16;
                    drawTextRight(cs, regular, 10, right - 90, y, "Tax");
                    drawTextRight(cs, regular, 10, right - 8, y, centsToAmount(taxCents).toString());
                    y -= 16;
                }
                Integer refundedCents = tx.getRefundedCents();
                if (refundedCents != null && refundedCents > 0) {
                    drawTextRight(cs, regular, 10, right - 90, y, "Refunded");
                    drawTextRight(cs, regular, 10, right - 8, y, "- " + centsToAmount(refundedCents));
                    y -= 16;
                }

                y -= 4;
                cs.setLineWidth(1f);
                cs.moveTo(right - 200, y);
                cs.lineTo(right, y);
                cs.stroke();
                y -= 18;

                drawTextRight(cs, bold, 13, right - 90, y, "Total");
                drawTextRight(cs, bold, 13, right - 8, y, currency + " " + amount);

                // Footer
                float footerY = 60f;
                cs.setLineWidth(0.5f);
                cs.moveTo(left, footerY + 20);
                cs.lineTo(right, footerY + 20);
                cs.stroke();
                drawTextCentered(cs, regular, 9, (left + right) / 2, footerY, "Thank you for your business — " + appName);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private BigDecimal centsToAmount(Integer cents) {
        return BigDecimal.valueOf(cents != null ? cents : 0).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private void drawText(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text != null ? text : "");
        cs.endText();
    }

    private void drawTextRight(PDPageContentStream cs, PDType1Font font, float size, float rightX, float y, String text) throws IOException {
        String t = text != null ? text : "";
        float width = font.getStringWidth(t) / 1000 * size;
        drawText(cs, font, size, rightX - width, y, t);
    }

    private void drawTextCentered(PDPageContentStream cs, PDType1Font font, float size, float centerX, float y, String text) throws IOException {
        String t = text != null ? text : "";
        float width = font.getStringWidth(t) / 1000 * size;
        drawText(cs, font, size, centerX - width / 2, y, t);
    }
}
