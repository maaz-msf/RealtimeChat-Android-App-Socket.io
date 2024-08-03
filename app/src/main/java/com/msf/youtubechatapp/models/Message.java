package com.msf.youtubechatapp.models;

public class Message {

    String senderId;
    String message;

    public Message(String senderId, String message) {
        this.senderId = senderId;
        this.message = message;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getMessage() {
        return message;
    }
}
