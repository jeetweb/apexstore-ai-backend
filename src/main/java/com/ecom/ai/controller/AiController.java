package com.ecom.ai.controller;

import com.ecom.ai.dto.AiChatRequest;
import com.ecom.ai.dto.AiChatResponse;
import com.ecom.ai.dto.SemanticSearchResult;
import com.ecom.ai.service.AiAgentService;
import com.ecom.ai.service.VectorSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Controller
@Tag(name = "AI & Vector Search API", description = "Endpoints for Agentic Function Calling and Dense Vector Semantic Discovery")
public class AiController {

    private final AiAgentService aiAgentService;
    private final VectorSearchService vectorSearchService;

    public AiController(AiAgentService aiAgentService, VectorSearchService vectorSearchService) {
        this.aiAgentService = aiAgentService;
        this.vectorSearchService = vectorSearchService;
    }

    // --- REST APIs ---

    @PostMapping("/api/ai/chat")
    @ResponseBody
    @Operation(summary = "Chat with Autonomous AI Commerce Agent with Tool Calling")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        String msg = request != null ? request.getMessage() : "";
        AiChatResponse response = aiAgentService.processMessage(msg);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/ai/search")
    @ResponseBody
    @Operation(summary = "Vector Semantic Search over Catalog using dense vector cosine similarity")
    public ResponseEntity<List<SemanticSearchResult>> semanticSearchApi(
            @RequestParam("query") String query,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "limit", defaultValue = "6") int limit) {
        List<SemanticSearchResult> results = vectorSearchService.search(query, maxPrice, limit);
        return ResponseEntity.ok(results);
    }

    // --- Thymeleaf Web View ---

    @GetMapping("/ai/search")
    public String semanticSearchView(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            Model model) {
        List<SemanticSearchResult> results = List.of();
        if (query != null && !query.isBlank()) {
            results = vectorSearchService.search(query, maxPrice, 12);
        }

        model.addAttribute("query", query);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("results", results);
        return "products/semantic-search";
    }
}
