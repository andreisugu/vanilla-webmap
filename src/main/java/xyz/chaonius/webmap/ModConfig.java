package xyz.chaonius.webmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    public static final int CURRENT_CONFIG_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/vanilla-webmap.json");

    public int configVersion = CURRENT_CONFIG_VERSION;
    public String serverTitle = "Vanilla Web Map";
    public String serverIp = "play.myserver.com";
    public String headerLinkUrl = "";
    public int httpPort = 8105;
    public String bindAddress = "0.0.0.0";
    public int maxBackgroundTilesPerSecond = 8;
    public boolean pauseOnLowTps = true;
    public float maxMsptThreshold = 40.0f;
    public String tileCacheDir = "webmap_cache/tiles";
    public boolean showPlayerHealth = true;
    public boolean showCoordinates = true;
    public boolean debugLogging = false;

    private long lastModified = 0;

    public static ModConfig loadOrCreate() {
        ModConfig config = new ModConfig();
        if (!CONFIG_FILE.exists()) {
            config.save();
            System.out.println("[VanillaWebMap] Generated default configuration: " + CONFIG_FILE.getAbsolutePath());
            return config;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            int fileVersion = json.has("config_version") ? json.get("config_version").getAsInt() : 0;

            if (json.has("server_title")) config.serverTitle = json.get("server_title").getAsString();
            if (json.has("server_ip")) config.serverIp = json.get("server_ip").getAsString();
            if (json.has("header_link_url")) config.headerLinkUrl = json.get("header_link_url").getAsString();
            if (json.has("http_port")) config.httpPort = json.get("http_port").getAsInt();
            if (json.has("bind_address")) config.bindAddress = json.get("bind_address").getAsString();
            if (json.has("max_background_tiles_per_second")) config.maxBackgroundTilesPerSecond = json.get("max_background_tiles_per_second").getAsInt();
            if (json.has("pause_on_low_tps")) config.pauseOnLowTps = json.get("pause_on_low_tps").getAsBoolean();
            if (json.has("max_mspt_threshold")) config.maxMsptThreshold = json.get("max_mspt_threshold").getAsFloat();
            if (json.has("tile_cache_directory")) config.tileCacheDir = json.get("tile_cache_directory").getAsString();
            if (json.has("show_player_health")) config.showPlayerHealth = json.get("show_player_health").getAsBoolean();
            if (json.has("show_coordinates")) config.showCoordinates = json.get("show_coordinates").getAsBoolean();
            if (json.has("debug_logging")) config.debugLogging = json.get("debug_logging").getAsBoolean();

            config.lastModified = CONFIG_FILE.lastModified();

            if (fileVersion < CURRENT_CONFIG_VERSION) {
                System.out.println("[VanillaWebMap] Migrating config from v" + fileVersion + " -> v" + CURRENT_CONFIG_VERSION);
                config.configVersion = CURRENT_CONFIG_VERSION;
                config.save();
            } else {
                System.out.println("[VanillaWebMap] Loaded config (v" + config.configVersion + ") [Title: " + config.serverTitle + ", Port: " + config.httpPort + ", RateLimit: " + config.maxBackgroundTilesPerSecond + "/s]");
            }
        } catch (Exception e) {
            System.err.println("[VanillaWebMap-ERROR] Failed reading config, falling back to defaults: " + e.getMessage());
            config.save();
        }

        return config;
    }

    public void checkAndReload() {
        if (CONFIG_FILE.exists() && CONFIG_FILE.lastModified() > lastModified) {
            System.out.println("[VanillaWebMap] Detected modification in " + CONFIG_FILE.getName() + ", reloading settings live...");
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("server_title")) this.serverTitle = json.get("server_title").getAsString();
                if (json.has("server_ip")) this.serverIp = json.get("server_ip").getAsString();
                if (json.has("header_link_url")) this.headerLinkUrl = json.get("header_link_url").getAsString();
                if (json.has("max_background_tiles_per_second")) this.maxBackgroundTilesPerSecond = json.get("max_background_tiles_per_second").getAsInt();
                if (json.has("pause_on_low_tps")) this.pauseOnLowTps = json.get("pause_on_low_tps").getAsBoolean();
                if (json.has("max_mspt_threshold")) this.maxMsptThreshold = json.get("max_mspt_threshold").getAsFloat();
                if (json.has("show_player_health")) this.showPlayerHealth = json.get("show_player_health").getAsBoolean();
                if (json.has("show_coordinates")) this.showCoordinates = json.get("show_coordinates").getAsBoolean();
                if (json.has("debug_logging")) this.debugLogging = json.get("debug_logging").getAsBoolean();

                this.lastModified = CONFIG_FILE.lastModified();
                System.out.println("[VanillaWebMap] Live config reload complete [Title: " + this.serverTitle + ", RateLimit: " + this.maxBackgroundTilesPerSecond + "/s]");
            } catch (Exception e) {
                System.err.println("[VanillaWebMap-ERROR] Error reloading config: " + e.getMessage());
            }
        }
    }

    public void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            JsonObject json = new JsonObject();
            json.addProperty("config_version", CURRENT_CONFIG_VERSION);
            json.addProperty("server_title", serverTitle);
            json.addProperty("server_ip", serverIp);
            json.addProperty("header_link_url", headerLinkUrl);
            json.addProperty("http_port", httpPort);
            json.addProperty("bind_address", bindAddress);
            json.addProperty("max_background_tiles_per_second", maxBackgroundTilesPerSecond);
            json.addProperty("pause_on_low_tps", pauseOnLowTps);
            json.addProperty("max_mspt_threshold", maxMsptThreshold);
            json.addProperty("tile_cache_directory", tileCacheDir);
            json.addProperty("show_player_health", showPlayerHealth);
            json.addProperty("show_coordinates", showCoordinates);
            json.addProperty("debug_logging", debugLogging);

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(json, writer);
            }
            this.lastModified = CONFIG_FILE.lastModified();
        } catch (IOException e) {
            System.err.println("[VanillaWebMap-ERROR] Failed writing config file: " + e.getMessage());
        }
    }
}
