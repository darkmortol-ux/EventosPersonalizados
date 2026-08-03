package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;

public class EternalNightEvent implements ServerEvent, Listener {

    private final EventosPlugin plugin;
    private final Random random = new Random();
    private BukkitTask endTask;
    private boolean running = false;
    private boolean listenerRegistered = false;

    public EternalNightEvent(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "eternal-night";
    }

    @Override
    public String getDisplayName() {
        return ChatColor.DARK_PURPLE + "Noche Eterna" + ChatColor.RESET;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean canStart() {
        return true;
    }

    @Override
    public void start() {
        if (running) return;
        running = true;

        var config = plugin.getConfig();
        int durationSeconds = config.getInt(getId() + ".duration-seconds", 300);
        boolean buffMobs = config.getBoolean(getId() + ".buff-mobs", true);

        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setTime(18000L); // medianoche
            }
        }

        if (buffMobs) {
            for (World world : plugin.getServer().getWorlds()) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (!(entity instanceof Player)) {
                        applyBuff(entity);
                    }
                }
            }
        }

        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }

        endTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::stop, durationSeconds * 20L);
    }

    private void applyBuff(LivingEntity entity) {
        var healthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(healthAttr.getBaseValue() * 1.5);
            entity.setHealth(Math.min(entity.getHealth() * 1.5, healthAttr.getValue()));
        }
        var dmgAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.setBaseValue(dmgAttr.getBaseValue() * 1.3);
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (!running) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        var config = plugin.getConfig();
        int bonusXp = config.getInt(getId() + ".bonus-xp", 5);
        List<String> lootNames = config.getStringList(getId() + ".loot-table");

        event.setDroppedExp(event.getDroppedExp() + bonusXp);

        if (!lootNames.isEmpty() && random.nextInt(100) < 40) { // 40% de probabilidad de loot extra
            String materialName = lootNames.get(random.nextInt(lootNames.size()));
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                event.getDrops().add(new ItemStack(material, 1));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Material inválido en loot-table de eternal-night: " + materialName);
            }
        }
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        if (endTask != null) endTask.cancel();

        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                world.setTime(1000L); // vuelve al día
            }
        }

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "La noche eterna ha terminado.");
        }
    }
}
