package org.dynmap.chat;

public class WebMessage {

    private ChatClientId sender;
//    private String name;
    private String message;
    private String ip;
    private String sessionUsername;
//    public String getName() {
//        return name;
//    }
//    public void setName(String name) {
//        this.name = name;
//    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public String getIp() {
        return ip;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }
    public String getSessionUsername() {
        return sessionUsername;
    }
    public void setSessionUsername(String sessionUsername) {
        this.sessionUsername = sessionUsername;
    }

    public void setSender(ChatClientId sender) {
        this.sender = sender;
    }
    
    public ChatClientId getSender() {
        return sender;
    }
    
    public interface ChatClientId {
    }
    
    public static class UnverifiedName implements ChatClientId {
        private String name;
        public UnverifiedName(String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
        @Override
        public String toString() {
            return getName();
        }
    }
    
    public static class PlayerId implements ChatClientId {
        private String username;
        public PlayerId(String username) {
            this.username = username;
        }
        public String getUsername() {
            return username;
        }
        @Override
        public String toString() {
            return getUsername();
        }
    }
    public static class ClientIp implements ChatClientId {
        private String ip;
        
        public ClientIp(String ip) {
            this.ip = ip;
        }

        public String getIp() {
            return ip;
        }
        @Override
        public String toString() {
            return getIp();
        }
    }
    
    public static class GeneratedAlias implements ChatClientId {
        private String alias;
        public GeneratedAlias(String alias) {
            this.alias = alias;
        }
        public String getAlias() {
            return alias;
        }
        @Override
        public String toString() {
            return getAlias();
        }
    }
    
}
