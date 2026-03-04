package ru.colliseum.siege;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public final class EliteInventorySnapshot {
    private final ItemStack[] main = new ItemStack[36];
    private final ItemStack[] armor = new ItemStack[4];
    private ItemStack offhand = ItemStack.EMPTY;

    public static EliteInventorySnapshot capture(ServerPlayerEntity player) {
        EliteInventorySnapshot s = new EliteInventorySnapshot();
        for (int i = 0; i < 36; i++) s.main[i] = player.getInventory().getStack(i).copy();
        for (int i = 0; i < 4; i++) s.armor[i] = player.getInventory().armor.get(i).copy();
        s.offhand = player.getInventory().offHand.get(0).copy();
        return s;
    }

    public void restore(ServerPlayerEntity player) {
        for (int i = 0; i < 36; i++) player.getInventory().setStack(i, main[i].copy());
        for (int i = 0; i < 4; i++) player.getInventory().armor.set(i, armor[i].copy());
        player.getInventory().offHand.set(0, offhand.copy());
        player.currentScreenHandler.sendContentUpdates();
    }
}
