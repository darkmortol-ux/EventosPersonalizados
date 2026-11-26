package com.miplugin.eventos.location;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Guarda ubicaciones personalizadas asignadas con /eventos setloc <evento>,
 * para eventos que necesitan un punto fijo en el mapa (ej. el aldeano del
 * Mercado Negro). Persiste en ubicaciones.yml.
 *
 * El mundo se guarda por NOMBRE y se busca hasta el momento de usar la ubicación
 * (getLocation), no al arrancar. Así, si un mundo todavía no está cargado cuando
 * este plugin se activa (por ejemplo los mundos de Multiverse, que cargan después),
 * la ubicación NO se pierde ni se borra de ubicaciones.yml: simplemente queda
 * disponible en cuanto el mundo exista.
 */
public class LocationManager {

    /** Ubicación tal como vive en disco: el mundo va por nombre, no como objeto World. */
    private record SavedLocation(String world, double x, double y, double z, float yaw, float pitch) {
    }

    private final EventosPlugin plugin;
    private final File file;
    private final Map<String, SavedLocation> locations = new LinkedHashMap<>();

    public LocationManager(EventosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ubicaciones.yml");
    }

    public void setLocation(String eventId, Location location) {
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "world";
        locations.put(eventId.toLowerCase(), new SavedLocation(
                worldName, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()));
        save();
    }

    /**
     * Devuelve la ubicación asignada a ese evento, o null si no se ha asignado ninguna
     * o si su mundo no está cargado ahora mismo (en ese caso hasLocation sigue dando true).
     */
    public Location getLocation(String eventId) {
        SavedLocation saved = locations.get(eventId.toLowerCase());
        if (saved == null) return null;

        World world = plugin.getServer().getWorld(saved.world());
        if (world == null) return null;
        return new Location(world, saved.x(), saved.y(), saved.z(), saved.yaw(), saved.pitch());
    }

    /** true si hay una ubicación guardada para ese evento, aunque su mundo no esté cargado ahora. */
    public boolean hasLocation(String eventId) {
        return locations.containsKey(eventId.toLowerCase());
    }

    /** Nombre del mundo de la ubicación guardada, o null si no hay ninguna. */
    public String getWorldName(String eventId) {
        SavedLocation saved = locations.get(eventId.toLowerCase());
        return saved != null ? saved.world() : null;
    }

    public void removeLocation(String eventId) {
        locations.remove(eventId.toLowerCase());
        save();
    }

    public void load() {
        locations.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("ubicaciones");
        if (section == null) return;

        for (String eventId : section.getKeys(false)) {
            String worldName = section.getString(eventId + ".world");
            if (worldName == null || worldName.isBlank()) {
                plugin.getLogger().warning("Ubicación guardada para '" + eventId + "' no tiene mundo; se ignora.");
                continue;
            }
            // Importante: NO se comprueba aquí que el mundo exista. Ver la nota de la clase.
            locations.put(eventId.toLowerCase(), new SavedLocation(
                    worldName,
                    section.getDouble(eventId + ".x"),
                    section.getDouble(eventId + ".y"),
                    section.getDouble(eventId + ".z"),
                    (float) section.getDouble(eventId + ".yaw", 0),
                    (float) section.getDouble(eventId + ".pitch", 0)));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, SavedLocation> entry : locations.entrySet()) {
            String path = "ubicaciones." + entry.getKey();
            SavedLocation loc = entry.getValue();
            yaml.set(path + ".world", loc.world());
            yaml.set(path + ".x", loc.x());
            yaml.set(path + ".y", loc.y());
            yaml.set(path + ".z", loc.z());
            yaml.set(path + ".yaw", loc.yaw());
            yaml.set(path + ".pitch", loc.pitch());
        }

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar ubicaciones.yml: " + e.getMessage());
        }
    }
}
