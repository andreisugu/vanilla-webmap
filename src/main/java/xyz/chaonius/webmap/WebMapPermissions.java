package xyz.chaonius.webmap;

import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;

import java.lang.reflect.Method;
import java.util.function.Predicate;

public final class WebMapPermissions {
    public static final String PERM_BASE = "vanillawebmap.command";
    public static final String PERM_STATUS = "vanillawebmap.command.status";
    public static final String PERM_HIDE = "vanillawebmap.command.hide";
    public static final String PERM_HIDE_OTHERS = "vanillawebmap.command.hide.others";
    public static final String PERM_SHOW = "vanillawebmap.command.show";
    public static final String PERM_SHOW_OTHERS = "vanillawebmap.command.show.others";
    public static final String PERM_RENDER = "vanillawebmap.command.render";
    public static final String PERM_RELOAD = "vanillawebmap.command.reload";
    public static final String PERM_CLEAR = "vanillawebmap.command.clear";
    public static final String PERM_ADMIN = "vanillawebmap.admin";

    private static final Method LUCK_CHECK_STACK;
    private static final boolean V1_PRESENT;

    static {
        Method mStack = null;
        try {
            Class<?> clazz = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            mStack = clazz.getMethod("check", CommandSourceStack.class, String.class, int.class);
        } catch (Throwable ignored) {
        }
        LUCK_CHECK_STACK = mStack;

        boolean v1 = false;
        try {
            Class.forName("net.fabricmc.fabric.api.permission.v1.PermissionPredicates");
            v1 = true;
        } catch (Throwable ignored) {
        }
        V1_PRESENT = v1;
    }

    private WebMapPermissions() {
    }

    public static Predicate<CommandSourceStack> require(String permission, PermissionLevel defaultLevel) {
        return source -> check(source, permission, defaultLevel);
    }

    public static boolean check(CommandSourceStack source, String permission, PermissionLevel defaultLevel) {
        if (source == null) {
            return false;
        }

        // Console / RCON always has full access
        if (source.getServer() != null && source.getEntity() == null) {
            return true;
        }

        // Master admin permission overrides all other checks
        if (!PERM_ADMIN.equals(permission) && checkInternal(source, PERM_ADMIN, PermissionLevel.GAMEMASTERS)) {
            return true;
        }

        return checkInternal(source, permission, defaultLevel);
    }

    @SuppressWarnings("unchecked")
    private static boolean checkInternal(CommandSourceStack source, String permission, PermissionLevel defaultLevel) {
        // 1. Check fabric-permissions-api-v0 (LuckPerms, FTB Ranks)
        if (LUCK_CHECK_STACK != null) {
            try {
                Object result = LUCK_CHECK_STACK.invoke(null, source, permission, defaultLevel.id());
                if (result instanceof Boolean b) {
                    return b;
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. Check fabric-permission-api-v1 (Fabric API)
        if (V1_PRESENT) {
            try {
                String path = permission.replace("vanillawebmap.", "").replace('.', '_');
                Identifier id = Identifier.fromNamespaceAndPath("vanillawebmap", path);
                Predicate<CommandSourceStack> pred = (Predicate<CommandSourceStack>) (Object) PermissionPredicates.require(id, defaultLevel);
                return pred.test(source);
            } catch (Throwable ignored) {
            }
        }

        // 3. Fallback to vanilla permission check
        return checkVanilla(source, defaultLevel);
    }

    public static boolean checkVanilla(CommandSourceStack source, PermissionLevel defaultLevel) {
        if (defaultLevel == PermissionLevel.ALL) {
            return true;
        }
        if (source.permissions() == null) {
            return false;
        }
        return switch (defaultLevel) {
            case ALL -> true;
            case MODERATORS -> Commands.LEVEL_MODERATORS.check(source.permissions());
            case GAMEMASTERS -> Commands.LEVEL_GAMEMASTERS.check(source.permissions());
            case ADMINS -> Commands.LEVEL_ADMINS.check(source.permissions());
            case OWNERS -> Commands.LEVEL_OWNERS.check(source.permissions());
        };
    }
}
