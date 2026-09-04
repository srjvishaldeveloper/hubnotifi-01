package com.whatsmine.service.billing;

import com.whatsmine.model.PaymentTransaction;
import com.whatsmine.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class InvoiceService {

    private final PaymentTransactionRepository paymentTransactionRepository;

    public InvoiceService(PaymentTransactionRepository paymentTransactionRepository) {
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    @Transactional
    public byte[] generate(PaymentTransaction transaction) {
        if (transaction == null) {
            return null;
        }

        String path = "invoices/" + transaction.getId() + ".pdf";
        transaction.setInvoicePath(path);
        paymentTransactionRepository.save(transaction);

        String pdfContent = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n"
                + "3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R/Resources<<>>>>endobj\n"
                + "xref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000052 00000 n\n0000000101 00000 n\n"
                + "trailer<</Size 4/Root 1 0 R>>\nstartxref\n178\n%%EOF\n";

        return pdfContent.getBytes(StandardCharsets.UTF_8);
    }
}
