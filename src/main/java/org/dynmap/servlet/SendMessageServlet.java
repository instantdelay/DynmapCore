package org.dynmap.servlet;

import static org.dynmap.JSONUtils.s;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.chat.ChatConfiguration;
import org.dynmap.chat.RawWebMessage;
import org.dynmap.chat.WebMessageEvent;
import org.dynmap.web.HttpField;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

@SuppressWarnings("serial")
public class SendMessageServlet extends HttpServlet {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private static final Charset cs_utf8 = Charset.forName("UTF-8");

    private final DynmapCore core;
    private final ChatConfiguration chatConfig;
    private final HashSet<String> proxyaddress = new HashSet<String>();

    public SendMessageServlet(DynmapCore core, ChatConfiguration chatConfig) {
        this.core = core;
        this.chatConfig = chatConfig;

        if(chatConfig.trustedproxies != null) {
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

        boolean chat_requires_login = core.getLoginRequired() || chatConfig.require_login;
        if(chat_requires_login && userID == null) {
            error = "login-required";
        }
        else if(chat_requires_login && userID != null && chatConfig.chat_perm && 
                (!core.checkPermission(userID, "webchat"))) {
            Log.info("Rejected web chat by " + userID + ": not permitted");
            error = "not-permitted";
        }
        else {
            String ip = request.getRemoteAddr();
            if (this.proxyaddress.contains(ip)) {
                /* If proxied client address, get original IP */
                if (request.getHeader("X-Forwarded-For") != null) {
                    /* If trusted proxies were chained, we get next client address till non-trusted proxy met */
                    String[] proxyAddrs = request.getHeader("X-Forwarded-For").split(", ");
                    for(int i = proxyAddrs.length - 1; i >= 0; i--){
                        if (!this.proxyaddress.contains(proxyAddrs[i])) {
                            /* use remaining addresses as name (maybe we can use the last or the first non-trusted one?) */
                            ip = proxyAddrs[0]; // 0 .. i
                            for(int j = 1; j <= i; j++) ip += ", " + proxyAddrs[j];
                            break;
                        }
                    }
                }
            }

            try (InputStreamReader reader = new InputStreamReader(request.getInputStream(), cs_utf8)) {
                JSONObject o = (JSONObject)new JSONParser().parse(reader);

                RawWebMessage rmsg = new RawWebMessage();
                rmsg.setProvidedName(Objects.toString(o.get("name"), null));
                rmsg.setIp(ip);
                rmsg.setSessionUsername(userID);
                rmsg.setMessage(Objects.toString(o.get("message"), ""));
                rmsg.setTimestamp(System.currentTimeMillis());

                WebMessageEvent event = new WebMessageEvent(rmsg);
                core.events.trigger("webmessage", event);

                if (event.getResult() != null) {
                    error = event.getResult();
                }
            }
            catch (ParseException e) {
                error = "bad-format";
            }
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

}
