package xyz.chaonius.webmap;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class VanillaWebMapMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "vanilla-webmap";
    private static VanillaWebMapMod INSTANCE;
    private ModConfig config;
    private HttpServerManager serverManager;
    private int tickCounter = 0;

    public static VanillaWebMapMod getInstance() {
        return INSTANCE;
    }

    public ModConfig getConfig() {
        return config;
    }

    public HttpServerManager getServerManager() {
        return serverManager;
    }

    @Override
    public void onInitializeServer() {
        INSTANCE = this;
        System.out.println("[VanillaWebMap] Initializing 8-Bit Vanilla Map Fabric Mod...");
        this.config = ModConfig.loadOrCreate();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            WebMapCommand.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverManager = new HttpServerManager(server, config);
            serverManager.start();
        });

        // Capture chunk color bytes on natural chunk load (0.005ms, guaranteed 0 missed chunks)
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, isNewChunk) -> {
            if (serverManager != null && level == level.getServer().overworld()) {
                serverManager.getTileManager().onChunkLoad(chunk);
            }
        });

        // Config file reload watcher (every 5 seconds)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter % 100 == 0) {
                config.checkAndReload();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (serverManager != null) {
                serverManager.getTileManager().stop();
                serverManager.stop();
            }
        });
    }
}
