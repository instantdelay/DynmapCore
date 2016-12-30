package org.dynmap.chat;

import java.util.List;

import org.dynmap.ConfigurationNode;

public class ChatConfiguration {
    /**
     * Can users of the web UI send chat messages?
     */
    public final boolean allowWebChat;
    /**
     * Should anonymous users have their IP masked with some generated identifier?
     */
    public final boolean hidewebchatip;
    /**
     * Trust the username provided in chat messages from an unauthenticated web client?
     */
    public final boolean trust_client_name;
    /**
     * Attempt to match anonymous web users to their Minecraft accounts based on last login IPs?
     */
    public final boolean use_player_login_ip;
    /**
     * If {@link #use_player_login_ip} is <code>true</code> but an anonymous user cannot be matched
     * to a previous Minecraft login, should we deny messages?
     * 
     * <p>If <code>true</code>, an anonymous web user will be prevented from chatting until they log
     * in via Minecraft from the same IP.
     */
    public final boolean req_player_login_ip;
    /**
     * Should an anonymous web user whose IP was matched to a banned Minecraft account be prevented
     * from chatting?
     */
    public final boolean block_banned_player_chat;
    /**
     * Prevent anonymous users from chatting and require a login?
     */
    public final boolean require_login;
    /**
     * <p>Require users to have the <code>dynmap.webchat</code> permission in order to use web chat?
     */
    public final boolean chat_perm;
    /**
     * Minimum number of seconds between web chat messages.
     */
    public final float webchatInterval;
    /**
     * Message displayed to the user in the event that the webchatInterval rate limit is exceeded.
     * 
     * <p>TODO This isn't actually used currently...
     */
    public final String spammessage;
    /**
     * Max character length of web chat message. Messages longer than this will be truncated.
     * 
     * <p>0 = unlimited
     */
    public final int length_limit;
    /**
     * Addresses of proxies from which we'll trust the <code>X-Forwarded-For</code> header.
     */
    public final List<String> trustedproxies;
    
    public ChatConfiguration(final ConfigurationNode config) {
        allowWebChat = config.getBoolean("allowwebchat", false);
        hidewebchatip = config.getBoolean("hidewebchatip", false);
        trust_client_name = config.getBoolean("trustclientname", false);
        use_player_login_ip = config.getBoolean("use-player-login-ip", true);
        req_player_login_ip = config.getBoolean("require-player-login-ip", false);
        block_banned_player_chat = config.getBoolean("block-banned-player-chat", false);
        require_login = config.getBoolean("webchat-requires-login", false);
        chat_perm = config.getBoolean("webchat-permissions", false);
        webchatInterval = config.getFloat("webchat-interval", 1);
        spammessage = config.getString("spammessage", "You may only chat once every %interval% seconds.");
        length_limit = config.getInteger("chatlengthlimit", 256);
        trustedproxies = config.getStrings("trusted-proxies", null);
    }
}