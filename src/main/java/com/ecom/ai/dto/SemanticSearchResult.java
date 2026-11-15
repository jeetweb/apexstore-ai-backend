package com.ecom.ai.dto;

import com.ecom.model.Product;
import java.util.List;

public class SemanticSearchResult {

    private Product product;
    private double similarityScore;
    private String matchReason;
    private List<String> matchedFeatures;

    public SemanticSearchResult() {
    }

    public SemanticSearchResult(Product product, double similarityScore, String matchReason, List<String> matchedFeatures) {
        this.product = product;
        this.similarityScore = similarityScore;
        this.matchReason = matchReason;
        this.matchedFeatures = matchedFeatures;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public String getMatchReason() {
        return matchReason;
    }

    public void setMatchReason(String matchReason) {
        this.matchReason = matchReason;
    }

    public List<String> getMatchedFeatures() {
        return matchedFeatures;
    }

    public void setMatchedFeatures(List<String> matchedFeatures) {
        this.matchedFeatures = matchedFeatures;
    }
}
