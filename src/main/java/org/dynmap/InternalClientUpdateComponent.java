package org.dynmap;

import java.util.concurrent.ConcurrentHashMap;

import org.dynmap.servlet.ClientUpdateServlet;
import org.json.simple.JSONObject;

public class InternalClientUpdateComponent extends ClientUpdateComponent {
    
    /**
     * Interval (in milliseconds) on which a client update snapshot should be gathered.
     */
    protected final long jsonInterval;
    protected long currentTimestamp = 0;
    protected long lastTimestamp = 0;
    protected long lastChatTimestamp = 0;
    private long last_confighash;
    private ConcurrentHashMap<String, JSONObject> updates = new ConcurrentHashMap<String, JSONObject>();
    private JSONObject clientConfiguration = null;
    private static InternalClientUpdateComponent singleton;
    
    public InternalClientUpdateComponent(final DynmapCore dcore, final ConfigurationNode configuration) {
        super(dcore, configuration);
        dcore.addServlet("/up/world/*", new ClientUpdateServlet(dcore));

        jsonInterval = (long)(configuration.getFloat("writeinterval", 1) * 1000);
        final long intervalTicks = jsonInterval / 50;
        
        core.getServer().scheduleServerTask(new Runnable() {
            @Override
            public void run() {
                currentTimestamp = System.currentTimeMillis();
                if(last_confighash != core.getConfigHashcode()) {
                    writeConfiguration();
                }
                writeUpdates();
                
                lastTimestamp = currentTimestamp;
                core.getServer().scheduleServerTask(this, intervalTicks);
            }}, intervalTicks);
        
        core.events.addListener("initialized", new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
            }
        });
        core.events.addListener("worldactivated", new Event.Listener<DynmapWorld>() {
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
