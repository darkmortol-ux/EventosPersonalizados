package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Cofre Supremo: aparece en un punto aleatorio alrededor de la coordenada 0,0 con loot sorteado.
 *
 * Flujo:
 *  1. Aparece el cofre y se anuncia por chat (con recordatorios periódicos).
 *  2. Un jugador le da click derecho: el cofre SE QUEDA mientras se sacan los ítems.
 *  3. Cuando se cierra el cofre (y nadie más lo tiene abierto), desaparece y el evento termina.
 *     Si quedaban ítems adentro, caen al suelo para que no se pierdan.
 *  4. Si nadie lo abre antes del tiempo límite, desaparece junto con su contenido.
 */
public class SupremeChestEvent implements ServerEvent, Listener {

    /**
     * Escala de "chance" de cada item del loot: 10.0 = 100% de probabilidad de aparecer,
     * 5.0 = 50%, 1.0 = 10%, hasta 0.05 = 0.5% (el mínimo). Cada item de la lista se sortea
     * de forma INDEPENDIENTE (pueden aparecer varios o uno solo; el cofre nunca queda vacío).
     */
    private static final double CHANCE_SCALE = 10.0;

    private record LootEntry(Material material, int min, int max, double chance) {
    }

    private final EventosPlugin plugin;
    private final Random random = new Random();
    private BukkitTask endTask;
    private BukkitTask reminderTask;
    private BukkitTask cleanupTask;
    private Location chestLocation;
    private boolean running = false;
    private boolean opened = false;
    private boolean listenerRegistered = false;

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

        List<Player> onlinePlayers = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        if (onlinePlayers.isEmpty()) return;

        var config = plugin.getConfig();
        int maxDistance = config.getInt(getId() + ".max-distance", 1000);
        int timeLimitMinutes = config.getInt(getId() + ".time-limit-minutes", 10);

        // El mundo de referencia es el primer mundo del servidor (normalmente el overworld),
        // ya que el cofre aparece alrededor de la coordenada 0,0 y no cerca de un jugador.
        World world = plugin.getServer().getWorlds().get(0);

        running = true;
        opened = false;

        chestLocation = pickChestLocation(world, maxDistance);
        int x = chestLocation.getBlockX();
        int y = chestLocation.getBlockY();
        int z = chestLocation.getBlockZ();

