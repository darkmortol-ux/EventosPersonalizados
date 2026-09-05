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
    /** Timestamp (millis) estimado en el que cada evento en curso deberia terminar. Usado solo para mostrar cuentas regresivas (ej: en un scoreboard). */
    private final Map<String, Long> finalizaEnMillis = new LinkedHashMap<>();

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

        long duracionSegundos = calcularDuracionSegundos(event);
        if (duracionSegundos > 0) {
            finalizaEnMillis.put(event.getId(), System.currentTimeMillis() + duracionSegundos * 1000L);
        } else {
            finalizaEnMillis.remove(event.getId());
        }

        event.start();
    }

    /**
     * Calcula cuantos segundos deberia durar el evento leyendo su config.
     * Soporta las dos convenciones usadas por los eventos existentes:
     * "duration-seconds" (la mayoria) y "time-limit-minutes" (cofre supremo).
     * Devuelve 0 si el evento no tiene una duracion fija configurada (ej: boss aleatorio).
     */
    private long calcularDuracionSegundos(ServerEvent event) {
        var config = plugin.getConfig();
        String id = event.getId();

        long duracion = config.getLong(id + ".duration-seconds", 0);
        if (duracion <= 0) {
            duracion = config.getLong(id + ".time-limit-minutes", 0) * 60L;
        }
        return duracion;
    }

    /** Devuelve el primer evento actualmente en curso, o null si ninguno esta activo. */
    public ServerEvent getEventoActivo() {
        for (ServerEvent event : events.values()) {
            if (event.isRunning()) return event;
        }
        return null;
    }

    /**
     * Segundos restantes estimados antes de que el evento indicado termine.
     * Devuelve 0 si el evento no esta corriendo o no tiene duracion fija configurada.
     */
    public long getSegundosRestantes(String id) {
        ServerEvent event = events.get(id);
        if (event == null || !event.isRunning()) return 0;

        Long finaliza = finalizaEnMillis.get(id);
        if (finaliza == null) return 0;

        return Math.max(0, (finaliza - System.currentTimeMillis()) / 1000);
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
