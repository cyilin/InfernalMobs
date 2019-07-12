package com.jacob_vejvoda.infernal_mobs.ability;

import com.jacob_vejvoda.infernal_mobs.Helper;
import com.jacob_vejvoda.infernal_mobs.persist.Mob;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import static org.bukkit.Material.AIR;

public class AbilityRust implements IAbility {
    @Override
    public void onPlayerAttack(LivingEntity mobEntity, Mob mob, Player attacker, boolean isDirectAttack, EntityDamageByEntityEvent ev) {
        if (Helper.possibility(0.3)) return;
        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (item == null || item.getType() == AIR || (item.hasItemMeta() && item.getItemMeta().isUnbreakable())) return;
        if (item.getType().getMaxDurability() < 1) return;
        int damage = Math.round(20F / (item.getEnchantmentLevel(Enchantment.DURABILITY) + 1F));
        if (damage <= 0) damage = 1;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable && ((Damageable) meta).hasDamage()) {
            int newDamage = ((Damageable) meta).getDamage() + damage;
            if (newDamage > item.getType().getMaxDurability()) {
                ((Damageable) meta).setDamage((short) (item.getType().getMaxDurability() + 1));
            } else {
                ((Damageable) meta).setDamage((short) newDamage);
            }
            item.setItemMeta(meta);
        }
    }
}
