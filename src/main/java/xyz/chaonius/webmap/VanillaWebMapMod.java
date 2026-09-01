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
        System.out.println("[VanillaWebMap] Initializing Multi-Dimension 8-Bit Vanilla Map Fabric Mod...");
        this.config = ModConfig.loadOrCreate();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            WebMapCommand.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverManager = new HttpServerManager(server, config);
            serverManager.start();
        });

        // 1. Capture newly loaded/generated chunks (0.005ms)
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, isNewChunk) -> {
            if (serverManager != null && level != null && chunk != null) {
                serverManager.getTileManager().onChunkLoad(level, chunk);
            }
        });

        // 2. Capture chunk unloads: catches distant TNT craters, quarries, and automated farm changes before leaving RAM
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            if (serverManager != null && level != null && chunk != null) {
                serverManager.getTileManager().onChunkUnload(level, chunk);
            }
        });

        // 3. Tick Loops: Active player proximity scanner & background spawn chunk check
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int interval = Math.max(10, config.scanIntervalTicks > 0 ? config.scanIntervalTicks : 40);
            if (++tickCounter % interval == 0) {
                if (serverManager != null) {
                    serverManager.getTileManager().scanActivePlayers(server);
                }
            }

            // Periodic spawn chunks / chunk loader scan (Every 30s = 600 ticks)
            if (tickCounter % 600 == 0) {
                if (serverManager != null) {
                    serverManager.getTileManager().scanSpawnChunks(server);
                }
            }

            if (tickCounter % 100 == 0) {
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
