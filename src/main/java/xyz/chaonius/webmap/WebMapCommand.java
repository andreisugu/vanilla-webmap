package xyz.chaonius.webmap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

public class WebMapCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("webmap")
            .requires(WebMapPermissions.require(WebMapPermissions.PERM_BASE, PermissionLevel.ALL))
            .executes(ctx -> {
                sendHelp(ctx.getSource());
                return 1;
            })
            .then(Commands.literal("status")
                .requires(WebMapPermissions.require(WebMapPermissions.PERM_STATUS, PermissionLevel.ALL))
                .executes(ctx -> {
                    sendStatus(ctx.getSource());
                    return 1;
                })
            )
            .then(Commands.literal("hide")
                .requires(WebMapPermissions.require(WebMapPermissions.PERM_HIDE, PermissionLevel.ALL))
                .executes(ctx -> executeHideSelf(ctx.getSource()))
                .then(Commands.argument("target", EntityArgument.player())
                    .requires(WebMapPermissions.require(WebMapPermissions.PERM_HIDE_OTHERS, PermissionLevel.GAMEMASTERS))
                    .executes(ctx -> executeHideOther(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))
                )
            )
            .then(Commands.literal("show")
                .requires(WebMapPermissions.require(WebMapPermissions.PERM_SHOW, PermissionLevel.ALL))
                .executes(ctx -> executeShowSelf(ctx.getSource()))
                .then(Commands.argument("target", EntityArgument.player())
                    .requires(WebMapPermissions.require(WebMapPermissions.PERM_SHOW_OTHERS, PermissionLevel.GAMEMASTERS))
                    .executes(ctx -> executeShowOther(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))
                )
            )
            .then(Commands.literal("reload")
                .requires(WebMapPermissions.require(WebMapPermissions.PERM_RELOAD, PermissionLevel.GAMEMASTERS))
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
                .requires(WebMapPermissions.require(WebMapPermissions.PERM_CLEAR, PermissionLevel.GAMEMASTERS))
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
                .requires(WebMapPermissions.require(WebMapPermissions.PERM_RENDER, PermissionLevel.GAMEMASTERS))
                .executes(ctx -> executeRender(ctx.getSource(), 12))
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
        StringBuilder sb = new StringBuilder("§6=== §eVanillaWebMap Commands §6===\n");
        boolean any = false;

        if (WebMapPermissions.check(source, WebMapPermissions.PERM_STATUS, PermissionLevel.ALL)) {
            sb.append("§e/webmap status §7- View live HTTP, cache, and multi-dimension stats\n");
            any = true;
        }
        if (WebMapPermissions.check(source, WebMapPermissions.PERM_HIDE, PermissionLevel.ALL)) {
            sb.append("§e/webmap hide [player] §7- Hide player from the live web map\n");
            any = true;
        }
        if (WebMapPermissions.check(source, WebMapPermissions.PERM_SHOW, PermissionLevel.ALL)) {
            sb.append("§e/webmap show [player] §7- Show player on the live web map\n");
            any = true;
        }
        if (WebMapPermissions.check(source, WebMapPermissions.PERM_RENDER, PermissionLevel.GAMEMASTERS)) {
            sb.append("§e/webmap render [radius] §7- Force render chunks in current dimension\n");
            any = true;
        }
        if (WebMapPermissions.check(source, WebMapPermissions.PERM_RELOAD, PermissionLevel.GAMEMASTERS)) {
            sb.append("§e/webmap reload §7- Reload vanilla-webmap.json configuration\n");
            any = true;
        }
        if (WebMapPermissions.check(source, WebMapPermissions.PERM_CLEAR, PermissionLevel.GAMEMASTERS)) {
            sb.append("§e/webmap clear §7- Clear all cached map tiles\n");
            any = true;
        }

        if (!any) {
            sb.append("§7You do not have permission to execute any VanillaWebMap commands.\n");
        }

        String result = sb.toString().trim();
        source.sendSuccess(() -> Component.literal(result), false);
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

        String webUrl;
        if (config.headerLinkUrl != null && !config.headerLinkUrl.isBlank()) {
            webUrl = config.headerLinkUrl;
        } else {
            String host = ("0.0.0.0".equals(config.bindAddress) || "::".equals(config.bindAddress)) ? "localhost" : config.bindAddress;
            webUrl = "http://" + host + ":" + config.httpPort + "/";
        }

        source.sendSuccess(() -> Component.literal(
                "§6=== §eVanillaWebMap Status §6===\n" +
                "§7• §fHTTP Server: §a" + config.bindAddress + ":" + config.httpPort + " §7(Online)\n" +
                "§7• §fOnline Players Tracked: §e" + onlinePlayers + "\n" +
                "§7• §fExplored Tiles: §a" + overworldTiles + " §7(Overworld) | §c" + netherTiles + " §7(Nether) | §d" + endTiles + " §7(End)\n" +
                "§7• §fServer MSPT: §e" + String.format("%.1f", mspt) + "ms\n" +
                "§7• §fWeb URL: §d" + webUrl), false);
    }

    private static int executeHideSelf(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§c[VanillaWebMap] Must specify a player: /webmap hide <player>"));
            return 0;
        }
        VanillaWebMapMod mod = VanillaWebMapMod.getInstance();
        if (mod == null) {
            source.sendFailure(Component.literal("§c[VanillaWebMap] Mod is not fully initialized yet."));
            return 0;
        }
        if (mod.isPlayerHidden(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§e[VanillaWebMap] You are already hidden from the web map."), false);
            return 1;
        }
        mod.setPlayerHidden(player.getUUID(), true);
        source.sendSuccess(() -> Component.literal("§a[VanillaWebMap] You are now hidden from the live web map."), false);
        return 1;
    }

    private static int executeShowSelf(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§c[VanillaWebMap] Must specify a player: /webmap show <player>"));
            return 0;
        }
        VanillaWebMapMod mod = VanillaWebMapMod.getInstance();
        if (mod == null) {
            source.sendFailure(Component.literal("§c[VanillaWebMap] Mod is not fully initialized yet."));
            return 0;
        }
        if (!mod.isPlayerHidden(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§e[VanillaWebMap] You are already visible on the web map."), false);
            return 1;
        }
        mod.setPlayerHidden(player.getUUID(), false);
        source.sendSuccess(() -> Component.literal("§a[VanillaWebMap] You are now visible on the live web map."), false);
        return 1;
    }

    private static int executeHideOther(CommandSourceStack source, ServerPlayer target) {
        VanillaWebMapMod mod = VanillaWebMapMod.getInstance();
        if (mod == null) {
            source.sendFailure(Component.literal("§c[VanillaWebMap] Mod is not fully initialized yet."));
            return 0;
        }
        mod.setPlayerHidden(target.getUUID(), true);
        source.sendSuccess(() -> Component.literal("§a[VanillaWebMap] " + target.getScoreboardName() + " is now hidden from the web map."), true);
        target.sendSystemMessage(Component.literal("§7[VanillaWebMap] You have been hidden from the web map by an administrator."));
        return 1;
    }

    private static int executeShowOther(CommandSourceStack source, ServerPlayer target) {
        VanillaWebMapMod mod = VanillaWebMapMod.getInstance();
        if (mod == null) {
            source.sendFailure(Component.literal("§c[VanillaWebMap] Mod is not fully initialized yet."));
            return 0;
        }
        mod.setPlayerHidden(target.getUUID(), false);
        source.sendSuccess(() -> Component.literal("§a[VanillaWebMap] " + target.getScoreboardName() + " is now visible on the web map."), true);
        target.sendSystemMessage(Component.literal("§7[VanillaWebMap] You are now visible on the web map."));
        return 1;
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
