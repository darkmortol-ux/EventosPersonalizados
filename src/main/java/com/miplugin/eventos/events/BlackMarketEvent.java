package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import com.miplugin.eventos.gui.BlackMarketMenu;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlackMarketEvent implements ServerEvent, Listener {

    private final EventosPlugin plugin;
    private final NamespacedKey merchantKey;
    private final List<BlackMarketOffer> offers = new ArrayList<>();

    private UUID merchantId;
    private BukkitTask endTask;
    private BukkitTask watchdogTask;
    private World ticketWorld;   // chunk que se mantiene cargado mientras dura el evento
    private int ticketChunkX;
    private int ticketChunkZ;
    private boolean running = false;
    private boolean listenerRegistered = false;

    public BlackMarketEvent(EventosPlugin plugin) {
        this.plugin = plugin;
        this.merchantKey = new NamespacedKey(plugin, "mercado_negro_npc");

        // El listener se registra desde el arranque (no hasta el primer start) para poder limpiar
        // comerciantes "huérfanos" que hayan quedado guardados en el mundo de ejecuciones anteriores.
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        listenerRegistered = true;
        plugin.getServer().getScheduler().runTask(plugin, this::removeOrphanMerchants);
    }

    @Override
    public String getId() {
        return "mercado-negro";
    }

    @Override
    public String getDisplayName() {
        return ChatColor.DARK_PURPLE + "Mercado Negro" + ChatColor.RESET;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean canStart() {
        // Sin jugadores conectados no tiene sentido abrir el mercado (se gastaría el turno del evento).
        if (plugin.getServer().getOnlinePlayers().isEmpty()) return false;

        // Tampoco si no hay ofertas o no hay dónde ponerlo: así no se anuncia "Comienza" sin que abra.
        List<?> rawOffers = plugin.getConfig().getList(getId() + ".ofertas");
        if (rawOffers == null || rawOffers.isEmpty()) return false;
        return resolveSpawnLocation() != null;
    }

    @Override
    public void start() {
        if (running) return;

        loadOffers();
        if (offers.isEmpty()) {
            plugin.getLogger().warning("Mercado Negro: no hay ofertas válidas en config.yml (mercado-negro.ofertas); el mercado no se abre.");
            return;
        }

        Location spawnLoc = resolveSpawnLocation();
        if (spawnLoc == null || spawnLoc.getWorld() == null) {
            plugin.getLogger().warning("Mercado Negro: no hay ubicación asignada y no se encontró un mundo válido. "
                    + "Usa /eventos setloc " + getId() + " para asignar una.");
            return;
        }

        int durationSeconds = Math.max(10, plugin.getConfig().getInt(getId() + ".duration-seconds", 300));

        // Primero se crea al aldeano y SOLO si funciona se marca el evento como activo. Antes se marcaba
        // activo desde el principio: si el spawn fallaba, el evento quedaba "pegado" sin aldeano ni
        // temporizador y bloqueaba TODOS los demás eventos (regla de 1 evento a la vez).
        // El chunk del mercado se carga y se mantiene cargado ANTES de crear al aldeano. Tras un
        // reinicio (o si nadie está cerca) ese chunk suele estar descargado, y crear una entidad en
        // un chunk descargado puede fallar o dejarla sin activar.
        holdChunk(spawnLoc);
        spawnLoc.getChunk().load(true);

        Villager merchant;
        try {
            merchant = spawnLoc.getWorld().spawn(spawnLoc, Villager.class);
        } catch (Exception e) {
            releaseChunk();
            plugin.getLogger().warning("Mercado Negro: no se pudo aparecer al comerciante en " + describe(spawnLoc)
                    + " (" + e.getMessage() + "). Si esa zona es una región de WorldGuard, revisa que no tenga "
                    + "'mob-spawning deny'.");
            return;
        }
        if (!merchant.isValid()) {
            merchant.remove();
            releaseChunk();
            plugin.getLogger().warning("Mercado Negro: otro plugin canceló la aparición del comerciante en "
                    + describe(spawnLoc) + ". Si es una región de WorldGuard, revisa el flag 'mob-spawning'.");
            return;
        }

        running = true;
        try {
            merchant.setCustomName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Comerciante Misterioso");
            merchant.setCustomNameVisible(true);
            merchant.setProfession(Villager.Profession.NITWIT); // evita que ofrezca trades vanilla
            merchant.setAI(false);
            merchant.setInvulnerable(true);
            merchant.setSilent(false);
            merchant.setCollidable(false);
            merchant.getPersistentDataContainer().set(merchantKey, PersistentDataType.BYTE, (byte) 1);
            // No se guarda en disco: si el servidor se reinicia o se cae a mitad del evento,
            // el comerciante simplemente no existe después (nunca queda uno "huérfano" en el mapa).
            merchant.setPersistent(false);
            merchantId = merchant.getUniqueId();

            if (plugin.getConfig().getBoolean("broadcast-events", true)) {
                plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.LIGHT_PURPLE
                        + "Un comerciante misterioso apareció en " + describe(spawnLoc) + ". "
                        + ChatColor.GRAY + "Tiene ofertas limitadas, ¡corre antes de que se acaben!");
            }

            endTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::stop, durationSeconds * 20L);
            // Vigilante: si algo borra al comerciante a mitad del evento, el mercado no se queda "abierto" vacío.
            watchdogTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkMerchantAlive, 100L, 100L);
        } catch (Exception e) {
            plugin.getLogger().warning("Mercado Negro: error al abrir el mercado: " + e.getMessage());
            cancelTasks();
            merchant.remove();
            releaseChunk();
            merchantId = null;
            running = false;
        }
    }

    private String describe(Location loc) {
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private void cancelTasks() {
        if (endTask != null) endTask.cancel();
        if (watchdogTask != null) watchdogTask.cancel();
    }

    private void checkMerchantAlive() {
        if (!running || merchantId == null) return;
        Entity merchant = plugin.getServer().getEntity(merchantId);
        if (merchant == null || !merchant.isValid()) {
            plugin.getLogger().warning("Mercado Negro: el comerciante desapareció antes de tiempo "
                    + "(¿un plugin que limpia entidades?). Se cierra el mercado.");
            stop();
        }
    }

    /** Usa la ubicación asignada con /eventos setloc; si no hay ninguna, cae al spawn del primer mundo. */
    private Location resolveSpawnLocation() {
        Location custom = plugin.getLocationManager().getLocation(getId());
        if (custom != null) {
            return custom;
        }
        if (plugin.getLocationManager().hasLocation(getId())) {
            plugin.getLogger().warning("Mercado Negro: la ubicación guardada está en el mundo '"
                    + plugin.getLocationManager().getWorldName(getId())
                    + "', que no está cargado ahora mismo. Se usa el spawn del primer mundo como respaldo.");
        }
        World fallbackWorld = plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().get(0);
        return fallbackWorld != null ? fallbackWorld.getSpawnLocation() : null;
    }

    @SuppressWarnings("unchecked")
    private void loadOffers() {
        offers.clear();
        List<?> rawOffers = plugin.getConfig().getList(getId() + ".ofertas");
        if (rawOffers == null) return;

        for (Object obj : rawOffers) {
            if (!(obj instanceof Map<?, ?> map)) continue;

            Object itemObj = map.get("item");
            if (itemObj == null) continue;

            Material material;
            try {
                material = Material.valueOf(itemObj.toString().toUpperCase());
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Mercado Negro: material inválido en config: " + itemObj);
                continue;
            }

            int amount = map.get("amount") instanceof Number n ? n.intValue() : 1;
            double price = map.get("precio") instanceof Number n ? n.doubleValue() : 0;
            int stock = map.get("stock") instanceof Number n ? n.intValue() : 1;

            Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
            Object enchantObj = map.get("encantamientos");
            if (enchantObj instanceof Map<?, ?> enchantMap) {
                for (Map.Entry<?, ?> entry : enchantMap.entrySet()) {
                    Enchantment enchantment = resolveEnchantment(entry.getKey().toString());
                    if (enchantment == null) {
                        plugin.getLogger().warning("Mercado Negro: encantamiento inválido: " + entry.getKey());
                        continue;
                    }
                    int level = entry.getValue() instanceof Number n ? n.intValue() : 1;
                    enchants.put(enchantment, level);
                }
            }

            offers.add(new BlackMarketOffer(material, amount, price, stock, enchants));
        }
    }

    private Enchantment resolveEnchantment(String name) {
        try {
            NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase());
            return org.bukkit.Registry.ENCHANTMENT.get(key);
        } catch (Exception e) {
            return null;
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!running || merchantId == null) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // el evento se dispara una vez por cada mano
        Entity entity = event.getRightClicked();
        if (!entity.getUniqueId().equals(merchantId)) return;

        event.setCancelled(true);
        if (event.getPlayer() instanceof Player player) {
            plugin.getBlackMarketMenu().open(player, this);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!running || merchantId == null) return;
        if (event.getEntity().getUniqueId().equals(merchantId)) {
            event.setCancelled(true); // el comerciante es invulnerable, pero por si acaso con daño "true"
        }
    }

    // ------------------------------------------------------------------
    //  Chunk cargado y limpieza de comerciantes huérfanos
    // ------------------------------------------------------------------

    private void holdChunk(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        ticketWorld = world;
        ticketChunkX = loc.getBlockX() >> 4;
        ticketChunkZ = loc.getBlockZ() >> 4;
        world.addPluginChunkTicket(ticketChunkX, ticketChunkZ, plugin);
    }

    private void releaseChunk() {
        if (ticketWorld == null) return;
        try {
            ticketWorld.removePluginChunkTicket(ticketChunkX, ticketChunkZ, plugin);
        } catch (Exception ignored) {
            // el mundo pudo descargarse; no pasa nada
        }
        ticketWorld = null;
    }

    /** true si la entidad es un comerciante del mercado que NO pertenece al evento actual. */
    private boolean isOrphanMerchant(Entity entity) {
        if (!entity.getPersistentDataContainer().has(merchantKey, PersistentDataType.BYTE)) return false;
        return merchantId == null || !entity.getUniqueId().equals(merchantId);
    }

    /** Quita comerciantes que quedaron guardados de versiones anteriores del plugin (en chunks ya cargados). */
    private void removeOrphanMerchants() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (isOrphanMerchant(villager)) villager.remove();
            }
        }
    }

    /** Lo mismo, pero para los chunks que se vayan cargando después del arranque. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isOrphanMerchant(entity)) {
                // Un tick después, para no modificar entidades en medio de la carga del chunk.
                plugin.getServer().getScheduler().runTask(plugin, entity::remove);
            }
        }
    }

    public List<BlackMarketOffer> getOffers() {
        return offers;
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        cancelTasks();

        if (merchantId != null) {
            Entity merchant = plugin.getServer().getEntity(merchantId);
            if (merchant != null) merchant.remove();
            merchantId = null;
        }
        releaseChunk();

        plugin.getBlackMarketMenu().closeAllViewers();

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "El Mercado Negro ha cerrado. El comerciante desapareció.");
        }
    }
}
