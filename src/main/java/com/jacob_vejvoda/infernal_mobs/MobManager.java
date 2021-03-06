package com.jacob_vejvoda.infernal_mobs;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.jacob_vejvoda.infernal_mobs.ability.EnumAbilities;
import com.jacob_vejvoda.infernal_mobs.api.InfernalMobSpawnEvent;
import com.jacob_vejvoda.infernal_mobs.api.InfernalSpawnReason;
import com.jacob_vejvoda.infernal_mobs.persist.Mob;
import com.jacob_vejvoda.infernal_mobs.persist.ParticleEffect;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.jacob_vejvoda.infernal_mobs.ability.EnumAbilities.*;

public class MobManager {
    public final Map<UUID, Mob> mobMap = new HashMap<>();
    public final Set<UUID> mounteeMobs = new HashSet<>();
    private final InfernalMobs plugin;

    // Map<childId, parentId>
    public final Cache<UUID, UUID> mamaSpawned = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();
    public final Cache<UUID, Boolean> unnaturallySpawned =  CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

    public MobManager(InfernalMobs plugin) {
        this.plugin = plugin;
    }

    /** spwan an infernal mob from nowhere */
    public Mob spawnMob(EntityType type, Location loc, List<EnumAbilities> abilities, InfernalSpawnReason reason) {
        return spawnMob(type, loc, abilities, null, reason);
    }

    public Mob spawnMob(EntityType type, Location loc, List<EnumAbilities> abilities, UUID parentId, InfernalSpawnReason reason) {

        if (!type.isAlive()) throw new IllegalArgumentException(type.name() + " is not a living entity");
        Entity spawnedEntity = loc.getWorld().spawnEntity(loc, type);
        UUID id = spawnedEntity.getUniqueId();
        int lives = abilities.contains(EnumAbilities.ONEUP) ? 2 : 1;
        Mob mob = new Mob(id, lives, ConfigReader.getRandomParticleEffect(), abilities);
        mobMap.put(id, mob);
        unnaturallySpawned.put(id, true);

        InfernalMobSpawnEvent spwanEvent = new InfernalMobSpawnEvent((LivingEntity) spawnedEntity, mob, parentId, reason);
        for (EnumAbilities ability : abilities) ability.onMobSpawn(spwanEvent);
        setInfernalHealth(spwanEvent);
        setInfernalMobName(spwanEvent);

        Bukkit.getServer().getPluginManager().callEvent(spwanEvent);
        return mob;
    }

    /**
     * Set the max health point for newly created infernal mobs, depending on their level.
     */
    public static void setInfernalHealth(InfernalMobSpawnEvent ev) {
        LivingEntity ent = ev.mobEntity;
        double baseHealth = ent.getHealth();
        double newHealth;

        if (ConfigReader.isHealthByPower()) {
            newHealth = baseHealth * ev.mob.getMobLevel();
        } else if (ConfigReader.isHealthByDistance()) {
            double mobDistance = ent.getWorld().getSpawnLocation().distance(ent.getLocation());
            double distanceLevel = mobDistance / ConfigReader.getDistancePerHealthLevel();
            newHealth = distanceLevel * ConfigReader.getHealthPerHealthLevel();
        } else {
            newHealth = baseHealth * ConfigReader.getHealthMultiplier();
        }

        if (newHealth > baseHealth) {
            long tmp = Math.round(newHealth);
            ent.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(tmp);
            ent.setHealth(tmp);
        }
    }

    /** Give infernal mobs names when they are spawned */
    public static void setInfernalMobName(InfernalMobSpawnEvent ev) {
        LivingEntity e = ev.mobEntity;
        if (ConfigReader.isInfernalMobHasNameTag()) {
            String nameTag = getMobNameTag(e.getType(), ev.mob.abilityList);
            e.setCustomName(nameTag);
            if (ConfigReader.isInfernalMobNameTagAlwaysVisible())
                e.setCustomNameVisible(true);
        }
    }

    /** Infernal Mob's name. Based on entity type and level */
    public static String getMobNameTag(EntityType type, List<EnumAbilities> abilities) {
        String tag = ConfigReader.getMobNameTag();
        tag = tag.replace("<mobName>", type.name())
                .replace("<mobLevel>", Integer.toString(abilities.size()))
                .replace("<abilities>", getHumanReadableAbilityString(abilities, 5, 32))
                .replace("<prefix>", ConfigReader.getNameTagPrefixByLevel(abilities.size()));
        tag = ChatColor.translateAlternateColorCodes('&', tag);
        return tag;
    }

