package com.rj1399.customersupport.rag;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PolicyKnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(PolicyKnowledgeService.class);
    private static final String KNOWLEDGE_MARKER = "customer-support-policy";

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resourceResolver;
    private final JdbcTemplate jdbcTemplate;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(400)
            .withMinChunkSizeChars(100)
            .build();

    @Value("${rag.top-k:4}")
    private int topK;

    @Value("${rag.similarity-threshold:0.60}")
    private double similarityThreshold;

    public PolicyKnowledgeService(VectorStore vectorStore,
                                  ResourcePatternResolver resourceResolver,
                                  JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.resourceResolver = resourceResolver;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void indexPolicies() {
        try {
            if (alreadyIndexed()) {
                log.info("rag.knowledge.index skipped=true reason=already-indexed");
                return;
            }

            Resource[] resources = resourceResolver.getResources("classpath:/knowledge/*.md");
            int documents = 0;
            int chunks = 0;
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                String content = read(resource);
                Document source = new Document(content, Map.of(
                        "source", filename,
                        "knowledgeBase", KNOWLEDGE_MARKER,
                        "documentType", "policy"
                ));
                List<Document> splitDocuments = splitter.apply(List.of(source));
                vectorStore.add(splitDocuments);
                documents++;
                chunks += splitDocuments.size();
            }
            log.info("rag.knowledge.index completed documents={} chunks={}", documents, chunks);
        } catch (Exception ex) {
            log.error("rag.knowledge.index failed errorType={} message={}",
                    ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw new IllegalStateException("Unable to initialize policy knowledge base", ex);
        }
    }

    public KnowledgeSearchResult search(String query) {
        long started = System.nanoTime();
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression("knowledgeBase == '" + KNOWLEDGE_MARKER + "'")
                .build());
        long durationMs = (System.nanoTime() - started) / 1_000_000;

        List<KnowledgeMatch> matches = documents.stream()
                .map(document -> new KnowledgeMatch(
                        String.valueOf(document.getMetadata().getOrDefault("source", "unknown")),
                        document.getScore(),
                        document.getText()))
                .toList();

        log.info("rag.knowledge.search queryLength={} matches={} durationMs={}",
                query.length(), matches.size(), durationMs);
        return new KnowledgeSearchResult(query, matches, durationMs);
    }

    private boolean alreadyIndexed() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vector_store WHERE metadata->>'knowledgeBase' = ?",
                    Integer.class, KNOWLEDGE_MARKER);
            return count != null && count > 0;
        } catch (Exception ex) {
            log.debug("rag.knowledge.index check unavailable; proceeding with indexing: {}", ex.getMessage());
            return false;
        }
    }

    private String read(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public record KnowledgeSearchResult(String query, List<KnowledgeMatch> matches, long durationMs) {
        public String context() {
            return matches.stream()
                    .map(match -> "Source: " + match.source() + "\n" + match.content())
                    .collect(Collectors.joining("\n\n---\n\n"));
        }
    }

    public record KnowledgeMatch(String source, Double score, String content) {}
}
