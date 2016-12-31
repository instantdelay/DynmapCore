package org.dynmap.chat;

public class RawWebMessage {

    private String sessionUsername;
    private String ip;
    private String providedName;
    private String message;
    private long timestamp;
    
    public String getSessionUsername() {
        return sessionUsername;
    }
    public void setSessionUsername(String sessionUsername) {
        this.sessionUsername = sessionUsername;
    }
    public String getIp() {
        return ip;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }
    public String getProvidedName() {
        return providedName;
    }
    public void setProvidedName(String providedName) {
        this.providedName = providedName;
    }

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
}
