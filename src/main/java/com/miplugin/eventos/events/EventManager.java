package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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

    /**
     * Momento (epoch en milisegundos) en que le toca a cada evento su próximo intento automático.
     * Se guarda en estado-eventos.yml para que reiniciar el servidor NO reinicie la cuenta.
     */
    private final Map<String, Long> nextRunMillis = new HashMap<>();
    private final File stateFile;

    /** Si a un evento se le pasó la hora mientras el servidor estaba apagado, espera esto tras arrancar... */
    private static final long OVERDUE_GRACE_MILLIS = 3 * 60_000L;
    /** ...más esto por cada otro evento atrasado, para que no intenten arrancar todos al mismo tiempo. */
    private static final long OVERDUE_STAGGER_MILLIS = 6 * 60_000L;

    /**
     * Si a la hora del ciclo hay otro evento activo, o este no cumple los requisitos en ese momento
     * (ej. no hay nadie conectado), se reintenta pronto en lugar de perder el ciclo completo.
     */
    private static final long RETRY_BLOCKED_MILLIS = 60_000L;
    /** Si el evento SÍ se intentó pero no logró abrir (ej. no pudo aparecer el aldeano), se reintenta más tarde. */
    private static final long RETRY_FAILED_MILLIS = 5 * 60_000L;

    public EventManager(EventosPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "estado-eventos.yml");
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

    /**
     * Programa cada evento según su interval-minutes en config.yml.
     *
     * La cuenta regresiva sobrevive a los reinicios: si el servidor se apaga con 40 minutos
     * pendientes para un evento, al volver a arrancar le siguen faltando esos 40 minutos
     * (no vuelve a empezar desde cero). Si se le pasó la hora mientras estaba apagado, se
     * intenta unos minutos después de arrancar.
     */
    public void scheduleAll() {
        loadState();
        FileConfiguration config = plugin.getConfig();
        long now = System.currentTimeMillis();
        int overdueCount = 0;

        for (ServerEvent event : events.values()) {
            if (!config.getBoolean(event.getId() + ".enabled", true)) {
                continue;
            }
            long intervalMillis = intervalMillis(event);
            Long saved = nextRunMillis.get(event.getId());

            long delayMillis;
            if (saved == null) {
                delayMillis = intervalMillis; // primera vez: espera un ciclo completo
            } else if (saved > now) {
                delayMillis = Math.min(saved - now, intervalMillis); // retoma donde se quedó
            } else {
                delayMillis = OVERDUE_GRACE_MILLIS + overdueCount * OVERDUE_STAGGER_MILLIS;
                overdueCount++;
            }
            scheduleNext(event, delayMillis);
            plugin.getLogger().info("Evento '" + event.getId() + "': próximo intento automático en ~"
                    + Math.max(1L, delayMillis / 60_000L) + " min.");
        }
        saveState();
    }

    private long intervalMillis(ServerEvent event) {
        long minutes = Math.max(1L, plugin.getConfig().getLong(event.getId() + ".interval-minutes", 60));
        return minutes * 60_000L;
    }

    private void scheduleNext(ServerEvent event, long delayMillis) {
        long delayTicks = Math.max(1L, delayMillis / 50L);
        nextRunMillis.put(event.getId(), System.currentTimeMillis() + delayMillis);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> onScheduledRun(event), delayTicks);
        BukkitTask previous = scheduledTasks.put(event.getId(), task);
        if (previous != null) previous.cancel();
    }

    private void onScheduledRun(ServerEvent event) {
        // Hay otro evento activo (solo puede haber 1 a la vez) o este no cumple requisitos ahora
        // mismo: se reintenta en 1 minuto. Antes se perdía todo el ciclo y el siguiente intento era
        // un intervalo completo después (90 min para el Mercado Negro).
        if (isAnyEventRunning() || !event.canStart()) {
            scheduleNext(event, RETRY_BLOCKED_MILLIS);
            saveState();
            return;
        }

        if (triggerEvent(event)) {
            // Arrancó: el siguiente ciclo cuenta desde ahora, sin importar cuánto dure el evento.
            scheduleNext(event, intervalMillis(event));
        } else {
            scheduleNext(event, RETRY_FAILED_MILLIS);
        }
        saveState();
    }

    private void loadState() {
        nextRunMillis.clear();
        if (!stateFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        var section = yaml.getConfigurationSection("proximo-intento");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            nextRunMillis.put(id, section.getLong(id));
        }
    }

    private void saveState() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Long> entry : nextRunMillis.entrySet()) {
            yaml.set("proximo-intento." + entry.getKey(), entry.getValue());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar estado-eventos.yml: " + e.getMessage());
        }
    }

    /**
     * Inicia el evento y devuelve true SOLO si de verdad quedó activo. El aviso "¡Comienza!" se
     * manda después de confirmarlo: antes salía primero, y si el evento no podía abrir (ej. no
     * aparecía el aldeano del Mercado Negro) el chat anunciaba algo que nunca ocurría.
     */
    private boolean triggerEvent(ServerEvent event) {
        try {
            event.start();
        } catch (Exception e) {
            plugin.getLogger().warning("El evento '" + event.getId() + "' falló al iniciar: " + e.getMessage());
        }

        if (!event.isRunning()) {
            String message = "El evento '" + event.getId() + "' no pudo iniciar. "
                    + "Revisa los avisos en la consola del servidor para ver el motivo.";
            plugin.getLogger().warning(message);
            notifyAdmins(message);
            return false;
        }

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "¡Comienza: " + event.getDisplayName() + ChatColor.YELLOW + "!");
        }
        return true;
    }

    /** Avisa en el juego a quien tenga permiso de administrar eventos (además de la consola). */
    private void notifyAdmins(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("eventos.admin")) {
                player.sendMessage(ChatColor.RED + "[Eventos] " + message);
            }
        }
    }

    /** Fuerza el inicio de un evento (comando o GUI), respetando la regla de "solo 1 a la vez". */
    public StartResult forceStart(String id) {
        ServerEvent event = events.get(id);
        if (event == null) return StartResult.NOT_FOUND;
        if (event.isRunning()) return StartResult.ALREADY_RUNNING;
        if (isAnyEventRunning()) return StartResult.OTHER_EVENT_ACTIVE;
        if (!event.canStart()) return StartResult.CANNOT_START;

        if (!triggerEvent(event)) return StartResult.CANNOT_START;

        // Un inicio manual cuenta como una ejecución: reinicia la cuenta regresiva automática de ese
        // evento para que no vuelva a salir justo después (ni quede uno atrasado en cola).
        if (plugin.getConfig().getBoolean(id + ".enabled", true)) {
            scheduleNext(event, intervalMillis(event));
            saveState();
        }
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
