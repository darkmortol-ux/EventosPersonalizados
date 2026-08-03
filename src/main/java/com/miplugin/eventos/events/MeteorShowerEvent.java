package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Random;

public class MeteorShowerEvent implements ServerEvent {

    private final EventosPlugin plugin;
    private final Random random = new Random();
    private BukkitTask spawnTask;
    private BukkitTask endTask;
    private boolean running = false;

    public MeteorShowerEvent(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "meteor-shower";
    }

    @Override
    public String getDisplayName() {
        return ChatColor.RED + "Lluvia de Meteoritos" + ChatColor.RESET;
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
        int meteorsPerSecond = config.getInt(getId() + ".meteors-per-second", 2);
        int radius = config.getInt(getId() + ".radius", 40);
        double explosionPower = config.getDouble(getId() + ".explosion-power", 3.0);
        boolean setFire = config.getBoolean(getId() + ".set-fire", true);

        long periodTicks = Math.max(1L, 20L / Math.max(1, meteorsPerSecond));

        spawnTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                World world = player.getWorld();
                Location base = player.getLocation();

                double offsetX = (random.nextDouble() * 2 - 1) * radius;
                double offsetZ = (random.nextDouble() * 2 - 1) * radius;
                Location spawnLoc = base.clone().add(offsetX, 40, offsetZ);
                spawnLoc.setY(Math.min(world.getMaxHeight() - 5, spawnLoc.getY()));

                Fireball meteor = world.spawn(spawnLoc, Fireball.class);
                meteor.setYield((float) explosionPower);
                meteor.setIsIncendiary(setFire);
                Vector direction = new Vector(
                        (random.nextDouble() * 0.4 - 0.2),
                        -1.0,
                        (random.nextDouble() * 0.4 - 0.2)
                );
                meteor.setDirection(direction);
                meteor.setVelocity(direction.multiply(1.5));
            }
        }, 0L, periodTicks);

        endTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::stop, durationSeconds * 20L);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        if (spawnTask != null) spawnTask.cancel();
        if (endTask != null) endTask.cancel();

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "La lluvia de meteoritos ha terminado.");
        }
    }
}
