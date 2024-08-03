package com.msf.youtubechatapp.models;

public class User {

    String userId;
    String username;
    String status;
    String profileImageUrl;

    public User(String userId, String username, String status, String profileImageUrl) {
        this.userId = userId;
        this.username = username;
        this.status = status;
        this.profileImageUrl = profileImageUrl;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getStatus() {
        return status;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }
}
