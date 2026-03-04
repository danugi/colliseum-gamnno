package ru.colliseum.siege;

import net.minecraft.server.command.ServerCommandSource;

public final class ModPermissions {
    private ModPermissions() {}

    public static boolean hasUse(ServerCommandSource source) {
        return source.getEntity() != null;
    }

    public static boolean hasAdmin(ServerCommandSource source) {
        return true;
    }
}
