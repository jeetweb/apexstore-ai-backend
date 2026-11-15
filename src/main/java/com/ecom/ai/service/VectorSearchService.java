package com.ecom.ai.service;

import com.ecom.ai.dto.SemanticSearchResult;
import com.ecom.ai.model.ProductEmbedding;
import com.ecom.model.Product;
import com.ecom.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise Vector Search & Discovery Service.
 * Implements an in-memory dense vector store with cosine similarity,
 * metadata pre-filtering (price/category constraints), and hybrid scoring.
 */
@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final ProductRepository productRepository;
    private final EmbeddingService embeddingService;
    private final Map<Long, ProductEmbedding> vectorIndex = new ConcurrentHashMap<>();

    private static final Pattern PRICE_EXTRACTION_PATTERN = Pattern.compile("(?:under|below|less than|<|budget of)?\\s*\\$?([0-9]+(?:\\.[0-9]{2})?)", Pattern.CASE_INSENSITIVE);

    public VectorSearchService(ProductRepository productRepository, EmbeddingService embeddingService) {
        this.productRepository = productRepository;
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void initializeIndex() {
        refreshIndex();
    }

    public synchronized void refreshIndex() {
        vectorIndex.clear();
        List<Product> products = productRepository.findByActiveTrue();
        for (Product product : products) {
            indexProduct(product);
        }
        log.info("Initialized Vector Search Index with {} catalog products.", vectorIndex.size());
    }

    public void indexProduct(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }

        String contentToEmbed = buildProductEmbeddingDocument(product);
        float[] vector = embeddingService.embedText(contentToEmbed);

        String categorySlug = product.getCategory() != null ? product.getCategory().getSlug() : "general";
        ProductEmbedding embedding = new ProductEmbedding(
                product.getId(),
                vector,
                contentToEmbed,
                categorySlug,
                product.getPrice(),
                product.getSku()
        );

        vectorIndex.put(product.getId(), embedding);
    }

    /**
     * Executes Semantic Vector Search with natural language query parsing,
     * optional price extraction, and hybrid cosine similarity scoring.
     */
    public List<SemanticSearchResult> search(String naturalLanguageQuery, BigDecimal explicitMaxPrice, int limit) {
        if (vectorIndex.isEmpty()) {
            refreshIndex();
        }

        if (naturalLanguageQuery == null || naturalLanguageQuery.isBlank()) {
            return Collections.emptyList();
        }

        // Extract budget constraint from prompt if not explicitly passed
        BigDecimal maxPrice = explicitMaxPrice != null ? explicitMaxPrice : extractBudgetFromQuery(naturalLanguageQuery);

        float[] queryVector = embeddingService.embedText(naturalLanguageQuery);
        String normalizedQuery = naturalLanguageQuery.toLowerCase(Locale.ROOT);

        List<ScoredProduct> scoredList = new ArrayList<>();

        for (Map.Entry<Long, ProductEmbedding> entry : vectorIndex.entrySet()) {
            ProductEmbedding item = entry.getValue();

            // Hard Filter: Price ceiling if specified
            if (maxPrice != null && item.getPrice().compareTo(maxPrice) > 0) {
                continue;
            }

            // 1. Vector Cosine Similarity
            double cosineScore = embeddingService.cosineSimilarity(queryVector, item.getVector());

            // 2. Hybrid Boost (Lexical matching for exact brand/SKU/category keywords)
            double hybridBoost = 0.0;
            String text = item.getIndexedText().toLowerCase(Locale.ROOT);
            for (String word : normalizedQuery.split("\\s+")) {
                if (word.length() > 2 && text.contains(word)) {
                    hybridBoost += 0.08;
                }
            }

            double finalScore = Math.min(1.0, cosineScore + hybridBoost);

            if (finalScore >= 0.15) { // Minimum relevance threshold
                scoredList.add(new ScoredProduct(item.getProductId(), finalScore, cosineScore));
            }
        }

        // Sort descending by score
        scoredList.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));

        List<SemanticSearchResult> results = new ArrayList<>();
        int count = 0;
        for (ScoredProduct scored : scoredList) {
            if (count++ >= limit) break;
            Optional<Product> prodOpt = productRepository.findById(scored.productId);
            if (prodOpt.isPresent()) {
                Product prod = prodOpt.get();
                String matchReason = buildMatchExplanation(prod, scored.finalScore, naturalLanguageQuery);
                List<String> features = extractMatchedFeatures(prod, naturalLanguageQuery);
                results.add(new SemanticSearchResult(prod, scored.finalScore, matchReason, features));
            }
        }

        return results;
    }

    private String buildProductEmbeddingDocument(Product product) {
        StringBuilder sb = new StringBuilder();
        sb.append(product.getName()).append(" ");
        if (product.getCategory() != null) {
            sb.append(product.getCategory().getName()).append(" ");
        }
        sb.append(product.getSku()).append(" ");
        if (product.getDescription() != null) {
            sb.append(product.getDescription()).append(" ");
        }
        sb.append("$").append(product.getPrice());
        return sb.toString();
    }

    private BigDecimal extractBudgetFromQuery(String query) {
        if (query == null) return null;
        Matcher matcher = PRICE_EXTRACTION_PATTERN.matcher(query);
        BigDecimal foundPrice = null;
        while (matcher.find()) {
            try {
                String raw = matcher.group(1);
                foundPrice = new BigDecimal(raw);
            } catch (Exception ignored) {
            }
        }
        return foundPrice;
    }

    private String buildMatchExplanation(Product p, double score, String query) {
        int percent = (int) Math.round(score * 100);
        return String.format("%d%% semantic intent match for \"%s\" in %s",
                percent,
                p.getName(),
                p.getCategory() != null ? p.getCategory().getName() : "Catalog");
    }

    private List<String> extractMatchedFeatures(Product p, String query) {
        List<String> features = new ArrayList<>();
        if (p.isInStock()) {
            features.add("In Stock (" + p.getStockQuantity() + " units available)");
        } else {
            features.add("Currently Out of Stock");
        }
        features.add("Price: $" + p.getPrice());
        if (p.getCategory() != null) {
            features.add("Category: " + p.getCategory().getName());
        }
        return features;
    }

    private record ScoredProduct(Long productId, double finalScore, double rawCosine) {}
}