    /** "mama thief ..." */
    public static String getHumanReadableAbilityString(List<EnumAbilities> abilities, int maxAbility, int maxLength) {
        if (abilities.size() <= 0) return "";
        maxLength -= 3;
        int count = 0;
        int len = 0;
        for (EnumAbilities ab : abilities) {
            len += ab.name().length() + 1;
            count += 1;
            if (len > maxLength) break;
        }
        if (len > maxLength) count --;
        String ret = abilities.get(0).name().toLowerCase();
        for (int i=1;i<count;i++) {
            ret += " " + abilities.get(i).name().toLowerCase();
        }
        if (count < abilities.size()) ret += " ...";
        return ret;
    }

    /**
     * Change the given entity into infernal mob
     * may need to be called delayed
     *
     * @param mobEntity     the entity
     */
    public void infernalNaturalSpawn(LivingEntity mobEntity) {
        if (mobEntity.isDead() || !mobEntity.isValid() || mobEntity.getCustomName() != null) return;
        if (mobEntity.hasMetadata("NPC") || mobEntity.hasMetadata("shopkeeper")) return;
        if (!isAcceptableBaby(mobEntity)) return;
        final UUID id = mobEntity.getUniqueId();
        UUID parentId = mamaSpawned.getIfPresent(id);
        if (unnaturallySpawned.getIfPresent(id) != null) return;
        if (!Helper.possibility(ConfigReader.getInfernalNaturalSpawningPercentage())) return;

        List<EnumAbilities> abilities = Helper.randomNItems(ConfigReader.getEnabledAbilities(), getInfernalLevelForLocation(mobEntity.getLocation()));
        if (abilities == null || abilities.size() <= 0) return;
        if (parentId != null) {
            if (!mobMap.containsKey(parentId) || mobMap.get(parentId).maxMamaInfernal <= 0) {
                return;
            }
            mobMap.get(parentId).maxMamaInfernal--;
            if (abilities.contains(EnumAbilities.MAMA)) {
                abilities.remove(EnumAbilities.MAMA);
                if (abilities.size() <= 0) {
                    return;
                }
            }
        }
        // setup infernal mob
        int lives = abilities.contains(EnumAbilities.ONEUP) ? 2 : 1;
        Mob mob = new Mob(id, lives, ConfigReader.getRandomParticleEffect(), abilities);

        InfernalMobSpawnEvent spwanEvent;
        if (parentId != null) {
            spwanEvent = new InfernalMobSpawnEvent(mobEntity, mob, parentId, InfernalSpawnReason.MAMA);
        } else {
            spwanEvent = new InfernalMobSpawnEvent(mobEntity, mob, null, InfernalSpawnReason.NATURAL);
        }
        for (EnumAbilities ability : abilities) ability.onMobSpawn(spwanEvent);

        setInfernalHealth(spwanEvent);
        setInfernalMobName(spwanEvent);

        mobMap.put(id, mob);

        Bukkit.getPluginManager().callEvent(spwanEvent);

        // Show message
        if (ConfigReader.isSpwanMessageEnabled()) {
            String msg = Helper.randomItem(ConfigReader.getSpwanMessages());
            msg = msg.replace("{mob}", mobEntity.getCustomName() == null ? mobEntity.getType().name().toLowerCase() : mobEntity.getCustomName());
            msg = ChatColor.translateAlternateColorCodes('&', msg);
            if (ConfigReader.isBroadcastSpawnMessageServer()) {
                Bukkit.broadcastMessage(msg);
            } else if (ConfigReader.isBroadcastSpawnMessageWorld()) {
                for (Player p : mobEntity.getWorld().getPlayers()) {
                    p.sendMessage(msg);
                }
            } else {
                int r = ConfigReader.getSpawnMessageBroadcaseRadius();
                for (Entity e : mobEntity.getNearbyEntities(r, r, r)) {
                    if (e instanceof Player) {
                        e.sendMessage(msg);
                    }
                }
            }
        }

    }

    /**
     * Returns false only when the Entity is a baby AND is disabled in config
     */
    public static boolean isAcceptableBaby(Entity e) {
        if (!ConfigReader.getDisabledBabyNameList().contains(e.getType())) return true;
        if (e instanceof Zombie && ((Zombie) e).isBaby()) return false;
        if (e instanceof Ageable && !((Ageable) e).isAdult()) return false;
        return true;
    }

