package com.ecom.ai;

import com.ecom.ai.dto.AiChatRequest;
import com.ecom.ai.dto.AiChatResponse;
import com.ecom.ai.dto.SemanticSearchResult;
import com.ecom.ai.service.AiAgentService;
import com.ecom.ai.service.EmbeddingService;
import com.ecom.ai.service.VectorSearchService;
import com.ecom.model.Order;
import com.ecom.model.Product;
import com.ecom.model.Role;
import com.ecom.model.User;
import com.ecom.repository.OrderRepository;
import com.ecom.repository.ProductRepository;
import com.ecom.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiIntegrationTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private AiAgentService aiAgentService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        vectorSearchService.refreshIndex();

        User testUser = userRepository.findByUsername("john").orElseGet(() -> {
            User u = new User("john", "john@test.com", "pass", "John Doe", "123", Role.ROLE_CUSTOMER);
            return userRepository.save(u);
        });

        testOrder = new Order("ORD-TEST-9988", testUser, null);
        testOrder.setTotalAmount(new BigDecimal("299.99"));
        testOrder.setTrackingNumber("TRK-EXP-112233");
        testOrder = orderRepository.save(testOrder);
    }

    @Test
    @DisplayName("1. Dense Vector Embedding Generation & Cosine Similarity")
    void testEmbeddingAndCosineSimilarity() {
        float[] vec1 = embeddingService.embedText("UltraBook high performance laptop");
        float[] vec2 = embeddingService.embedText("UltraBook high performance laptop");
        float[] vecUnrelated = embeddingService.embedText("Fresh organic garden salad");

        assertThat(vec1).hasSize(EmbeddingService.VECTOR_DIMENSIONS);
        double exactMatch = embeddingService.cosineSimilarity(vec1, vec2);
        assertThat(exactMatch).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));

        double unrelatedMatch = embeddingService.cosineSimilarity(vec1, vecUnrelated);
        assertThat(exactMatch).isGreaterThan(unrelatedMatch);
    }

    @Test
    @DisplayName("2. Vector Semantic Search with Budget Constraint & Intent Matching")
    void testVectorSearchIntent() {
        List<SemanticSearchResult> results = vectorSearchService.search("noise cancellation headphones under $350", null, 3);
        assertThat(results).isNotEmpty();

        SemanticSearchResult top = results.get(0);
        assertThat(top.getProduct().getName()).containsIgnoringCase("Headphones");
        assertThat(top.getSimilarityScore()).isGreaterThan(0.20);
        assertThat(top.getProduct().getPrice()).isLessThanOrEqualTo(new BigDecimal("350.00"));
    }

    @Test
    @DisplayName("3. Autonomous Agent Tool Calling: Track Order in OMS")
    void testAgentToolCallingTrackOrder() {
        AiChatResponse response = aiAgentService.processMessage("Where is my order ORD-TEST-9988?");

        assertThat(response.getToolExecuted()).isEqualTo("track_order");
        assertThat(response.getReply()).contains("ORD-TEST-9988");
        assertThat(response.getReply()).contains("TRK-EXP-112233");
        assertThat(response.getToolPayload()).isNotNull();
    }

    @Test
    @DisplayName("4. Autonomous Agent Tool Calling: Real-Time Warehouse Stock Check")
    void testAgentToolCallingCheckStock() {
        AiChatResponse response = aiAgentService.processMessage("Check live inventory for SKU AUD-AURA-01");

        assertThat(response.getToolExecuted()).isEqualTo("check_inventory");
        assertThat(response.getReply()).contains("AUD-AURA-01");
        assertThat(response.getToolPayload()).isNotNull();
    }

    @Test
    @DisplayName("5. Autonomous Agent Tool Calling: Vector Search Dispatch")
    void testAgentToolCallingVectorSearch() {
        AiChatResponse response = aiAgentService.processMessage("Recommend me an ergonomic vertical mouse");

        assertThat(response.getToolExecuted()).isEqualTo("search_catalog");
        assertThat(response.getReply()).contains("Vector Semantic Search");
    }

    @Test
    @DisplayName("6. REST API: /api/ai/chat returns deterministic structured tool metadata")
    void testAiChatRestEndpoint() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is the status of ORD-TEST-9988?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolExecuted").value("track_order"))
                .andExpect(jsonPath("$.reply").value(containsString("ORD-TEST-9988")));
    }

    @Test
    @DisplayName("7. REST API: /api/ai/search returns semantic vector matches")
    void testAiSearchRestEndpoint() throws Exception {
        mockMvc.perform(get("/api/ai/search")
                        .param("query", "mechanical keyboard")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].product.name").value(containsString("Keyboard")))
                .andExpect(jsonPath("$[0].similarityScore").isNumber());
    }

    @Test
    @DisplayName("8. Thymeleaf View: /ai/search displays semantic discovery UI")
    void testAiSearchView() throws Exception {
        mockMvc.perform(get("/ai/search").param("query", "titanium smartwatch"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/semantic-search"))
                .andExpect(content().string(containsString("Vector Semantic Search")));
    }
}
