package com.bitchat.mesh;

import java.io.Serializable;

public class Message implements Serializable {
    private String id;
    private String senderId;
    private String senderName;
    private String content;
    private long timestamp;
    private int hopCount;
    private boolean isMine;

    public Message() {}

    public Message(String id, String senderId, String senderName, String content, long timestamp) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = timestamp;
        this.hopCount = 0;
        this.isMine = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }
    public void incrementHop() { this.hopCount++; }
    
    public boolean isMine() { return isMine; }
    public void setMine(boolean mine) { isMine = mine; }
}
