package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.ChatColor;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class ToxicStormEvent implements ServerEvent {

    private final EventosPlugin plugin;
    private BukkitTask damageTask;
    private BukkitTask endTask;
    private boolean running = false;

    public ToxicStormEvent(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "toxic-storm";
    }

    @Override
    public String getDisplayName() {
        return ChatColor.DARK_GREEN + "Tormenta Tóxica" + ChatColor.RESET;
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
        int durationSeconds = config.getInt(getId() + ".duration-seconds", 60);
        double damagePerTick = config.getDouble(getId() + ".damage-per-tick", 1.0);
        int tickIntervalSeconds = config.getInt(getId() + ".tick-interval-seconds", 3);
        boolean onlyOutdoors = config.getBoolean(getId() + ".only-outdoors", true);

        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                world.setStorm(true);
                world.setWeatherDuration(durationSeconds * 20);
            }
        }

        damageTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (onlyOutdoors && !isExposedToSky(player)) continue;
                player.damage(damagePerTick);
            }
        }, 0L, tickIntervalSeconds * 20L);

        endTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::stop, durationSeconds * 20L);
    }

    private boolean isExposedToSky(Player player) {
        World world = player.getWorld();
        return world.getHighestBlockYAt(player.getLocation()) <= player.getLocation().getBlockY();
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        if (damageTask != null) damageTask.cancel();
        if (endTask != null) endTask.cancel();

        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                world.setStorm(false);
            }
        }

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "La tormenta tóxica ha terminado.");
        }
    }
}