        Block block = chestLocation.getBlock();
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) {
            fillChest(chest);
        }

        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }

        if (config.getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "¡Un cofre supremo ha aparecido en " + world.getName()
                    + " (" + x + ", " + y + ", " + z + ")! Tienes " + timeLimitMinutes
                    + " minutos para encontrarlo antes de que desaparezca. "
                    + "Solo haz click derecho para abrirlo.");
        }

        endTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::stop, timeLimitMinutes * 60L * 20L);
        scheduleReminders(world, x, y, z, timeLimitMinutes);
    }

    /**
     * Elige un punto aleatorio dentro de max-distance bloques de la coordenada 0,0.
     * Evita (hasta 15 intentos) sitios donde el cofre quedaría pegado a otro cofre
     * (se fusionaría en un cofre doble) o sobre agua/lava.
     */
    private Location pickChestLocation(World world, int maxDistance) {
        Location fallback = null;
        for (int attempt = 0; attempt < 15; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = random.nextDouble() * maxDistance;
            int x = (int) Math.round(Math.cos(angle) * dist);
            int z = (int) Math.round(Math.sin(angle) * dist);
            int y = world.getHighestBlockYAt(x, z) + 1;

            Location loc = new Location(world, x, y, z);
            if (fallback == null) fallback = loc;
            if (isGoodSpot(loc)) return loc;
        }
        return fallback;
    }

    private boolean isGoodSpot(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        if (loc.getBlockY() >= world.getMaxHeight() - 1) return false;

        Block block = loc.getBlock();
        if (block.getRelative(BlockFace.DOWN).isLiquid()) return false;

        BlockFace[] sides = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : sides) {
            Material neighbor = block.getRelative(face).getType();
            if (neighbor == Material.CHEST || neighbor == Material.TRAPPED_CHEST) return false;
        }
        return true;
    }

    /**
     * Programa los avisos por chat mientras el cofre sigue sin abrirse:
     * - Recuerda las coordenadas cada "reminder-interval-minutes" (2 por defecto)
     * - En los últimos "final-warning-minutes" (2 por defecto) avisa que está por desaparecer,
     *   repitiendo el aviso cada "final-warning-interval-seconds" (30 por defecto)
     */
    private void scheduleReminders(World world, int x, int y, int z, int timeLimitMinutes) {
        var config = plugin.getConfig();
        int reminderIntervalMinutes = config.getInt(getId() + ".reminder-interval-minutes", 2);
        int finalWarningMinutes = config.getInt(getId() + ".final-warning-minutes", 2);
        int finalWarningIntervalSeconds = Math.max(1, config.getInt(getId() + ".final-warning-interval-seconds", 30));

        long reminderIntervalSeconds = Math.max(1, reminderIntervalMinutes * 60L);
        long finalPhaseStartSeconds = Math.max(0, (timeLimitMinutes - finalWarningMinutes) * 60L);
        long totalSeconds = timeLimitMinutes * 60L;
        boolean broadcastEnabled = config.getBoolean("broadcast-events", true);

        final long[] elapsed = {0L};

        reminderTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            elapsed[0]++;
            if (!running || opened || elapsed[0] >= totalSeconds) return;

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

    // ------------------------------------------------------------------
    //  Eventos del cofre: abrir, cerrar, romper, explotar
    // ------------------------------------------------------------------

    /** Primer click derecho: el cofre se abre normal y se queda en su lugar para poder sacar los ítems. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestOpen(InventoryOpenEvent event) {
        if (!running || opened) return;
        if (!isSupremeChest(event.getInventory().getLocation())) return;

        opened = true;

        // Ya fue encontrado: se detienen los avisos y el límite de tiempo.
        if (endTask != null) endTask.cancel();
        if (reminderTask != null) reminderTask.cancel();

        // Respaldo por si nadie lo cierra (ej. el jugador se queda con el cofre abierto sin moverse).
        int cleanupSeconds = Math.max(5, plugin.getConfig().getInt(getId() + ".opened-cleanup-seconds", 120));
        cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::stop, cleanupSeconds * 20L);

        if (plugin.getConfig().getBoolean("broadcast-events", true) && event.getPlayer() instanceof Player player) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + player.getName() + " encontró el cofre supremo.");
        }
    }

    /** Cuando el último jugador cierra el cofre ya abierto, desaparece (lo que sobre cae al suelo). */
    @EventHandler
    public void onChestClose(InventoryCloseEvent event) {
        if (!running || !opened) return;
        if (!isSupremeChest(event.getInventory().getLocation())) return;

        long otherViewers = event.getInventory().getViewers().stream()
                .filter(viewer -> !viewer.equals(event.getPlayer()))
                .count();
        if (otherViewers > 0) return; // todavía hay alguien más mirando adentro

        // Se retira en el siguiente tick para no modificar el bloque en medio del evento de cierre.
        plugin.getServer().getScheduler().runTask(plugin, this::stop);
    }

    /** Si alguien rompe el cofre en vez de abrirlo, los ítems caen como en vanilla y el evento termina. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestBreak(BlockBreakEvent event) {
        if (!running || !isSupremeChest(event.getBlock().getLocation())) return;

        cancelTasks();
        running = false;
        opened = false;
        chestLocation = null;

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + event.getPlayer().getName() + " rompió el cofre supremo.");
        }
    }

    /** El cofre es inmune a explosiones (TNT, creepers, etc.) mientras el evento está activo. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protectFromExplosion(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        protectFromExplosion(event.blockList());
    }

    private void protectFromExplosion(List<Block> blocks) {
        if (!running || chestLocation == null) return;
        blocks.removeIf(block -> isSupremeChest(block.getLocation()));
    }

    private boolean isSupremeChest(Location loc) {
        if (chestLocation == null || loc == null) return false;
        if (loc.getWorld() == null || !loc.getWorld().equals(chestLocation.getWorld())) return false;
        return loc.getBlockX() == chestLocation.getBlockX()
                && loc.getBlockY() == chestLocation.getBlockY()
                && loc.getBlockZ() == chestLocation.getBlockZ();
    }

    // ------------------------------------------------------------------
    //  Loot
    // ------------------------------------------------------------------

    /** Lee supreme-chest.loot de config.yml (acepta tanto Map como ConfigurationSection). */
    private List<LootEntry> readLoot() {
        List<LootEntry> entries = new ArrayList<>();
        List<?> lootList = plugin.getConfig().getList(getId() + ".loot");
        if (lootList == null) return entries;

        for (Object obj : lootList) {
            Object itemObj;
            Object minObj;
            Object maxObj;
            Object chanceObj;

            if (obj instanceof Map<?, ?> map) {
                itemObj = map.get("item");
                minObj = map.get("min");
                maxObj = map.get("max");
                chanceObj = map.get("chance");
            } else if (obj instanceof ConfigurationSection section) {
                itemObj = section.get("item");
                minObj = section.get("min");
                maxObj = section.get("max");
                chanceObj = section.get("chance");
            } else {
                continue;
            }
            if (itemObj == null) continue;

            Material material = Material.matchMaterial(itemObj.toString());
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("Material inválido en loot de supreme-chest: " + itemObj);
                continue;
            }

            int min = Math.max(1, toInt(minObj, 1));
            int max = Math.max(min, toInt(maxObj, min));
            double chance = toDouble(chanceObj, CHANCE_SCALE); // por defecto 100% si no se especifica
            entries.add(new LootEntry(material, min, max, chance));
        }
        return entries;
    }

    private void fillChest(Chest chest) {
        List<LootEntry> entries = readLoot();
        if (entries.isEmpty()) {
            plugin.getLogger().warning("supreme-chest.loot está vacío o es inválido en config.yml: el cofre aparecerá sin ítems.");
            return;
        }

        Inventory inventory = chest.getBlockInventory();
        int added = 0;

        for (LootEntry entry : entries) {
            // Sorteo independiente de probabilidad: chance/10.0 = fracción (10.0 -> 1.0 = 100%)
            double probability = Math.max(0.0, Math.min(entry.chance(), CHANCE_SCALE)) / CHANCE_SCALE;
            if (random.nextDouble() < probability) {
                inventory.addItem(buildStack(entry));
                added++;
            }
        }

        // Garantiza que el cofre nunca quede vacío: si ningún ítem pasó su sorteo,
        // se agrega el de mayor probabilidad.
        if (added == 0) {
            LootEntry best = entries.get(0);
            for (LootEntry entry : entries) {
                if (entry.chance() > best.chance()) best = entry;
            }
            inventory.addItem(buildStack(best));
        }
    }

    private ItemStack buildStack(LootEntry entry) {
        int amount = entry.min() + (entry.max() > entry.min() ? random.nextInt(entry.max() - entry.min() + 1) : 0);
        return new ItemStack(entry.material(), amount);
    }

    private static int toInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double toDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    // ------------------------------------------------------------------
    //  Fin del evento
    // ------------------------------------------------------------------

    private void cancelTasks() {
        if (endTask != null) endTask.cancel();
        if (reminderTask != null) reminderTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
    }

    /**
     * Quita el bloque del cofre. Cierra el inventario a quien lo tenga abierto y, si
     * dropLeftovers es true, lo que quedaba adentro cae al suelo en vez de perderse.
     */
    private void removeChest(boolean dropLeftovers) {
        if (chestLocation == null) return;

        Block block = chestLocation.getBlock();
        if (block.getType() == Material.CHEST && block.getState() instanceof Chest chest) {
            Inventory inventory = chest.getBlockInventory();

            for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
                viewer.closeInventory();
            }

            if (dropLeftovers && chestLocation.getWorld() != null) {
                Location dropAt = chestLocation.clone().add(0.5, 0.5, 0.5);
                for (ItemStack item : inventory.getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        chestLocation.getWorld().dropItemNaturally(dropAt, item);
                    }
                }
            }

            inventory.clear();
            block.setType(Material.AIR);
        }
        chestLocation = null;
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        cancelTasks();

        boolean wasOpened = opened;
        opened = false;

        // Si ya lo abrieron, lo que sobre cae al suelo. Si el tiempo se acabó sin abrirse,
        // el cofre desaparece con todo su contenido.
        removeChest(wasOpened);

        if (!wasOpened && plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "El cofre supremo ha desaparecido.");
        }
    }
}
