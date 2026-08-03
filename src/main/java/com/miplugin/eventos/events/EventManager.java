package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;

public class EventManager {

    private final EventosPlugin plugin;
    private final Map<String, ServerEvent> events = new LinkedHashMap<>();
    private final Map<String, BukkitTask> scheduledTasks = new LinkedHashMap<>();

    public EventManager(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAllEvents() {
        register(new MeteorShowerEvent(plugin));
        register(new MobInvasionEvent(plugin));
        register(new EternalNightEvent(plugin));
        register(new ToxicStormEvent(plugin));
        register(new SupremeChestEvent(plugin));
        register(new RandomBossEvent(plugin));
        register(new ParkourRaceEvent(plugin));
    }

    private void register(ServerEvent event) {
        events.put(event.getId(), event);
    }

    /** Programa cada evento según su interval-minutes en config.yml */
    public void scheduleAll() {
        FileConfiguration config = plugin.getConfig();

        for (ServerEvent event : events.values()) {
            String path = event.getId();
            if (!config.getBoolean(path + ".enabled", true)) {
                continue;
            }
            long intervalMinutes = config.getLong(path + ".interval-minutes", 60);
            long intervalTicks = intervalMinutes * 60L * 20L;

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (event.isRunning()) return;
                if (!event.canStart()) return;
                triggerEvent(event);
            }, intervalTicks, intervalTicks);

            scheduledTasks.put(event.getId(), task);
        }
    }

    private void triggerEvent(ServerEvent event) {
        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "¡Comienza: " + event.getDisplayName() + ChatColor.YELLOW + "!");
        }
        event.start();
    }

    public boolean forceStart(String id) {
        ServerEvent event = events.get(id);
        if (event == null || event.isRunning()) return false;
        triggerEvent(event);
        return true;
    }

    public boolean forceStop(String id) {
        ServerEvent event = events.get(id);
        if (event == null || !event.isRunning()) return false;
        event.stop();
        return true;
    }

    public void stopAll() {
        for (ServerEvent event : events.values()) {
            if (event.isRunning()) {
                event.stop();
            }
        }
        for (BukkitTask task : scheduledTasks.values()) {
            task.cancel();
        }
        scheduledTasks.clear();
    }

    public void reload() {
        stopAll();
        plugin.reloadConfig();
        scheduleAll();
    }

    public Map<String, ServerEvent> getEvents() {
        return events;
    }
}
