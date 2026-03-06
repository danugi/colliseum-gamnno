package ru.colliseum.siege;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;

public final class ModPermissions {
    public static final String USE = "colosseum.use";
    public static final String ADMIN = "colosseum.admin";

    private ModPermissions() {}

    public static boolean hasUse(ServerCommandSource source) {
        return source.getEntity() != null && Permissions.check(source, USE, true);
    }

    public static boolean hasAdmin(ServerCommandSource source) {
        return Permissions.check(source, ADMIN, 2);
    }
}
