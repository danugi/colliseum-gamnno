package ru.colliseum.siege;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class ArenaData {
    public String name;
    public String world = "minecraft:overworld";
    public Vec3d center;
    public double radius = 30.0;
    public Vec3d entry;
    public Vec3d exit;
    public Vec3d lobby;
    public int maxPlayers = 5;
    public final List<Vec3d> spawns = new ArrayList<>();

    public RegistryKey<World> worldKey() {
        return RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, Identifier.of(world));
    }

    public BlockPos centerPos() {
        return BlockPos.ofFloored(center);
    }

    public boolean isReady() {
        return center != null && entry != null && exit != null && lobby != null && !spawns.isEmpty() && maxPlayers >= 1;
    }

    public boolean isInside(ServerWorld worldObj, Vec3d pos) {
        if (center == null) return false;
        if (!worldObj.getRegistryKey().getValue().toString().equals(world)) return false;
        return pos.squaredDistanceTo(center) <= radius * radius;
    }
}
