package ru.colliseum.siege;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;

public final class ModPermissions {
    private ModPermissions() {}

    public static boolean hasUse(ServerCommandSource source) {
        return Permissions.check(source, "colosseum.use", true);
    }

    public static boolean hasAdmin(ServerCommandSource source) {
        return Permissions.check(source, "colosseum.admin", source.hasPermissionLevel(2));
    }
}
