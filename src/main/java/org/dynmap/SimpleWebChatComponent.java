package org.dynmap;

import static org.dynmap.JSONUtils.s;

import java.util.List;

import org.dynmap.common.DynmapListenerManager;
import org.dynmap.common.DynmapListenerManager.ChatEventListener;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.servlet.SendMessageServlet;
import org.dynmap.servlet.SendMessageServlet.Message;
import org.json.simple.JSONObject;

public class SimpleWebChatComponent extends Component {
    
    private final ChatConfiguration chatConfig;

    public SimpleWebChatComponent(final DynmapCore plugin, final ConfigurationNode config) {
        super(plugin, config);
        this.chatConfig = new ChatConfiguration(configuration);
        
        plugin.events.addListener(InternalEvents.BUILD_CLIENT_CONFIG, new Event.Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                s(t, "allowwebchat", chatConfig.allowWebChat);
                s(t, "webchat-interval", chatConfig.webchatInterval);
                s(t, "webchat-requires-login", chatConfig.require_login);
                s(t, "chatlengthlimit", chatConfig.length_limit);
            }
        });
        
        if(chatConfig.allowWebChat) {
            activate();
        }
    }
    
    private void activate() {
        core.events.addListener(InternalEvents.WEBCHAT, new Event.Listener<ChatEvent>() {
            @Override
            public void triggered(ChatEvent t) {
                if(core.getServer().sendWebChatEvent(t.source, t.name, t.message)) {
                    // Broadcast the message in-game
                    core.getServer().broadcastMessage(formatWebChatMessage(t));
                    
                    // And then to other web UI clients
                    if(core.mapManager != null) {
                        core.mapManager.pushUpdate(new Client.ChatMessage("web", null, t.name, t.message, null));
                    }
                }
            }
        });
        
        core.listenerManager.addListener(EventType.PLAYER_CHAT, new ChatEventListener() {
            @Override
            public void chatEvent(DynmapPlayer p, String msg) {
                if(core.disable_chat_to_web) return;
                if(core.mapManager != null)
                    core.mapManager.pushUpdate(new Client.ChatMessage("player", "", p.getDisplayName(), msg, p.getName()));
            }
        });
        core.listenerManager.addListener(EventType.PLAYER_JOIN, new DynmapListenerManager.PlayerEventListener() {
            @Override
            public void playerEvent(DynmapPlayer p) {
                if(core.disable_chat_to_web) return;
                if((core.mapManager != null) && (core.playerList != null) && (core.playerList.isVisiblePlayer(p.getName()))) {
                    core.mapManager.pushUpdate(new Client.PlayerJoinMessage(p.getDisplayName(), p.getName()));
                }
            }
        });
        core.listenerManager.addListener(EventType.PLAYER_QUIT, new DynmapListenerManager.PlayerEventListener() {
            @Override
            public void playerEvent(DynmapPlayer p) {
                if(core.disable_chat_to_web) return;
                if((core.mapManager != null) && (core.playerList != null) && (core.playerList.isVisiblePlayer(p.getName()))) {
                    core.mapManager.pushUpdate(new Client.PlayerQuitMessage(p.getDisplayName(), p.getName()));
                }
            }
        });
        
        
        final SendMessageServlet messageServlet = new SendMessageServlet(core, chatConfig);
        messageServlet.onMessageReceived.addListener(new Event.Listener<Message> () {
            @Override
            public void triggered(Message t) {
                core.webChat(t.name, t.message);
            }
        });
        
        core.addServlet("/up/sendmessage", messageServlet);
    }
    
    /**
     * Format a web UI chat message to be broadcast to Minecraft clients.
     */
    private String formatWebChatMessage(ChatEvent t) {
        String msgfmt = core.configuration.getString("webmsgformat", null);
        if(msgfmt != null) {
            msgfmt = unescapeString(msgfmt);
            return msgfmt.replace("%playername%", t.name).replace("%message%", t.message);
        }
        else {
            return unescapeString(core.configuration.getString("webprefix", "\u00A72[WEB] ")) + t.name + ": " + unescapeString(core.configuration.getString("websuffix", "\u00A7f")) + t.message;
        }
    }
    
    public static class ChatConfiguration {
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
    
}
