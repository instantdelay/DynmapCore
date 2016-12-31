package org.dynmap.chat;

public class WebMessageEvent {

    private RawWebMessage message;
    private String result;

    public WebMessageEvent(RawWebMessage message) {
        this.message = message;
    }

    public RawWebMessage getMessage() {
        return message;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

}
