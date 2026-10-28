package com.miplugin.eventos.location;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Guarda ubicaciones personalizadas asignadas con /eventos setloc <evento>,
 * para eventos que necesitan un punto fijo en el mapa (ej. el aldeano del
 * Mercado Negro). Persiste en ubicaciones.yml.
 */
public class LocationManager {

    private final EventosPlugin plugin;
    private final File file;
    private final Map<String, Location> locations = new HashMap<>();

    public LocationManager(EventosPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ubicaciones.yml");
    }

    public void setLocation(String eventId, Location location) {
        locations.put(eventId.toLowerCase(), location.clone());
        save();
    }

    /** Devuelve la ubicación asignada a ese evento, o null si no se ha asignado ninguna. */
    public Location getLocation(String eventId) {
        Location loc = locations.get(eventId.toLowerCase());
        return loc != null ? loc.clone() : null;
    }

    public boolean hasLocation(String eventId) {
        return locations.containsKey(eventId.toLowerCase());
    }

    public void removeLocation(String eventId) {
        locations.remove(eventId.toLowerCase());
        save();
    }

    public void load() {
        locations.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("ubicaciones");
        if (section == null) return;

        for (String eventId : section.getKeys(false)) {
            String worldName = section.getString(eventId + ".world");
            World world = worldName != null ? plugin.getServer().getWorld(worldName) : null;
            if (world == null) {
                plugin.getLogger().warning("Ubicación guardada para '" + eventId + "' apunta a un mundo inexistente: " + worldName);
                continue;
            }
            double x = section.getDouble(eventId + ".x");
            double y = section.getDouble(eventId + ".y");
            double z = section.getDouble(eventId + ".z");
            float yaw = (float) section.getDouble(eventId + ".yaw", 0);
            float pitch = (float) section.getDouble(eventId + ".pitch", 0);

            locations.put(eventId, new Location(world, x, y, z, yaw, pitch));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Location> entry : locations.entrySet()) {
            String path = "ubicaciones." + entry.getKey();
            Location loc = entry.getValue();
            yaml.set(path + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
            yaml.set(path + ".x", loc.getX());
            yaml.set(path + ".y", loc.getY());
            yaml.set(path + ".z", loc.getZ());
            yaml.set(path + ".yaw", loc.getYaw());
            yaml.set(path + ".pitch", loc.getPitch());
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
