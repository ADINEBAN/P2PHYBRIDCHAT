package com.example.common.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public class MessageDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private long conversationId;
    private long senderId;
    private String senderName; // Kèm tên người gửi để hiện thị trong nhóm
    private String content;
    private LocalDateTime createdAt;

    // Type: TEXT, FILE, IMAGE... (đơn giản ta dùng String trước)

    public MessageDTO() {}

    // --- Getters & Setters ---
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getConversationId() { return conversationId; }
    public void setConversationId(long conversationId) { this.conversationId = conversationId; }
    public long getSenderId() { return senderId; }
    public void setSenderId(long senderId) { this.senderId = senderId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}