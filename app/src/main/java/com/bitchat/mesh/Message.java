package com.bitchat.mesh;

public class Message {
    public String id;
    public String senderId;
    public String senderName;
    public String content;
    public long timestamp;
    public boolean isEncrypted;

    public Message() {}

    public Message(String id, String senderId, String senderName, String content, long timestamp, boolean isEncrypted) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = timestamp;
        this.isEncrypted = isEncrypted;
    }
}
