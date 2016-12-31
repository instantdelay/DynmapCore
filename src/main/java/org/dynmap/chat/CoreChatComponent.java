package org.dynmap.chat;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.dynmap.Component;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.Event.Listener;
import org.dynmap.Log;
import org.dynmap.chat.WebMessage.ChatClientId;
import org.dynmap.chat.WebMessage.ClientIp;
import org.dynmap.chat.WebMessage.GeneratedAlias;
import org.dynmap.chat.WebMessage.PlayerId;
import org.dynmap.chat.WebMessage.UnverifiedName;

public class CoreChatComponent extends Component implements Listener<WebMessageEvent> {

    private final ChatConfiguration chatConfig;
    private final Map<String, String> useralias = new HashMap<String,String>();
    private int aliasindex = 1;

    private final Map<String, WebUser> disallowedUsers = new HashMap<String, WebUser>();
    private final LinkedList<WebUser> disallowedUserQueue = new LinkedList<WebUser>();
    private final Object disallowedUsersLock = new Object();
    private int maximumMessageInterval = 1000;

    public CoreChatComponent(DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        this.chatConfig = new ChatConfiguration(configuration);
        this.maximumMessageInterval = (int)(chatConfig.webchatInterval * 1000);
        core.events.addListener("webmessage", this);
    }

    private ChatClientId determineSender(RawWebMessage msg) {
        if (msg.getSessionUsername() != null) {
            return new PlayerId(msg.getSessionUsername());
        }

        if (chatConfig.use_player_login_ip) {
            // Try to match using IPs of player logins
            List<String> ids = core.getIDsForIP(msg.getIp());
            if (ids != null && !ids.isEmpty()) {
                return new PlayerId(ids.get(0));
            }
            else if (chatConfig.req_player_login_ip) {
                Log.info("Ignore message from '" + msg.getIp() + "' - no matching player login recorded");
                return null;
            }
        }

        if (chatConfig.trust_client_name && msg.getProvidedName() != null) {
            return new UnverifiedName(msg.getProvidedName());
        }

        if (chatConfig.hidewebchatip) {
            String ipAlias;
            synchronized (useralias) {
                ipAlias = useralias.get(msg.getIp());
                if (ipAlias == null) {
                    // Generate one
                    ipAlias = String.format("web-%03d", aliasindex);
                    aliasindex++;
                    useralias.put(msg.getIp(), ipAlias);
                }
            }
            return new GeneratedAlias(ipAlias);
        }

        return new ClientIp(msg.getIp());
    }

    @Override
    public void triggered(WebMessageEvent t) {
        RawWebMessage msg = t.getMessage();
        ChatClientId sender = determineSender(msg);

        if (sender == null) {
            // Could not determine sender following our configuration
            t.setResult("not-allowed");
            return;
        }

        if (sender instanceof PlayerId) {
            String username = ((PlayerId)sender).getUsername();
            if (chatConfig.block_banned_player_chat) {
                if (core.getServer().isPlayerBanned(username)) {
                    Log.info("Ignore message from '" + msg.getIp() + "' - banned player (" + username + ")");
                    t.setResult("not-allowed");
                    return;
                }
            }
            if (chatConfig.chat_perm && !core.getServer().checkPlayerPermission(username, "webchat")) {
                Log.info("Rejected web chat from " + msg.getIp() + ": not permitted (" + username + ")");
                t.setResult("not-allowed");
                return;
            }
        }

        final long now = System.currentTimeMillis();

        synchronized (disallowedUsersLock) {
            // Allow users that  user that are now allowed to send messages.
            while (!disallowedUserQueue.isEmpty()) {
                WebUser wu = disallowedUserQueue.getFirst();
                if (now >= wu.nextMessageTime) {
                    disallowedUserQueue.remove();
                    disallowedUsers.remove(wu.name);
                } else {
                    break;
                }
            }

            WebUser user = disallowedUsers.get(sender.toString());
            if (user == null) {
                user = new WebUser(sender.toString(), now + maximumMessageInterval);
                disallowedUsers.put(user.name, user);
                disallowedUserQueue.add(user);
            }
            else {
                t.setResult("not-allowed");
                return;
            }
        }

        String message = msg.getMessage();
        if ((chatConfig.length_limit > 0) && (message.length() > chatConfig.length_limit))
            message = message.substring(0, chatConfig.length_limit);

        core.webChat(sender.toString(), message);
    }

    public static class WebUser {
        public long nextMessageTime;
        public String name;
        public WebUser(String name, long nextMessageTime) {
            this.name = name;
            this.nextMessageTime = nextMessageTime;
        }
    }

}
