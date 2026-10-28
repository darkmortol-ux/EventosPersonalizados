package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;

public class EventManager {

    /** Resultado detallado de intentar iniciar un evento, para poder dar el mensaje correcto en comando/GUI. */
    public enum StartResult {
        STARTED,
        NOT_FOUND,
        ALREADY_RUNNING,     // ese mismo evento ya estaba activo
        OTHER_EVENT_ACTIVE,  // hay OTRO evento activo; solo puede haber uno a la vez
        CANNOT_START         // canStart() devolvió false (ej. faltan jugadores)
    }

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
        register(new BlackMarketEvent(plugin));
    }

    private void register(ServerEvent event) {
        events.put(event.getId(), event);
    }

    /** true si CUALQUIER evento (el que sea) está activo en este momento. */
    public boolean isAnyEventRunning() {
        for (ServerEvent event : events.values()) {
            if (event.isRunning()) return true;
        }
        return false;
    }

    /** Devuelve el evento actualmente activo, o null si ninguno lo está. */
    public ServerEvent getActiveEvent() {
        for (ServerEvent event : events.values()) {
            if (event.isRunning()) return event;
        }
        return null;
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
                // Nunca se activa un evento automático si YA hay otro (o el mismo) corriendo,
                // aunque a varios eventos les toque el mismo ciclo por coincidencia de tiempos.
                if (isAnyEventRunning()) return;
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

    /** Fuerza el inicio de un evento (comando o GUI), respetando la regla de "solo 1 a la vez". */
    public StartResult forceStart(String id) {
        ServerEvent event = events.get(id);
        if (event == null) return StartResult.NOT_FOUND;
        if (event.isRunning()) return StartResult.ALREADY_RUNNING;
        if (isAnyEventRunning()) return StartResult.OTHER_EVENT_ACTIVE;
        if (!event.canStart()) return StartResult.CANNOT_START;

        triggerEvent(event);
        return StartResult.STARTED;
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
