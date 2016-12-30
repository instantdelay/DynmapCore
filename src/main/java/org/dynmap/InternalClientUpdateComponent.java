package org.dynmap;

import static org.dynmap.JSONUtils.s;

import java.util.concurrent.ConcurrentHashMap;

import org.dynmap.chat.ChatConfiguration;
import org.dynmap.servlet.ClientUpdateServlet;
import org.dynmap.servlet.SendMessageServlet;
import org.json.simple.JSONObject;

public class InternalClientUpdateComponent extends ClientUpdateComponent {
    protected long jsonInterval;
    protected long currentTimestamp = 0;
    protected long lastTimestamp = 0;
    private long last_confighash;
    private ConcurrentHashMap<String, JSONObject> updates = new ConcurrentHashMap<String, JSONObject>();
    private JSONObject clientConfiguration = null;
    private static InternalClientUpdateComponent singleton;
    
    public InternalClientUpdateComponent(final DynmapCore dcore, final ConfigurationNode configuration) {
        super(dcore, configuration);
        dcore.addServlet("/up/world/*", new ClientUpdateServlet(dcore));

        jsonInterval = (long)(configuration.getFloat("writeinterval", 1) * 1000);
        final ChatConfiguration chatConfig = new ChatConfiguration(configuration);

        dcore.events.addListener(InternalEvents.BUILD_CLIENT_CONFIG, new Event.Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                s(t, "allowwebchat", chatConfig.allowWebChat);
                s(t, "webchat-interval", chatConfig.webchatInterval);
                s(t, "webchat-requires-login", chatConfig.require_login);
                s(t, "chatlengthlimit", chatConfig.length_limit);
            }
        });

        if (chatConfig.allowWebChat) {
            @SuppressWarnings("serial")
            SendMessageServlet messageHandler = new SendMessageServlet(core, chatConfig) {{
                onMessageReceived.addListener(new Event.Listener<Message> () {
                    @Override
                    public void triggered(Message t) {
                        core.webChat(t.name, t.message);
                    }
                });
            }};
            dcore.addServlet("/up/sendmessage", messageHandler);
        }
        core.getServer().scheduleServerTask(new Runnable() {
            @Override
            public void run() {
                currentTimestamp = System.currentTimeMillis();
                if(last_confighash != core.getConfigHashcode()) {
                    writeConfiguration();
                }
                writeUpdates();
                lastTimestamp = currentTimestamp;
                core.getServer().scheduleServerTask(this, jsonInterval/50);
            }}, jsonInterval/50);
        
        core.events.addListener(InternalEvents.INITIALIZED, new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
            }
        });
        core.events.addListener(InternalEvents.WORLD_ACTIVATED, new Event.Listener<DynmapWorld>() {
            @Override
            public void triggered(DynmapWorld t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
            }
        });

        /* Initialize */
        writeConfiguration();
        writeUpdates();
        
        singleton = this;
    }
    @SuppressWarnings("unchecked")
    protected void writeUpdates() {
        if(core.mapManager == null) return;
        //Handles Updates
        for (DynmapWorld dynmapWorld : core.mapManager.getWorlds()) {
            JSONObject update = new JSONObject();
            update.put("timestamp", currentTimestamp);
            ClientUpdateEvent clientUpdate = new ClientUpdateEvent(currentTimestamp - 30000, dynmapWorld, update);
            clientUpdate.include_all_users = true;
            core.events.trigger(InternalEvents.BUILD_CLIENT_UPDATE, clientUpdate);

            updates.put(dynmapWorld.getName(), update);
        }
    }
    protected void writeConfiguration() {
        JSONObject clientConfiguration = new JSONObject();
        core.events.trigger(InternalEvents.BUILD_CLIENT_CONFIG, clientConfiguration);
        this.clientConfiguration = clientConfiguration;
        last_confighash = core.getConfigHashcode();
    }
    public static JSONObject getWorldUpdate(String wname) {
        if(singleton != null) {
            return singleton.updates.get(wname);
        }
        return null;
    }
    public static JSONObject getClientConfig() {
        if(singleton != null)
            return singleton.clientConfiguration;
        return null;
    }
}
