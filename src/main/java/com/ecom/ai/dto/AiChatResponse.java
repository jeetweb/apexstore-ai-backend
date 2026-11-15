package com.ecom.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

public class AiChatResponse {

    private String reply;
    private String toolExecuted;
    private Object toolPayload;
    private List<String> suggestedActions;
    private LocalDateTime timestamp = LocalDateTime.now();

    public AiChatResponse() {
    }

    public AiChatResponse(String reply, String toolExecuted, Object toolPayload, List<String> suggestedActions) {
        this.reply = reply;
        this.toolExecuted = toolExecuted;
        this.toolPayload = toolPayload;
        this.suggestedActions = suggestedActions;
        this.timestamp = LocalDateTime.now();
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getToolExecuted() {
        return toolExecuted;
    }

    public void setToolExecuted(String toolExecuted) {
        this.toolExecuted = toolExecuted;
    }

    public Object getToolPayload() {
        return toolPayload;
    }

    public void setToolPayload(Object toolPayload) {
        this.toolPayload = toolPayload;
    }

    public List<String> getSuggestedActions() {
        return suggestedActions;
    }

    public void setSuggestedActions(List<String> suggestedActions) {
        this.suggestedActions = suggestedActions;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