    public static int getInfernalLevelForLocation(Location loc) {
        int level;
        if (ConfigReader.isSpawnedLevelByDistance()) {
            Location spLoc = loc.getWorld().getSpawnLocation();
            double dist = (loc.getX() - spLoc.getX()) * (loc.getX() - spLoc.getX());
            dist += (loc.getZ() - spLoc.getZ()) * (loc.getZ() - spLoc.getZ());
            dist = Math.sqrt(dist);
            level = (int)Math.ceil(dist / ConfigReader.getSpawnDistancePerLevel());
        } else {
            level = Helper.rand(ConfigReader.getMinimalLevel(), ConfigReader.getMaximumLevel());
        }
        if (level <= 0) level = 1;
        return level;
    }

    // preserve health and lives
    public LivingEntity morphInfernalMob(LivingEntity mobEntity, Mob mob) {
        EntityType type = Helper.randomItem(ConfigReader.getEnabledEntityTypes());
        Location loc = mobEntity.getLocation();
        Entity spawnedEntity = loc.getWorld().spawnEntity(loc, type);
        UUID id = spawnedEntity.getUniqueId();
        mob.entityId = id;
        unnaturallySpawned.put(id, true);

        InfernalMobSpawnEvent spwanEvent = new InfernalMobSpawnEvent((LivingEntity) spawnedEntity, mob, mobEntity.getUniqueId(), InfernalSpawnReason.MORPH);
        for (EnumAbilities ability : mob.abilityList) ability.onMobSpawn(spwanEvent);

        ((LivingEntity) spawnedEntity).getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(mobEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());
        ((LivingEntity) spawnedEntity).setHealth(mobEntity.getHealth());
        setInfernalMobName(spwanEvent);

        mobMap.put(id, mob);
        mobMap.remove(mobEntity.getUniqueId());
        mobEntity.remove();
        Bukkit.getPluginManager().callEvent(spwanEvent);
        return (LivingEntity) spawnedEntity;
    }

    public void spawnGhost(UUID parentId, Location loc) {
        boolean isEvil = Helper.possibility(0.3);

        // ability list
        List<EnumAbilities> abilities = null;
        if (isEvil)
            abilities = Lists.newArrayList(CLOAKED, ENDER, NECROMANCER, WITHERING, BLINDING);
        else
            abilities = Lists.newArrayList(CLOAKED, ENDER, GHASTLY, SAPPER, CONFUSING);

        // spawn
        Mob mob = spawnMob(EntityType.ZOMBIE, loc, abilities, parentId, InfernalSpawnReason.GHOST);
        LivingEntity mobEntity = (LivingEntity) Bukkit.getServer().getEntity(mob.entityId);
        mobEntity.setCanPickupItems(false);

        ItemStack chest, head;
        if (isEvil) {
            mob.particleEffect = new ParticleEffect(Particle.SMOKE_NORMAL, 2, 12);
            chest = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
            Helper.changeLeatherColor(chest, Color.BLACK);
            chest.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1);

            head = new ItemStack(Material.WITHER_SKELETON_SKULL, 1);
            ItemMeta m = head.getItemMeta();
            m.setDisplayName(ChatColor.BLACK + "Ghost Head");
            head.setItemMeta(m);
        } else {
            mob.particleEffect = new ParticleEffect(Particle.CLOUD, 0, 8);
            chest = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
            Helper.changeLeatherColor(chest, Color.WHITE);
            chest.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1);

            head = new ItemStack(Material.SKELETON_SKULL, 1);
            ItemMeta m = head.getItemMeta();
            m.setDisplayName(ChatColor.WHITE + "Ghost Head");
            head.setItemMeta(m);
        }

        mobEntity.getEquipment().setHelmet(head);
        mobEntity.getEquipment().setChestplate(chest);
        mobEntity.getEquipment().setHelmetDropChance(0);
        mobEntity.getEquipment().setChestplateDropChance(0);

        if (Helper.possibility(0.2)) {
            mobEntity.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_HOE));
            mobEntity.getEquipment().setItemInMainHandDropChance(0);
        }

        // setup moving pattern
        final LivingEntity g = mobEntity;
        new BukkitRunnable() {
            private boolean cancelled = false;
            @Override
            public void run() {
                if (cancelled) return;
                if (g.isDead()) {
                    cancelled = true;
                    this.cancel();
                    return;
                }
                org.bukkit.util.Vector v = g.getEyeLocation().getDirection().multiply(0.3);
                g.setVelocity(v);
            }
        }.runTaskTimer(InfernalMobs.instance, 2L, 2L);
    }
}
