package com.ecom.ai.model;

import java.math.BigDecimal;
import java.util.Arrays;

public class ProductEmbedding {

    private final Long productId;
    private final float[] vector;
    private final String indexedText;
    private final String categorySlug;
    private final BigDecimal price;
    private final String sku;

    public ProductEmbedding(Long productId, float[] vector, String indexedText, String categorySlug, BigDecimal price, String sku) {
        this.productId = productId;
        this.vector = vector;
        this.indexedText = indexedText;
        this.categorySlug = categorySlug;
        this.price = price;
        this.sku = sku;
    }

    public Long getProductId() {
        return productId;
    }

    public float[] getVector() {
        return vector;
    }

    public String getIndexedText() {
        return indexedText;
    }

    public String getCategorySlug() {
        return categorySlug;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getSku() {
        return sku;
    }

    @Override
    public String toString() {
        return "ProductEmbedding{" +
                "productId=" + productId +
                ", sku='" + sku + '\'' +
                ", dimensions=" + (vector != null ? vector.length : 0) +
                '}';
    }
}
