package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MobInvasionEvent implements ServerEvent {

    private final EventosPlugin plugin;
    private final Random random = new Random();
    private final List<LivingEntity> spawnedMobs = new ArrayList<>();
    private BukkitTask endTask;
    private boolean running = false;

    public MobInvasionEvent(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "mob-invasion";
    }

    @Override
    public String getDisplayName() {
        return ChatColor.DARK_GREEN + "Invasión de Mobs" + ChatColor.RESET;
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
        int durationSeconds = config.getInt(getId() + ".duration-seconds", 90);
        int mobCount = config.getInt(getId() + ".mob-count", 20);
        int spawnRadius = config.getInt(getId() + ".spawn-radius", 25);
        List<String> mobTypeNames = config.getStringList(getId() + ".mob-types");

        List<EntityType> mobTypes = new ArrayList<>();
        for (String name : mobTypeNames) {
            try {
                mobTypes.add(EntityType.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Tipo de mob inválido en config: " + name);
            }
        }
        if (mobTypes.isEmpty()) {
            mobTypes.add(EntityType.ZOMBIE);
        }

        List<Player> onlinePlayers = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        if (onlinePlayers.isEmpty()) {
            running = false;
            return;
        }

        for (int i = 0; i < mobCount; i++) {
            Player target = onlinePlayers.get(random.nextInt(onlinePlayers.size()));
            World world = target.getWorld();
            Location base = target.getLocation();

            double angle = random.nextDouble() * Math.PI * 2;
            double dist = random.nextDouble() * spawnRadius;
            double x = base.getX() + Math.cos(angle) * dist;
            double z = base.getZ() + Math.sin(angle) * dist;
            int y = world.getHighestBlockYAt((int) x, (int) z) + 1;

            Location spawnLoc = new Location(world, x, y, z);
            EntityType type = mobTypes.get(random.nextInt(mobTypes.size()));

            var entity = world.spawnEntity(spawnLoc, type);
            if (entity instanceof LivingEntity living) {
                spawnedMobs.add(living);
            }
        }

        endTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::stop, durationSeconds * 20L);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        if (endTask != null) endTask.cancel();

        // Elimina los mobs que sigan vivos al terminar el evento
        for (LivingEntity mob : spawnedMobs) {
            if (mob != null && !mob.isDead()) {
                mob.remove();
            }
        }
        spawnedMobs.clear();

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "La invasión de mobs ha terminado.");
        }
    }
}
