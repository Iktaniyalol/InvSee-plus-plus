package com.janboerman.invsee.spigot.internal.placeholder;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class Placeholders {

    private Placeholders() {
    }

    public static final String INACCESSIBLE = ChatColor.DARK_RED + "";
    public static final String HELMET = ChatColor.AQUA + "Шлем";
    public static final String CHESTPLATE = ChatColor.AQUA + "Нагрудник";
    public static final String LEGGINGS = ChatColor.AQUA + "Поножи";
    public static final String BOOTS = ChatColor.AQUA + "Ботинки";
    public static final String OFFHAND = ChatColor.AQUA + "Вторая рука";
    public static final String CURSOR = ChatColor.AQUA + "Курсор";
    public static final String CRAFTING = ChatColor.AQUA + "Ингредиент верстака";
    public static final String ANVIL = ChatColor.AQUA + "Слот наковальни";
    public static final String MERCHANT = ChatColor.AQUA + "Оплата торговца";
    public static final String CARTOGRAPHY = ChatColor.AQUA + "Слот стола картографии";
    public static final String ENCHANTING_ITEM = ChatColor.AQUA + "Зачаровываемый предмет";
    public static final String ENCHANTING_FUEL = ChatColor.AQUA + "Лазурит для зачарования";
    public static final String GRINDSTONE = ChatColor.AQUA + "Слот точила";
    public static final String LOOM = ChatColor.AQUA + "Слот ткацкого станка";
    public static final String SMITHING_BASE = ChatColor.AQUA + "Ковка предмета";
    public static final String SMITHING_TEMPLATE = ChatColor.AQUA + "Ковочный шаблон";
    public static final String SMITHING_ADDITION = ChatColor.AQUA + "Дополнение для ковки";
    public static final String STONECUTTER = ChatColor.AQUA + "Слот камнереза";
    public static final String GENERIC = ChatColor.AQUA + "";

    public static final Consumer<? super ItemMeta> HIDE_ATTRIBUTES = meta -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

    public static <IM extends ItemMeta> ItemStack makeStack(Material material, Consumer<? super IM> itemMetaModifier) {
        ItemStack stack = new ItemStack(material);
        modifyStack(stack, itemMetaModifier);
        return stack;
    }

    public static <IM extends ItemMeta> ItemStack makeStack(Material material, byte dataValue, Consumer<? super IM> itemMetaModifier) {
        ItemStack stack = new ItemStack(material, 1, (short) 0, dataValue);
        modifyStack(stack, itemMetaModifier);
        return stack;
    }

    public static <IM extends ItemMeta> void modifyStack(ItemStack itemStack, Consumer<? super IM> itemMetaModifier) {
        IM meta = (IM) itemStack.getItemMeta();
        itemMetaModifier.accept(meta);

        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            lore = Collections.singletonList("§9Просмотр инвентаря");
        } else if (!"§9Просмотр инвентаря".equals(lore.get(lore.size() - 1))) {
            lore.add("");
            lore.add("§9Просмотр инвентаря");
        }
        meta.setLore(lore);

        itemStack.setItemMeta(meta);
    }

    public static <IM extends ItemMeta> Consumer<IM> name(String name) {
        return meta -> meta.setDisplayName(name);
    }

    public static <IM extends ItemMeta> Consumer<IM> and(Consumer<? super IM>... modifiers) {
        return meta -> { for (Consumer<? super IM> modifier : modifiers) modifier.accept(meta); };
    }

}
