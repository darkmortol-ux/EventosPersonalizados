package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class RandomBossEvent implements ServerEvent, Listener {

    private final EventosPlugin plugin;
    private final Random random = new Random();
    private UUID currentBossId;
    private boolean running = false;
    private boolean listenerRegistered = false;

    private static final EntityType[] BOSS_TYPES = {
            EntityType.WITHER_SKELETON, EntityType.RAVAGER, EntityType.EVOKER, EntityType.VINDICATOR
    };

    public RandomBossEvent(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "random-boss";
    }

    @Override
    public String getDisplayName() {
        return ChatColor.DARK_RED + "Jefe Aleatorio" + ChatColor.RESET;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean canStart() {
        int minPlayers = plugin.getConfig().getInt(getId() + ".min-players", 3);
        return plugin.getServer().getOnlinePlayers().size() >= minPlayers;
    }

    @Override
    public void start() {
        if (running) return;
        List<Player> players = new java.util.ArrayList<>(plugin.getServer().getOnlinePlayers());
        if (players.isEmpty()) return;

        running = true;
        var config = plugin.getConfig();
        double bossHealth = config.getDouble(getId() + ".boss-health", 200.0);
        double bossDamage = config.getDouble(getId() + ".boss-damage", 8.0);

        Player target = players.get(random.nextInt(players.size()));
        World world = target.getWorld();
        Location spawnLoc = target.getLocation().add(
                random.nextInt(10) - 5, 0, random.nextInt(10) - 5
        );

        EntityType type = BOSS_TYPES[random.nextInt(BOSS_TYPES.length)];
        LivingEntity boss = (LivingEntity) world.spawnEntity(spawnLoc, type);
        boss.setCustomName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Jefe del Servidor");
        boss.setCustomNameVisible(true);

        var healthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(bossHealth);
            boss.setHealth(bossHealth);
        }
        var dmgAttr = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.setBaseValue(bossDamage);
        }

        currentBossId = boss.getUniqueId();

        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.RED
                    + "¡Un jefe ha aparecido cerca de " + target.getName() + "! Prepárense para el combate.");
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!running || currentBossId == null) return;
        if (!event.getEntity().getUniqueId().equals(currentBossId)) return;

        var config = plugin.getConfig();
        int rewardMoney = config.getInt(getId() + ".reward-money", 0);
        List<?> rewardItems = config.getList(getId() + ".reward-items");

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            if (rewardMoney > 0) {
                boolean paid = plugin.getEconomyManager().deposit(killer, rewardMoney);
                if (paid) {
                    killer.sendMessage(ChatColor.GREEN + "¡Has ganado "
                            + plugin.getEconomyManager().format(rewardMoney)
                            + " por derrotar al jefe!");
                } else {
                    killer.sendMessage(ChatColor.GREEN + "¡Has derrotado al jefe! "
                            + ChatColor.GRAY + "(recompensa en dinero no disponible: instala Vault + un plugin de economía)");
                }
            }
            if (rewardItems != null) {
                for (Object obj : rewardItems) {
                    if (obj instanceof java.util.Map<?, ?> map) {
                        giveRewardItem(killer, map);
                    }
                }
            }
        }

        stop();
    }

    private void giveRewardItem(Player player, java.util.Map<?, ?> map) {
        Object itemObj = map.get("item");
        if (itemObj == null) return;
        try {
            Material material = Material.valueOf(itemObj.toString().toUpperCase());
            int min = map.get("min") instanceof Number ? ((Number) map.get("min")).intValue() : 1;
            int max = map.get("max") instanceof Number ? ((Number) map.get("max")).intValue() : 1;
            int amount = min + (max > min ? random.nextInt(max - min + 1) : 0);
            player.getInventory().addItem(new ItemStack(material, amount));
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Material inválido en reward-items de random-boss");
        }
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;

        if (currentBossId != null) {
            for (World world : plugin.getServer().getWorlds()) {
                world.getEntities().stream()
                        .filter(e -> e.getUniqueId().equals(currentBossId))
                        .findFirst()
                        .ifPresent(e -> e.remove());
            }
        }
        currentBossId = null;

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "El jefe del servidor ha sido derrotado o el evento ha terminado.");
        }
    }
}
