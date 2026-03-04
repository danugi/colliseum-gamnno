package ru.colliseum.siege;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomData;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RewardService {
    private final Random random = new Random();

    public void giveVictoryRewards(ServerPlayerEntity player, Difficulty d) {
        List<ItemStack> rewards = new ArrayList<>();
        switch (d) {
            case EASY -> { rewards.add(new ItemStack(Items.GOLD_INGOT, roll(1, 2))); if (chance(10)) rewards.add(book(randomIv())); }
            case MEDIUM -> {
                rewards.add(new ItemStack(Items.DIAMOND, roll(1, 2)));
                rewards.add(new ItemStack(Items.GOLD_INGOT, roll(6, 10)));
                rewards.add(new ItemStack(Items.GOLDEN_CARROT, roll(12, 16)));
                if (chance(40)) rewards.add(new ItemStack(Items.ANCIENT_DEBRIS, 1));
                if (chance(20)) rewards.add(new ItemStack(Items.GOLDEN_APPLE, 1));
                if (chance(25)) rewards.add(book(randomIv()));
            }
            case HARD -> {
                rewards.add(new ItemStack(Items.DIAMOND, roll(2, 4)));
                rewards.add(new ItemStack(Items.GOLD_INGOT, roll(10, 16)));
                rewards.add(new ItemStack(Items.GOLDEN_CARROT, roll(16, 24)));
                rewards.add(new ItemStack(Items.ANCIENT_DEBRIS, 1));
                rewards.add(seal(1));
                if (chance(50)) rewards.add(new ItemStack(Items.ANCIENT_DEBRIS, 1));
                if (chance(35)) rewards.add(book("minecraft:mending", 1));
                if (chance(30)) rewards.add(book("minecraft:unbreaking", 3));
                if (chance(25)) rewards.add(book("minecraft:efficiency", 5));
                if (chance(25)) rewards.add(book("minecraft:sharpness", 5));
                if (chance(20)) rewards.add(new ItemStack(Items.GOLDEN_APPLE, 1));
                if (chance(25)) rewards.add(new ItemStack(Items.TOTEM_OF_UNDYING, 1));
            }
            case HARDCORE -> {
                rewards.add(new ItemStack(Items.DIAMOND, roll(4, 6)));
                rewards.add(new ItemStack(Items.GOLD_BLOCK, 1));
                rewards.add(new ItemStack(Items.GOLDEN_CARROT, roll(24, 32)));
                rewards.add(new ItemStack(Items.ANCIENT_DEBRIS, 4));
                rewards.add(seal(2));
                rewards.add(book("minecraft:mending", 1));
                if (chance(50)) rewards.add(new ItemStack(Items.TOTEM_OF_UNDYING, 1));
                if (chance(40)) rewards.add(book("minecraft:mending", 1));
                if (chance(35)) rewards.add(book("minecraft:sharpness", 5));
                if (chance(35)) rewards.add(book("minecraft:protection", 4));
                if (chance(30)) rewards.add(book("minecraft:efficiency", 5));
                if (chance(25)) rewards.add(book("minecraft:unbreaking", 3));
                if (chance(25)) rewards.add(new ItemStack(Items.GOLDEN_APPLE, 1));
            }
        }
        rewards.forEach(player::giveItemStack);
    }

    public static boolean hasSeal(ItemStack stack) {
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, CustomData.EMPTY).copyNbt();
        return nbt.getBoolean("colosseum_seal_applied");
    }

    public static ItemStack applySealTo(ItemStack target, String effectId) {
        ItemStack out = target.copy();
        NbtCompound nbt = out.getOrDefault(DataComponentTypes.CUSTOM_DATA, CustomData.EMPTY).copyNbt();
        nbt.putBoolean("colosseum_seal_applied", true);
        nbt.putString("colosseum_seal_effect", effectId);
        out.set(DataComponentTypes.CUSTOM_DATA, CustomData.of(nbt));
        return out;
    }

    private ItemStack seal(int count) {
        ItemStack seal = new ItemStack(Items.NETHER_STAR, count);
        seal.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6Печать Колизея"));
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("colosseum_seal_item", true);
        seal.set(DataComponentTypes.CUSTOM_DATA, CustomData.of(nbt));
        return seal;
    }

    private ItemStack book(String id, int lvl) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        var reg = net.minecraft.registry.Registries.ENCHANTMENT.getEntry(net.minecraft.util.Identifier.of(id));
        reg.ifPresent(entry -> EnchantmentHelper.set(book, java.util.Map.of(entry, lvl)));
        return book;
    }

    private ItemStack book(RegistryEntry<Enchantment> e, int lvl) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.set(book, java.util.Map.of(e, lvl));
        return book;
    }

    private ItemStack book(RegistryEntry<Enchantment> e) { return book(e, 4); }

    private RegistryEntry<Enchantment> randomIv() {
        List<String> ids = List.of("minecraft:protection", "minecraft:sharpness", "minecraft:power", "minecraft:efficiency");
        var id = net.minecraft.util.Identifier.of(ids.get(random.nextInt(ids.size())));
        return net.minecraft.registry.Registries.ENCHANTMENT.getEntry(id).orElseThrow();
    }

    private int roll(int min, int max) { return min + random.nextInt(max - min + 1); }
    private boolean chance(int pct) { return random.nextInt(100) < pct; }
}
