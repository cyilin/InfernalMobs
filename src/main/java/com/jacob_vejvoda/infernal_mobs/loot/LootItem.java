package com.jacob_vejvoda.infernal_mobs.loot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/** Template of items to be given to players */
public class LootItem {
    public ItemStack item;
    public RangePair damageRange;
    public RangePair amountRange;
    public Map<Enchantment, RangePair> extraEnchants;
    public List<String> commands;

    /**
     * get a randomized item to be given to player
     */
    public ItemStack get() {
        ItemStack ret = item.clone();
        ItemMeta meta = ret.getItemMeta();
        if (damageRange != null && meta instanceof Damageable && ((Damageable) meta).hasDamage()) {
            short damageR = (short) damageRange.get();
            if (damageR < 0) damageR = 0;
            if (damageR >= ret.getType().getMaxDurability())
                damageR = (short) (ret.getType().getMaxDurability() - 1);
            ((Damageable) meta).setDamage(damageR);
            ret.setItemMeta(meta);
        }
        if (amountRange != null) {
            ret.setAmount(amountRange.get());
        }
        if (extraEnchants != null) {
            if (ret.getType() == Material.ENCHANTED_BOOK) {
                EnchantmentStorageMeta enchantmentStorageMeta = (EnchantmentStorageMeta) ret.getItemMeta();
                for (Enchantment e : extraEnchants.keySet()) {
                    enchantmentStorageMeta.addStoredEnchant(e, extraEnchants.get(e).get(), true);
                }
                ret.setItemMeta(enchantmentStorageMeta);
            } else {
                for (Enchantment e : extraEnchants.keySet()) {
                    ret.addUnsafeEnchantment(e, extraEnchants.get(e).get());
                }
            }
        }
        return ret;
    }

    public void applyCommands(Player p) {
        if (commands == null) return;
        for (String cmd : commands) {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(),
                    ChatColor.translateAlternateColorCodes('&', cmd)
                            .replace("{player}", p.getName()));
        }
    }
}
