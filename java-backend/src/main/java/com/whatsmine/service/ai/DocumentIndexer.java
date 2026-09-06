package com.whatsmine.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsmine.model.AiKbChunk;
import com.whatsmine.model.AiKbDocument;
import com.whatsmine.queue.QueueDispatcher;
import com.whatsmine.repository.AiKbChunkRepository;
import com.whatsmine.repository.AiKbDocumentRepository;
import com.whatsmine.service.ai.llm.LlmManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Ports php/app/Modules/AI/Jobs/IndexDocumentJob.php's extractText() beyond
 * plain text/FAQ: real URL fetching (HTML -> text via Jsoup), PDF parsing
 * (Apache PDFBox, reading the uploaded file back from local storage under
 * storage/app/public/ — the same disk convention MediaService writes to),
 * and sitemap crawling (parses <urlset>/<sitemapindex> XML, fans out one
 * child "url" document per page via the real IndexDocumentJob queue so a
 * sitemap with many pages doesn't block the originating request, matching
 * PHP's own stated reason for doing this asynchronously).
 */
@Service
public class DocumentIndexer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);
    private static final int SITEMAP_URL_LIMIT = 200;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Autowired
    private AiKbDocumentRepository documentRepository;

    @Autowired
    private AiKbChunkRepository chunkRepository;

    @Autowired
    private LlmGateway llmGateway;

    @Autowired
    private LlmManager llmManager;

    @Autowired
    private EmbeddingStore embeddingStore;

    @Autowired
    private QueueDispatcher queueDispatcher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void indexDocument(Long documentId, Long workspaceId) {
        AiKbDocument doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) return;

        doc.setStatus("indexing");
        documentRepository.save(doc);

        try {
            String text = extractText(doc, workspaceId);
            List<String> chunksText = chunkText(text, 800, 100);

            // Delete old chunks
            chunkRepository.deleteByDocumentId(doc.getId());

            List<AiKbChunk> chunkModels = new ArrayList<>();
            for (int i = 0; i < chunksText.size(); i++) {
                String cText = chunksText.get(i);
                AiKbChunk chunk = new AiKbChunk();
                chunk.setKbId(doc.getKbId());
                chunk.setDocumentId(doc.getId());
                chunk.setOrd(i);
                chunk.setContent(cText);
                chunk.setTokens((int) Math.ceil(cText.length() / 4.0));
                chunkModels.add(chunkRepository.save(chunk));
            }

            // Embed chunks — a missing embedding-capable provider is non-fatal
            // (the document still indexes as plain text); a transient API error
            // during the actual embed() call propagates so the job retries
            // instead of silently indexing with no vectors.
            if (workspaceId != null && !chunkModels.isEmpty()) {
                if (embedProviderAvailable(workspaceId)) {
                    List<String> texts = chunkModels.stream().map(AiKbChunk::getContent).toList();
                    List<List<Double>> embeddings = llmGateway.embed(workspaceId, texts);
                    for (int i = 0; i < chunkModels.size(); i++) {
                        if (i < embeddings.size()) {
                            embeddingStore.storeEmbedding(chunkModels.get(i), embeddings.get(i));
                        }
                    }
                } else {
                    log.warn("Document {} indexed without embeddings — no embedding-capable provider configured for workspace {}", doc.getId(), workspaceId);
                }
            }

            int totalTokens = chunkModels.stream().mapToInt(AiKbChunk::getTokens).sum();
            doc.setStatus("indexed");
            doc.setTokens(totalTokens);
            doc.setLastIndexedAt(LocalDateTime.now());
            documentRepository.save(doc);

        } catch (Exception e) {
            log.error("Failed to index document {}: {}", doc.getId(), e.getMessage());
            doc.setStatus("error");
            documentRepository.save(doc);
        }
    }

    /** True when the workspace has an embedding-capable provider (OpenAI/Gemini) configured. */
    private boolean embedProviderAvailable(Long workspaceId) {
        try {
            llmManager.forWorkspaceEmbed(workspaceId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractText(AiKbDocument doc, Long workspaceId) {
        String sourceRef = doc.getSourceRef() != null ? doc.getSourceRef() : "";
        return switch (doc.getSourceType() != null ? doc.getSourceType().toLowerCase() : "") {
            case "text" -> sourceRef;
            case "faq" -> formatFaq(sourceRef);
            case "url" -> fetchUrl(sourceRef);
            case "file" -> readFile(sourceRef);
            case "sitemap" -> processSitemap(doc, workspaceId);
            default -> sourceRef;
        };
    }

    private String formatFaq(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            List<Map<String, String>> faqPairs = objectMapper.readValue(raw, new TypeReference<List<Map<String, String>>>() {});
            StringBuilder sb = new StringBuilder();
            for (Map<String, String> pair : faqPairs) {
                String q = pair.getOrDefault("question", "").trim();
                String a = pair.getOrDefault("answer", "").trim();
                if (!q.isEmpty() || !a.isEmpty()) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append("Q: ").append(q).append("\nA: ").append(a);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return raw;
        }
    }

    private String fetchUrl(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            String html = httpGet(url, Duration.ofSeconds(30));
            if (html == null) return "";
            Document parsed = Jsoup.parse(html, url);
            parsed.select("script, style, noscript").remove();
            return parsed.text();
        } catch (Exception e) {
            log.warn("Failed to fetch URL '{}' for KB indexing: {}", url, e.getMessage());
            return "";
        }
    }

    /**
     * Reads an uploaded document back from local disk storage. sourceRef is a
     * storage-relative key (e.g. "kb-docs/{uuid}.pdf") under storage/app/public/,
     * the same disk MediaService writes uploads to.
     */
    private String readFile(String path) {
        if (path == null || path.isBlank()) return "";
        try {
            Path filePath = Paths.get("storage/app/public", path);
            if (!Files.exists(filePath)) {
                log.warn("KB document file not found on disk: {}", filePath);
                return "";
            }

            String ext = "";
            int dot = path.lastIndexOf('.');
            if (dot >= 0) ext = path.substring(dot + 1).toLowerCase();

            if ("pdf".equals(ext)) {
                try (PDDocument pdf = PDDocument.load(filePath.toFile())) {
                    return new PDFTextStripper().getText(pdf);
                }
            }

            return Files.readString(filePath);
        } catch (Exception e) {
            log.warn("Failed to read KB document file '{}': {}", path, e.getMessage());
            return "";
        }
    }

    /**
     * Parses a sitemap and fans out one lightweight child "url" document per
     * page via the real IndexDocumentJob queue, so this request never blocks
     * on crawling hundreds of pages inline. Handles both <urlset> (flat page
     * list) and <sitemapindex> (nested sitemaps, e.g. Yoast/WordPress) — for
     * the latter, each nested sitemap is enqueued as its own "sitemap" child.
     */
    private String processSitemap(AiKbDocument doc, Long workspaceId) {
        String sitemapUrl = doc.getSourceRef();
        if (sitemapUrl == null || sitemapUrl.isBlank()) return "";

        try {
            String xml = httpGet(sitemapUrl, Duration.ofSeconds(20));
            if (xml == null) return "";

            org.w3c.dom.Document xmlDoc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            NodeList sitemapNodes = xmlDoc.getElementsByTagName("sitemap");
            boolean isIndex = sitemapNodes.getLength() > 0;
            NodeList entryNodes = isIndex ? sitemapNodes : xmlDoc.getElementsByTagName("url");
            String childType = isIndex ? "sitemap" : "url";

            LinkedHashSet<String> locs = new LinkedHashSet<>();
            for (int i = 0; i < entryNodes.getLength(); i++) {
                Element entry = (Element) entryNodes.item(i);
                NodeList locNodes = entry.getElementsByTagName("loc");
                if (locNodes.getLength() == 0) continue;
                String loc = locNodes.item(0).getTextContent();
                if (loc != null && !loc.isBlank()) {
                    locs.add(loc.trim());
                }
            }

            int count = 0;
            for (String loc : locs) {
                if (count >= SITEMAP_URL_LIMIT) break;
                AiKbDocument child = new AiKbDocument();
                child.setKbId(doc.getKbId());
                child.setTitle(loc);
                child.setSourceType(childType);
                child.setSourceRef(loc);
                child.setStatus("pending");
                child = documentRepository.save(child);

                Map<String, Object> jobData = new LinkedHashMap<>();
                jobData.put("documentId", child.getId());
                jobData.put("workspaceId", workspaceId);
                queueDispatcher.dispatch("ai", "IndexDocumentJob", jobData);
                count++;
            }

            return "";
        } catch (Exception e) {
            // Malformed sitemap XML — fall back to treating it as a plain page.
            log.warn("Failed to parse sitemap '{}', falling back to URL fetch: {}", sitemapUrl, e.getMessage());
            return fetchUrl(sitemapUrl);
        }
    }

    private String httpGet(String url, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "Mozilla/5.0 (compatible; HubNotificationBot/1.0)")
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        return response.body();
    }

    public List<String> chunkText(String text, int size, int overlap) {
        if (text == null || text.isBlank()) return List.of();
        String[] words = text.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();
        int i = 0;
        while (i < words.length) {
            int end = Math.min(i + size, words.length);
            String[] slice = Arrays.copyOfRange(words, i, end);
            chunks.add(String.join(" ", slice));
            i += (size - overlap);
        }
        return chunks;
    }
}
