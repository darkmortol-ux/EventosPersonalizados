package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;

public class SupremeChestEvent implements ServerEvent, Listener {

    private final EventosPlugin plugin;
    private final Random random = new Random();
    private BukkitTask endTask;
    private BukkitTask reminderTask;
    private Location chestLocation;
    private boolean running = false;
    private boolean listenerRegistered = false;
    private boolean chestHasEstrella = false;

    public SupremeChestEvent(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "supreme-chest";
    }

    @Override
    public String getDisplayName() {
        return ChatColor.AQUA + "Cofre Supremo" + ChatColor.RESET;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean canStart() {
        return !plugin.getServer().getOnlinePlayers().isEmpty();
    }

    @Override
    public void start() {
        if (running) return;

        List<Player> onlinePlayers = new java.util.ArrayList<>(plugin.getServer().getOnlinePlayers());
        if (onlinePlayers.isEmpty()) return;

        running = true;
        chestHasEstrella = false;
        var config = plugin.getConfig();
        int maxDistance = config.getInt(getId() + ".max-distance", 1000);
        int timeLimitMinutes = config.getInt(getId() + ".time-limit-minutes", 10);

        // El mundo de referencia es el primer mundo del servidor (normalmente el overworld),
        // ya que el cofre aparece alrededor de la coordenada 0,0 y no cerca de un jugador.
        World world = plugin.getServer().getWorlds().get(0);

        double angle = random.nextDouble() * Math.PI * 2;
        double dist = random.nextDouble() * maxDistance;
        int x = (int) Math.round(Math.cos(angle) * dist);
        int z = (int) Math.round(Math.sin(angle) * dist);
        int y = world.getHighestBlockYAt(x, z) + 1;

        chestLocation = new Location(world, x, y, z);
        Block block = chestLocation.getBlock();
        block.setType(Material.CHEST);

        if (block.getState() instanceof Chest chest) {
            fillChest(chest);
        }

        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "¡Un cofre supremo ha aparecido en " + world.getName()
                    + " (" + x + ", " + y + ", " + z + ")! Tienes " + timeLimitMinutes
                    + " minutos para encontrarlo antes de que desaparezca.");

            if (chestHasEstrella) {
                plugin.getServer().broadcastMessage(ChatColor.LIGHT_PURPLE + "[Evento] "
                        + ChatColor.YELLOW + "¡Esta vez el cofre contiene una " + ChatColor.LIGHT_PURPLE
                        + "★ Estrella del Cambio" + ChatColor.YELLOW + "! ¡Corran por ella!");
            }
        }

        endTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::stop, timeLimitMinutes * 60L * 20L);
        scheduleReminders(world, x, y, z, timeLimitMinutes);
    }

    /**
     * Programa los avisos por chat mientras el cofre sigue en el mapa:
     * - Recuerda las coordenadas cada "reminder-interval-minutes" (2 por defecto)
     * - En los últimos "final-warning-minutes" (2 por defecto) avisa que está por desaparecer,
     *   repitiendo el aviso cada "final-warning-interval-seconds" (30 por defecto)
     */
    private void scheduleReminders(World world, int x, int y, int z, int timeLimitMinutes) {
        var config = plugin.getConfig();
        int reminderIntervalMinutes = config.getInt(getId() + ".reminder-interval-minutes", 2);
        int finalWarningMinutes = config.getInt(getId() + ".final-warning-minutes", 2);
        int finalWarningIntervalSeconds = config.getInt(getId() + ".final-warning-interval-seconds", 30);

        long reminderIntervalSeconds = Math.max(1, reminderIntervalMinutes * 60L);
        long finalPhaseStartSeconds = Math.max(0, (timeLimitMinutes - finalWarningMinutes) * 60L);
        long totalSeconds = timeLimitMinutes * 60L;
        boolean broadcastEnabled = config.getBoolean("broadcast-events", true);

        final long[] elapsed = {0L};

        reminderTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            elapsed[0]++;
            if (!running || elapsed[0] >= totalSeconds) return;

            if (broadcastEnabled) {
                if (elapsed[0] >= finalPhaseStartSeconds) {
                    if (elapsed[0] % finalWarningIntervalSeconds == 0) {
                        plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.RED
                                + "¡El cofre supremo está a punto de desaparecer! "
                                + ChatColor.YELLOW + "(" + world.getName() + ": " + x + ", " + y + ", " + z + ")");
                    }
                } else if (elapsed[0] % reminderIntervalSeconds == 0) {
                    plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                            + "El cofre supremo sigue esperando en " + world.getName()
                            + " (" + x + ", " + y + ", " + z + ").");
                }
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {
        if (!running || chestLocation == null) return;

        Location openedLoc = event.getInventory().getLocation();
        if (openedLoc == null || openedLoc.getWorld() == null) return;
        if (!openedLoc.getWorld().equals(chestLocation.getWorld())) return;
        if (openedLoc.getBlockX() != chestLocation.getBlockX()
                || openedLoc.getBlockY() != chestLocation.getBlockY()
                || openedLoc.getBlockZ() != chestLocation.getBlockZ()) return;

        if (plugin.getConfig().getBoolean("broadcast-events", true) && event.getPlayer() instanceof Player player) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + player.getName() + " encontró el cofre supremo.");
        }

        stop();
    }

    private void fillChest(Chest chest) {
        List<?> lootList = plugin.getConfig().getList(getId() + ".loot");
        if (lootList == null) return;

        for (Object obj : lootList) {
            if (!(obj instanceof ConfigurationSection section)) {
                // Cuando viene de config.yml como Map
                if (obj instanceof java.util.Map<?, ?> map) {
                    addLootFromMap(chest, map);
                }
                continue;
            }
            String itemName = section.getString("item");
            int min = section.getInt("min", 1);
            int max = section.getInt("max", 1);
            double chance = section.getDouble("chance", 1.0);
            addLootItem(chest, itemName, min, max, chance);
        }
    }

    private void addLootFromMap(Chest chest, java.util.Map<?, ?> map) {
        Object itemObj = map.get("item");
        Object minObj = map.get("min");
        Object maxObj = map.get("max");
        Object chanceObj = map.get("chance");
        if (itemObj == null) return;

        int min = minObj instanceof Number ? ((Number) minObj).intValue() : 1;
        int max = maxObj instanceof Number ? ((Number) maxObj).intValue() : 1;
        double chance = chanceObj instanceof Number ? ((Number) chanceObj).doubleValue() : 1.0;
        addLootItem(chest, itemObj.toString(), min, max, chance);
    }

    private void addLootItem(Chest chest, String itemName, int min, int max, double chance) {
        if (itemName == null) return;

        // Roll de probabilidad: chance=1.0 (default) siempre dropea, igual que el comportamiento original
        if (chance < 1.0 && random.nextDouble() > chance) {
            return;
        }

        // Ítem especial provisto por otro plugin (SistemaClases), en vez de un Material vanilla
        if (itemName.equalsIgnoreCase("SISTEMA_CLASES:ESTRELLA_CAMBIO")) {
            ItemStack estrella = plugin.getSistemaClasesHook().crearEstrellaCambio();
            if (estrella != null) {
                chest.getBlockInventory().addItem(estrella);
                chestHasEstrella = true;
            } else {
                plugin.getLogger().warning("SistemaClases no está disponible; se omitió la Estrella del Cambio del cofre supremo.");
            }
            return;
        }

        try {
            Material material = Material.valueOf(itemName.toUpperCase());
            int amount = min + (max > min ? random.nextInt(max - min + 1) : 0);
            chest.getBlockInventory().addItem(new ItemStack(material, amount));
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Material inválido en loot de supreme-chest: " + itemName);
        }
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        if (endTask != null) endTask.cancel();
        if (reminderTask != null) reminderTask.cancel();

        if (chestLocation != null) {
            Block block = chestLocation.getBlock();
            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
            }
        }

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "El cofre supremo ha desaparecido.");
        }
    }
}
