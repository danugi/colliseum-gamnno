package ru.colliseum.siege;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public final class EliteInventorySnapshot {
    private final ItemStack[] main = new ItemStack[36];
    private final ItemStack[] armor = new ItemStack[4];
    private ItemStack offhand = ItemStack.EMPTY;

    public static EliteInventorySnapshot capture(ServerPlayerEntity player) {
        EliteInventorySnapshot s = new EliteInventorySnapshot();
        for (int i = 0; i < 36; i++) s.main[i] = player.getInventory().getStack(i).copy();
        s.armor[0] = player.getEquippedStack(EquipmentSlot.FEET).copy();
        s.armor[1] = player.getEquippedStack(EquipmentSlot.LEGS).copy();
        s.armor[2] = player.getEquippedStack(EquipmentSlot.CHEST).copy();
        s.armor[3] = player.getEquippedStack(EquipmentSlot.HEAD).copy();
        s.offhand = player.getOffHandStack().copy();
        return s;
    }

    public void restore(ServerPlayerEntity player) {
        for (int i = 0; i < 36; i++) player.getInventory().setStack(i, main[i].copy());
        player.equipStack(EquipmentSlot.FEET, armor[0].copy());
        player.equipStack(EquipmentSlot.LEGS, armor[1].copy());
        player.equipStack(EquipmentSlot.CHEST, armor[2].copy());
        player.equipStack(EquipmentSlot.HEAD, armor[3].copy());
        player.getInventory().setStack(40, offhand.copy());
        player.currentScreenHandler.sendContentUpdates();
    }
}
