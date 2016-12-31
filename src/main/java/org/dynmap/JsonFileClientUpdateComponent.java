package org.dynmap;

import static org.dynmap.JSONUtils.s;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;

import org.dynmap.chat.ChatConfiguration;
import org.dynmap.chat.RawWebMessage;
import org.dynmap.chat.WebMessageEvent;
import org.dynmap.storage.MapStorage;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.web.Json;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class JsonFileClientUpdateComponent extends ClientUpdateComponent {
    protected long jsonInterval;
    protected long currentTimestamp = 0;
    protected long lastTimestamp = 0;
    protected long lastChatTimestamp = 0;
    private final ChatConfiguration chatConfig;
    private long last_confighash;
    private MessageDigest md;
    private MapStorage storage;
    private File baseStandaloneDir;

    private static class FileToWrite {
        String filename;
        byte[] content;
        boolean phpwrapper;
        @Override
        public boolean equals(Object o) {
            if(o instanceof FileToWrite) {
                return ((FileToWrite)o).filename.equals(this.filename);
            }
            return false;
        }
    }
    private class FileProcessor implements Runnable {
        @Override
        public void run() {
            while(true) {
                FileToWrite f = null;
                synchronized(lock) {
                    if(files_to_write.isEmpty() == false) {
                        f = files_to_write.removeFirst();
                    }
                    else {
                        pending = null;
                        return;
                    }
                }
                BufferOutputStream buf = null;
                if (f.content != null) {
                    buf = new BufferOutputStream();
                    if(f.phpwrapper) {
                        buf.write("<?php /*\n".getBytes(cs_utf8));
                    }
                    buf.write(f.content);
                    if(f.phpwrapper) {
                        buf.write("\n*/ ?>\n".getBytes(cs_utf8));
                    }
                }
                if (!storage.setStandaloneFile(f.filename, buf)) {
                    Log.severe("Exception while writing JSON-file - " + f.filename);
                }
            }
        }
    }
    private Object lock = new Object();
    private FileProcessor pending;
    private LinkedList<FileToWrite> files_to_write = new LinkedList<FileToWrite>();

    private void enqueueFileWrite(String filename, byte[] content, boolean phpwrap) {
        FileToWrite ftw = new FileToWrite();
        ftw.filename = filename;
        ftw.content = content;
        ftw.phpwrapper = phpwrap;
        synchronized(lock) {
            boolean didadd = false;
            if(pending == null) {
                didadd = true;
                pending = new FileProcessor();
            }
            files_to_write.remove(ftw);
            files_to_write.add(ftw);
            if(didadd) {
                MapManager.scheduleDelayedJob(new FileProcessor(), 0);
            }
        }
    }
    
    private static Charset cs_utf8 = Charset.forName("UTF-8");
    public JsonFileClientUpdateComponent(final DynmapCore core, final ConfigurationNode configuration) {
        super(core, configuration);
        this.chatConfig = new ChatConfiguration(configuration);
        jsonInterval = (long)(configuration.getFloat("writeinterval", 1) * 1000);
        storage = core.getDefaultMapStorage();
        baseStandaloneDir = new File(core.configuration.getString("webpath", "web"), "standalone");
        if (!baseStandaloneDir.isAbsolute()) {
            baseStandaloneDir = new File(core.getDataFolder(), baseStandaloneDir.toString());
        }
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException nsax) {
            Log.severe("Unable to get message digest SHA-1");
        }
        /* Generate our config.js file */
        generateConfigJS(core);
        
        core.getServer().scheduleServerTask(new Runnable() {
            @Override
            public void run() {
                currentTimestamp = System.currentTimeMillis();
                if(last_confighash != core.getConfigHashcode()) {
                    writeConfiguration();
                }
                writeUpdates();
                if (chatConfig.allowWebChat) {
                    handleWebChat();
                }
                if(core.isLoginSupportEnabled())
                    handleRegister();
                lastTimestamp = currentTimestamp;
                core.getServer().scheduleServerTask(this, jsonInterval/50);
            }}, jsonInterval/50);
        
        core.events.addListener(InternalEvents.BUILD_CLIENT_CONFIG, new Event.Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                s(t, "jsonfile", true);
                s(t, "allowwebchat", chatConfig.allowWebChat);
                s(t, "webchat-requires-login", chatConfig.require_login);
                s(t, "loginrequired", core.isLoginRequired());
                // For 'sendmessage.php'
                s(t, "webchat-interval", configuration.getFloat("webchat-interval", 5.0f));
                s(t, "chatlengthlimit", chatConfig.length_limit);
            }
        });
        core.events.addListener(InternalEvents.INITIALIZED, new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
                writeLogins();
                writeAccess();
            }
        });
        core.events.addListener(InternalEvents.SERVER_STARTED, new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
                writeLogins();
                writeAccess();
            }
        });
        core.events.addListener(InternalEvents.WORLD_ACTIVATED, new Event.Listener<DynmapWorld>() {
            @Override
            public void triggered(DynmapWorld t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
                writeAccess();
            }
        });
        core.events.addListener(InternalEvents.LOGIN_UPDATED, new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeLogins();
                writeAccess();
            }
        });
        core.events.addListener(InternalEvents.PLAYER_SET_UPDATED, new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeAccess();
            }
        });
    }
        
    private void generateConfigJS(DynmapCore core) {
        /* Test if login support is enabled */
        boolean login_enabled = core.isLoginSupportEnabled();

        // configuration: 'standalone/dynmap_config.json?_={timestamp}',
        // update: 'standalone/dynmap_{world}.json?_={timestamp}',
        // sendmessage: 'standalone/sendmessage.php',
        // login: 'standalone/login.php',
        // register: 'standalone/register.php',
        // tiles : 'tiles/',
        // markers : 'tiles/'

        // configuration: 'standalone/configuration.php',
        // update: 'standalone/update.php?world={world}&ts={timestamp}',
        // sendmessage: 'standalone/sendmessage.php',
        // login: 'standalone/login.php',
        // register: 'standalone/register.php',
        // tiles : 'standalone/tiles.php?tile=',
        // markers : 'standalone/markers.php?marker='
        
        MapStorage store = core.getDefaultMapStorage();
        
        StringBuilder sb = new StringBuilder();
        sb.append("var config = {\n");
        sb.append(" url : {\n");
        /* Get configuration URL */
        sb.append("  configuration: '");
        sb.append(core.configuration.getString("url/configuration", store.getConfigurationJSONURI(login_enabled)));
        sb.append("',\n");
        /* Get update URL */
        sb.append("  update: '");
        sb.append(core.configuration.getString("url/update", store.getUpdateJSONURI(login_enabled)));
        sb.append("',\n");
        /* Get sendmessage URL */
        sb.append("  sendmessage: '");
        sb.append(core.configuration.getString("url/sendmessage", store.getSendMessageURI()));
        sb.append("',\n");
        /* Get login URL */
        sb.append("  login: '");
        sb.append(core.configuration.getString("url/login", store.getStandaloneLoginURI()));
        sb.append("',\n");
        /* Get register URL */
        sb.append("  register: '");
        sb.append(core.configuration.getString("url/register", store.getStandaloneRegisterURI()));
        sb.append("',\n");
        /* Get tiles URL */
        sb.append("  tiles: '");
        sb.append(core.configuration.getString("url/tiles", store.getTilesURI(login_enabled)));
        sb.append("',\n");
        /* Get markers URL */
        sb.append("  markers: '");
        sb.append(core.configuration.getString("url/markers", store.getMarkersURI(login_enabled)));
        sb.append("'\n }\n};\n");
        
        byte[] outputBytes = sb.toString().getBytes(cs_utf8);
        File f = new File(baseStandaloneDir, "config.js");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            fos.write(outputBytes);
        } catch (IOException iox) {
            Log.severe("Exception while writing " + f.getPath(), iox);
        } finally {
            if(fos != null) {
                try {
                    fos.close();
                } catch (IOException x) {}
                fos = null;
            }
        }
    }
    
    protected void writeConfiguration() {
        JSONObject clientConfiguration = new JSONObject();
        core.events.trigger(InternalEvents.BUILD_CLIENT_CONFIG, clientConfiguration);
        last_confighash = core.getConfigHashcode();
        
        byte[] content = clientConfiguration.toJSONString().getBytes(cs_utf8);

        String outputFile;
        boolean dowrap = storage.wrapStandaloneJSON(core.isLoginSupportEnabled());
        if(dowrap) {
            outputFile = "dynmap_config.php";
        }
        else {
            outputFile = "dynmap_config.json";
        }
        
        enqueueFileWrite(outputFile, content, dowrap);
    }
    
    @SuppressWarnings("unchecked")
    protected void writeUpdates() {
        if(core.mapManager == null) return;
        //Handles Updates
        ArrayList<DynmapWorld> wlist = new ArrayList<DynmapWorld>(core.mapManager.getWorlds());	// Grab copy of world list
        for (int windx = 0; windx < wlist.size(); windx++) {
        	DynmapWorld dynmapWorld = wlist.get(windx);
            JSONObject update = new JSONObject();
            update.put("timestamp", currentTimestamp);
            ClientUpdateEvent clientUpdate = new ClientUpdateEvent(currentTimestamp - 30000, dynmapWorld, update);
            clientUpdate.include_all_users = true;
            core.events.trigger(InternalEvents.BUILD_CLIENT_UPDATE, clientUpdate);

            String outputFile;
            boolean dowrap = storage.wrapStandaloneJSON(core.isLoginSupportEnabled());
            if(dowrap) {
                outputFile = "updates_" + dynmapWorld.getName() + ".php";
            }
            else {
                outputFile = "dynmap_" + dynmapWorld.getName() + ".json";
            }
            byte[] content = Json.stringifyJson(update).getBytes(cs_utf8);

            enqueueFileWrite(outputFile, content, dowrap);
        }
    }
    
    private byte[] loginhash = new byte[16];
    
    protected void writeLogins() {
        String loginFile = "dynmap_login.php";

        if(core.isLoginSupportEnabled()) {
            String s = core.getLoginPHP(storage.wrapStandalonePHP());
            if(s != null) {
                byte[] bytes = s.getBytes(cs_utf8);
                md.reset();
                byte[] hash = md.digest(bytes);
                if(Arrays.equals(hash, loginhash)) {
                    return;
                }
                enqueueFileWrite(loginFile, bytes, false);
                loginhash = hash;
            }
        }
        else {
            enqueueFileWrite(loginFile, null, false);
        }
    }

    private byte[] accesshash = new byte[16];

    protected void writeAccess() {
        String accessFile = "dynmap_access.php";

        String s = core.getAccessPHP(storage.wrapStandalonePHP());
        if(s != null) {
            byte[] bytes = s.getBytes(cs_utf8);
            md.reset();
            byte[] hash = md.digest(bytes);
            if(Arrays.equals(hash, accesshash)) {
                return;
            }
            enqueueFileWrite(accessFile, bytes, false);
            accesshash = hash;
        }
    }

    protected void handleWebChat() {
        BufferInputStream bis = storage.getStandaloneFile("dynmap_webchat.json");
        if (bis != null && lastTimestamp != 0) {
            JSONArray jsonMsgs = null;
            Reader inputFileReader = null;
            try {
                inputFileReader = new InputStreamReader(bis, cs_utf8);
                jsonMsgs = (JSONArray) new JSONParser().parse(inputFileReader);
            } catch (IOException ex) {
                Log.severe("Exception while reading JSON-file.", ex);
            } catch (ParseException ex) {
                Log.severe("Exception while parsing JSON-file.", ex);
            } finally {
                if(inputFileReader != null) {
                    try {
                        inputFileReader.close();
                    } catch (IOException iox) {
                        
                    }
                    inputFileReader = null;
                }
            }

            if (jsonMsgs != null) {
                Iterator<?> iter = jsonMsgs.iterator();
                boolean init_skip = (lastChatTimestamp == 0);
                while (iter.hasNext()) {
                    JSONObject o = (JSONObject) iter.next();
                    long messageTimestamp = tryParse(Objects.toString(o.get("timestamp"), "0"));

                    if (messageTimestamp <= lastChatTimestamp) {
                        // Skip old messages
                        continue;
                    }
                    lastChatTimestamp = messageTimestamp;

                    if (init_skip)
                        continue;

                    RawWebMessage rmsg = new RawWebMessage();
                    rmsg.setProvidedName(Objects.toString(o.get("name"), null));
                    rmsg.setIp(Objects.toString(o.get("ip"), null));
                    rmsg.setSessionUsername(Objects.toString(o.get("userid"), null));
                    rmsg.setMessage(Objects.toString(o.get("message"), ""));
                    rmsg.setTimestamp(messageTimestamp);
                    core.events.trigger("webmessage", new WebMessageEvent(rmsg));
                }
            }
        }
    }

    private long tryParse(String ts) {
        long messageTimestamp;
        try {
            messageTimestamp = Long.parseLong(ts);
        } catch (NumberFormatException nfx) {
            try {
                messageTimestamp = (long) Double.parseDouble(ts);
            } catch (NumberFormatException nfx2) {
                messageTimestamp = 0;
            }
        }
        return messageTimestamp;
    }
    protected void handleRegister() {
        if(core.pendingRegisters() == false)
            return;
        BufferInputStream bis = storage.getStandaloneFile("dynmap_reg.php");
        if (bis != null) {
            BufferedReader br = null;
            ArrayList<String> lines = new ArrayList<String>();
            try {
                br = new BufferedReader(new InputStreamReader(bis));
                String line;
                while ((line = br.readLine()) != null)   {
                    if(line.startsWith("<?") || line.startsWith("*/")) {
                        continue;
                    }
                    lines.add(line);
                }
            } catch (IOException iox) {
                Log.severe("Exception while reading dynmap_reg.php", iox);
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException x) {
                    }
                    br = null;
                }
            }
            for(int i = 0; i < lines.size(); i++) {
                String[] vals = lines.get(i).split("=");
                if(vals.length == 3) {
                    core.processCompletedRegister(vals[0].trim(), vals[1].trim(), vals[2].trim());
                }
            }
        }
    }
    
    @Override
    public void dispose() {
        super.dispose();
    }
}
