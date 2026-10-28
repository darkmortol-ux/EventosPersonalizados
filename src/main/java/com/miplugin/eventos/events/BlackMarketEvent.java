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
    private boolean running = false;
    private boolean listenerRegistered = false;

    public BlackMarketEvent(EventosPlugin plugin) {
        this.plugin = plugin;
        this.merchantKey = new NamespacedKey(plugin, "mercado_negro_npc");
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
        return true;
    }

    @Override
    public void start() {
        if (running) return;
        running = true;

        loadOffers();

        var config = plugin.getConfig();
        int durationSeconds = config.getInt(getId() + ".duration-seconds", 300);

        Location spawnLoc = resolveSpawnLocation();
        if (spawnLoc == null) {
            plugin.getLogger().warning("Mercado Negro: no hay ubicación asignada y no se encontró un mundo válido. "
                    + "Usa /eventos setloc " + getId() + " para asignar una.");
            running = false;
            return;
        }

        Villager merchant = spawnLoc.getWorld().spawn(spawnLoc, Villager.class);
        merchant.setCustomName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Comerciante Misterioso");
        merchant.setCustomNameVisible(true);
        merchant.setProfession(Villager.Profession.NITWIT); // evita que ofrezca trades vanilla
        merchant.setAI(false);
        merchant.setInvulnerable(true);
        merchant.setSilent(false);
        merchant.setCollidable(false);
        merchant.getPersistentDataContainer().set(merchantKey, PersistentDataType.BYTE, (byte) 1);
        merchantId = merchant.getUniqueId();

        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.LIGHT_PURPLE
                    + "Un comerciante misterioso apareció en " + spawnLoc.getWorld().getName()
                    + " (" + spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ() + "). "
                    + ChatColor.GRAY + "Tiene ofertas limitadas, ¡corre antes de que se acaben!");
        }

        endTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::stop, durationSeconds * 20L);
    }

    /** Usa la ubicación asignada con /eventos setloc; si no hay ninguna, cae al spawn del primer mundo. */
    private Location resolveSpawnLocation() {
        Location custom = plugin.getLocationManager().getLocation(getId());
        if (custom != null && custom.getWorld() != null) {
            return custom;
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

    public List<BlackMarketOffer> getOffers() {
        return offers;
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        if (endTask != null) endTask.cancel();

        if (merchantId != null) {
            for (World world : plugin.getServer().getWorlds()) {
                world.getEntities().stream()
                        .filter(e -> e.getUniqueId().equals(merchantId))
                        .findFirst()
                        .ifPresent(Entity::remove);
            }
            merchantId = null;
        }

        plugin.getBlackMarketMenu().closeAllViewers();

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "El Mercado Negro ha cerrado. El comerciante desapareció.");
        }
    }
}
