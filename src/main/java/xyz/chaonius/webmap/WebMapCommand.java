package xyz.chaonius.webmap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class WebMapCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("webmap")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes(ctx -> {
                sendHelp(ctx.getSource());
                return 1;
            })
            .then(Commands.literal("status")
                .executes(ctx -> {
                    sendStatus(ctx.getSource());
                    return 1;
                })
            )
            .then(Commands.literal("reload")
                .executes(ctx -> {
                    VanillaWebMapMod mod = VanillaWebMapMod.getInstance();
                    if (mod != null && mod.getConfig() != null) {
                        mod.getConfig().loadOrCreate();
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[VanillaWebMap] Config reloaded successfully!"), true);
                    return 1;
                })
            )
            .then(Commands.literal("clear")
                .executes(ctx -> {
                    VanillaWebMapMod mod = VanillaWebMapMod.getInstance();
                    if (mod != null && mod.getServerManager() != null) {
                        mod.getServerManager().getTileManager().clearCache();
                        ctx.getSource().sendSuccess(() -> Component.literal("§a[VanillaWebMap] Tile cache cleared across all dimensions!"), true);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("§c[VanillaWebMap] Server manager not ready yet."));
                    }
                    return 1;
                })
            )
            .then(Commands.literal("render")
                .executes(ctx -> {
                    return executeRender(ctx.getSource(), 12);
                })
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                    .executes(ctx -> {
                        int r = IntegerArgumentType.getInteger(ctx, "radius");
                        return executeRender(ctx.getSource(), r);
                    })
                )
            )
        );
    }

    private static void sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6=== §eVanillaWebMap Commands §6===\n" +
                "§e/webmap status §7- View live HTTP, cache, and multi-dimension stats\n" +
                "§e/webmap render [radius] §7- Force render chunks in current dimension\n" +
                "§e/webmap reload §7- Reload vanilla-webmap.json configuration\n" +
                "§e/webmap clear §7- Clear all cached map tiles"), false);
    }

    private static void sendStatus(CommandSourceStack source) {
        VanillaWebMapMod mod = VanillaWebMapMod.getInstance();
        if (mod == null || mod.getServerManager() == null || mod.getConfig() == null) {
            source.sendFailure(Component.literal("§c[VanillaWebMap] Mod is not fully initialized yet."));
            return;
        }

        HttpServerManager serverManager = mod.getServerManager();
        ModConfig config = mod.getConfig();
        BinaryTileManager mgr = serverManager.getTileManager();
        int overworldTiles = mgr.getDiskTileCount("overworld");
        int netherTiles = mgr.getDiskTileCount("the_nether");
        int endTiles = mgr.getDiskTileCount("the_end");
        int onlinePlayers = source.getServer().getPlayerCount();
        float mspt = source.getServer().getCurrentSmoothedTickTime();

        source.sendSuccess(() -> Component.literal(
                "§6=== §eVanillaWebMap Status §6===\n" +
                "§7• §fHTTP Server: §a" + config.bindAddress + ":" + config.httpPort + " §7(Online)\n" +
                "§7• §fOnline Players Tracked: §e" + onlinePlayers + "\n" +
                "§7• §fExplored Tiles: §a" + overworldTiles + " §7(Overworld) | §c" + netherTiles + " §7(Nether) | §d" + endTiles + " §7(End)\n" +
                "§7• §fServer MSPT: §e" + String.format("%.1f", mspt) + "ms\n" +
                "§7• §fWeb URL: §dhttps://map.192015145.xyz/smp/"), false);
    }

    private static int executeRender(CommandSourceStack source, int radius) {
        VanillaWebMapMod mod = VanillaWebMapMod.getInstance();
        if (mod == null || mod.getServerManager() == null) {
            source.sendFailure(Component.literal("§c[VanillaWebMap] Server manager not ready yet."));
            return 0;
        }

        HttpServerManager serverManager = mod.getServerManager();
        int centerX = 0, centerZ = 0;
        ServerLevel level = source.getLevel();
        if (source.getEntity() instanceof ServerPlayer player) {
            centerX = ((int) player.getX()) >> 4;
            centerZ = ((int) player.getZ()) >> 4;
            level = (ServerLevel) player.level();
        }

        final int cx = centerX;
        final int cz = centerZ;
        final String dimName = BinaryTileManager.normalizeDim(level.dimension().identifier().toString());
        source.sendSuccess(() -> Component.literal("§e[VanillaWebMap] Scanning and rendering radius " + radius + " chunks around [" + cx + ", " + cz + "] in §6" + dimName + "§e..."), true);

        long start = System.currentTimeMillis();
        int count = serverManager.getTileManager().forceRenderRadius(level, cx, cz, radius);
        long elapsed = System.currentTimeMillis() - start;

        source.sendSuccess(() -> Component.literal("§a[VanillaWebMap] Render complete! Cached " + count + " chunks in " + elapsed + "ms!"), true);
        return count;
    }
}
