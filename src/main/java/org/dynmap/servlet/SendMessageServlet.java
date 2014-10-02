package org.dynmap.servlet;

import static org.dynmap.JSONUtils.s;

import org.dynmap.DynmapCore;
import org.dynmap.Event;
import org.dynmap.Log;
import org.dynmap.SimpleWebChatComponent.ChatConfiguration;
import org.dynmap.web.HttpField;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@SuppressWarnings("serial")
public class SendMessageServlet extends HttpServlet {
    
    public final Event<Message> onMessageReceived = new Event<Message>();
    
    protected static final Logger log = Logger.getLogger("Minecraft");
    private static final Charset cs_utf8 = Charset.forName("UTF-8");
    
    private final DynmapCore core;
    private final ChatConfiguration chatConfig;
    private final int minMessageInterval;
    private final Set<String> proxyaddress = new HashSet<String>();
    
    private final HashMap<String, WebUser> disallowedUsers = new HashMap<String, WebUser>();
    private final LinkedList<WebUser> disallowedUserQueue = new LinkedList<WebUser>();
    private final Object disallowedUsersLock = new Object();
    private final HashMap<String,String> useralias = new HashMap<String,String>();
    private int aliasindex = 1;
    
    public SendMessageServlet(DynmapCore core, ChatConfiguration chatConfig) {
        this.core = core;
        this.chatConfig = chatConfig;
        this.minMessageInterval = (int)(chatConfig.webchatInterval * 1000);
        
        // Configure trusted proxies
        if (chatConfig.trustedproxies != null) {
            for(String s : chatConfig.trustedproxies) {
                this.proxyaddress.add(s.trim());
            }
        }
        else {
            this.proxyaddress.add("127.0.0.1");
            this.proxyaddress.add("0:0:0:0:0:0:0:1");
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        byte[] bytes;
        String error = "none";
        HttpSession sess = request.getSession(true);
        String userID = (String) sess.getAttribute(LoginServlet.USERID_ATTRIB);
        if(userID == null) userID = LoginServlet.USERID_GUEST;
        boolean chat_requires_login = core.getLoginRequired() || chatConfig.require_login;
        if(chat_requires_login && userID.equals(LoginServlet.USERID_GUEST)) {
            error = "login-required";
        }
        else if(chat_requires_login && (!userID.equals(LoginServlet.USERID_GUEST)) && chatConfig.chat_perm && 
                (!core.checkPermission(userID, "webchat"))) {
            Log.info("Rejected web chat by " + userID + ": not permitted");
            error = "not-permitted";
        }
        else {
            boolean ok = true;
            
            InputStreamReader reader = new InputStreamReader(request.getInputStream(), cs_utf8);

            JSONObject o = null;
            try {
                o = (JSONObject)new JSONParser().parse(reader);
            } catch (ParseException e) {
                error = "bad-format";
                ok = false;
            }

            final Message message = new Message();

            if (userID.equals(LoginServlet.USERID_GUEST)) {
                message.name = "";
                if (chatConfig.trust_client_name) {
                    message.name = String.valueOf(o.get("name"));
                }
                boolean isip = true;
                if ((message.name == null) || message.name.equals("")) {
                    /* If from trusted proxy, check for client */
                    String rmtaddr = request.getRemoteAddr(); 
                    if (this.proxyaddress.contains(rmtaddr)) {
                        /* If proxied client address, get original IP */
                        if (request.getHeader("X-Forwarded-For") != null) {
                            /* If trusted proxies were chained, we get next client address till non-trusted proxy met */
                            String[] proxyAddrs = request.getHeader("X-Forwarded-For").split(", ");
                            for(int i = proxyAddrs.length - 1; i >= 0; i--){
                                if (!this.proxyaddress.contains(proxyAddrs[i])) {
                                    /* use remaining addresses as name (maybe we can use the last or the first non-trusted one?) */
                                    message.name = proxyAddrs[0]; // 0 .. i
                                    for(int j = 1; j <= i; j++) message.name += ", " + proxyAddrs[j];
                                    break;
                                }
                            }
                        } else {
                            message.name = String.valueOf(o.get("name"));
                        }
                    }
                    else
                        message.name = request.getRemoteAddr();
                }
                
                if (chatConfig.use_player_login_ip) {
                    List<String> ids = core.getIDsForIP(message.name);
                    if (ids != null) {
                        String id = ids.get(0);
                        if (chatConfig.block_banned_player_chat) {
                            if (core.getServer().isPlayerBanned(id)) {
                                Log.info("Ignore message from '" + message.name + "' - banned player (" + id + ")");
                                error = "not-allowed";
                                ok = false;
                            }
                        }
                        if (chatConfig.chat_perm && !core.getServer().checkPlayerPermission(id, "webchat")) {
                            Log.info("Rejected web chat from '" + message.name + "': not permitted (" + id + ")");
                            error = "not-allowed";
                            ok = false;
                        }
                        message.name = id;
                        isip = false;
                    } else if (chatConfig.req_player_login_ip) {
                        Log.info("Ignore message from '" + message.name + "' - no matching player login recorded");
                        error = "not-allowed";
                        ok = false;
                    }
                }
                if (chatConfig.hidewebchatip && isip) { /* If hiding IP, find or assign alias */
                    synchronized (disallowedUsersLock) {
                        String n = useralias.get(message.name);
                        if (n == null) { /* Make ID */
                            n = String.format("web-%03d", aliasindex);
                            aliasindex++;
                            useralias.put(message.name, n);
                        }
                        message.name = n;
                    }
                }
            }
            else {
                message.name = userID;
            }
            message.message = String.valueOf(o.get("message"));
            if((chatConfig.length_limit > 0) && (message.message.length() > chatConfig.length_limit)) {
                message.message = message.message.substring(0, chatConfig.length_limit);
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

                WebUser user = disallowedUsers.get(message.name);
                if (user == null) {
                    user = new WebUser() {
                        {
                            name = message.name;
                            nextMessageTime = now + minMessageInterval;
                        }
                    };
                    disallowedUsers.put(user.name, user);
                    disallowedUserQueue.add(user);
                } else {
                    error = "not-allowed";
                    ok = false;
                }
            }
            if(ok)
                onMessageReceived.trigger(message);
        }
        JSONObject json = new JSONObject();
        s(json, "error", error);
        bytes = json.toJSONString().getBytes(cs_utf8);
        
        String dateStr = new Date().toString();
        response.addHeader(HttpField.Date, dateStr);
        response.addHeader(HttpField.ContentType, "text/plain; charset=utf-8");
        response.addHeader(HttpField.Expires, "Thu, 01 Dec 1994 16:00:00 GMT");
        response.addHeader(HttpField.LastModified, dateStr);
        response.addHeader(HttpField.ContentLength, Integer.toString(bytes.length));
        response.getOutputStream().write(bytes);
    }

    public static class Message {
        public String name;
        public String message;
    }
    public static class WebUser {
        public long nextMessageTime;
        public String name;
    }
}
