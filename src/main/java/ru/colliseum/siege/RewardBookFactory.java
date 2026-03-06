package ru.colliseum.siege;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

public final class RewardBookFactory {
    private RewardBookFactory() {}

    public static ItemStack create(MinecraftServer server, RewardBook type) {
        RegistryWrapper.Impl<Enchantment> enchantments = server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);

        return switch (type) {
            case BOOK_IV -> namedBook(enchantments, Text.literal("§bКнига IV"), pickBookIvKey(), 4);
            case MENDING -> namedBook(enchantments, Text.literal("§bMending"), Enchantments.MENDING, 1);
            case UNBREAKING_III -> namedBook(enchantments, Text.literal("§bUnbreaking III"), Enchantments.UNBREAKING, 3);
            case EFFICIENCY_V -> namedBook(enchantments, Text.literal("§bEfficiency V"), Enchantments.EFFICIENCY, 5);
            case SHARPNESS_V -> namedBook(enchantments, Text.literal("§bSharpness V"), Enchantments.SHARPNESS, 5);
            case PROTECTION_IV -> namedBook(enchantments, Text.literal("§bProtection IV"), Enchantments.PROTECTION, 4);
        };
    }

    private static RegistryKey<Enchantment> pickBookIvKey() {
        return Enchantments.PROTECTION;
    }

    private static ItemStack namedBook(
            RegistryWrapper.Impl<Enchantment> enchantments,
            Text name,
            RegistryKey<Enchantment> key,
            int level
    ) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);

        enchantments.getOptional(key).ifPresent(entry -> builder.add(entry, level));

        stack.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
        stack.set(DataComponentTypes.CUSTOM_NAME, name);
        return stack;
    }
}
