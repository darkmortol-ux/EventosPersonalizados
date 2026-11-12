package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Jefe Aleatorio: aparece un jefe cerca de un jugador al azar. Quien lo derrota recibe
 * dinero (Vault), ítems y, si está activada la integración con SistemaClases, 1 esencia
 * de clase elegida por porcentaje.
 *
 * Como solo puede haber 1 evento activo a la vez, el jefe SIEMPRE tiene un límite de
 * tiempo (duration-seconds) y un vigilante que termina el evento si el jefe desaparece;
 * así nunca puede quedar el evento "pegado" bloqueando a los demás.
 */
public class RandomBossEvent implements ServerEvent, Listener {

    /** Resultado de intentar entregar una esencia (se usa también en /eventos esencia). */
    public enum EssenceResult {
        GIVEN,
        DISABLED,
        NO_DROP,
        PLUGIN_MISSING,
        NO_ESSENCES,
        NO_COMMAND,
        COMMAND_FAILED
    }

    private record EssenceEntry(String id, String name, double chance) {
    }

    private final EventosPlugin plugin;
    private final Random random = new Random();
    private UUID currentBossId;
    private BukkitTask endTask;
    private BukkitTask watchdogTask;
    private boolean running = false;
    private boolean listenerRegistered = false;

    private static final EntityType[] BOSS_TYPES = {
            EntityType.WITHER_SKELETON, EntityType.RAVAGER, EntityType.EVOKER, EntityType.VINDICATOR
    };

    public RandomBossEvent(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "random-boss";
    }

    @Override
    public String getDisplayName() {
        return ChatColor.DARK_RED + "Jefe Aleatorio" + ChatColor.RESET;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean canStart() {
        int minPlayers = plugin.getConfig().getInt(getId() + ".min-players", 3);
        return plugin.getServer().getOnlinePlayers().size() >= minPlayers;
    }

    @Override
    public void start() {
        if (running) return;
        List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        if (players.isEmpty()) return;

        var config = plugin.getConfig();
        double bossHealth = config.getDouble(getId() + ".boss-health", 200.0);
        double bossDamage = config.getDouble(getId() + ".boss-damage", 8.0);
        int durationSeconds = Math.max(30, config.getInt(getId() + ".duration-seconds", 900));

        Player target = players.get(random.nextInt(players.size()));
        World world = target.getWorld();
        Location spawnLoc = findSpawnLocation(target.getLocation());

        EntityType type = BOSS_TYPES[random.nextInt(BOSS_TYPES.length)];
        Entity spawned = world.spawnEntity(spawnLoc, type);
        if (!(spawned instanceof LivingEntity boss)) {
            spawned.remove();
            return;
        }

        running = true;

        boss.setCustomName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Jefe del Servidor");
        boss.setCustomNameVisible(true);
        // No se guarda en disco (no queda huérfano si el servidor se cae) y no se despawnea al alejarse.
        boss.setPersistent(false);
        if (boss instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
        }

        var healthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(bossHealth);
            boss.setHealth(bossHealth);
        }
        var dmgAttr = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.setBaseValue(bossDamage);
        }

        currentBossId = boss.getUniqueId();

        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }

        // Límite de tiempo: si nadie lo derrota, el jefe escapa y el evento termina.
        endTask = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> endEvent("Nadie derrotó al jefe a tiempo: el jefe del servidor escapó.", true),
                durationSeconds * 20L);
        // Vigilante: si el jefe desaparece por cualquier causa, el evento no se queda pegado.
        watchdogTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkBossAlive, 100L, 100L);

        if (config.getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.RED
                    + "¡Un jefe ha aparecido cerca de " + target.getName() + "! Prepárense para el combate. "
                    + "Tienen " + (durationSeconds / 60) + " minutos para derrotarlo.");
        }
    }

    /** Aparece cerca del jugador, pero nunca dentro de bloques sólidos. */
    private Location findSpawnLocation(Location base) {
        Location candidate = base.clone().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5);
        if (candidate.getBlock().isPassable() && candidate.clone().add(0, 1, 0).getBlock().isPassable()) {
            return candidate;
        }
        return base.clone();
    }

    private void checkBossAlive() {
        if (!running || currentBossId == null) return;
        Entity boss = plugin.getServer().getEntity(currentBossId);
        if (boss == null || !boss.isValid()) {
            endEvent("El jefe del servidor desapareció.", false);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!running || currentBossId == null) return;
        if (!event.getEntity().getUniqueId().equals(currentBossId)) return;

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            giveRewards(killer);
        }

        String message = killer != null
                ? killer.getName() + " derrotó al jefe del servidor."
                : "El jefe del servidor ha muerto.";
        endEvent(message, false);
    }

    // ------------------------------------------------------------------
    //  Recompensas
    // ------------------------------------------------------------------

    private void giveRewards(Player killer) {
        var config = plugin.getConfig();
        int rewardMoney = config.getInt(getId() + ".reward-money", 0);
        List<?> rewardItems = config.getList(getId() + ".reward-items");

        if (rewardMoney > 0) {
            boolean paid = plugin.getEconomyManager().deposit(killer, rewardMoney);
            if (paid) {
                killer.sendMessage(ChatColor.GREEN + "¡Has ganado "
                        + plugin.getEconomyManager().format(rewardMoney)
                        + " por derrotar al jefe!");
            } else {
                killer.sendMessage(ChatColor.GREEN + "¡Has derrotado al jefe! "
                        + ChatColor.GRAY + "(recompensa en dinero no disponible: instala Vault + un plugin de economía)");
            }
        }
        if (rewardItems != null) {
            for (Object obj : rewardItems) {
                if (obj instanceof Map<?, ?> map) {
                    giveRewardItem(killer, map);
                }
            }
        }

        EssenceResult result = giveEssence(killer, false);
        if (result == EssenceResult.NO_DROP) {
            killer.sendMessage(ChatColor.GRAY + "El jefe no soltó ninguna esencia esta vez.");
        }
    }

    private void giveRewardItem(Player player, Map<?, ?> map) {
        Object itemObj = map.get("item");
        if (itemObj == null) return;
        Material material = Material.matchMaterial(itemObj.toString());
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("Material inválido en reward-items de random-boss: " + itemObj);
            return;
        }
        int min = map.get("min") instanceof Number ? ((Number) map.get("min")).intValue() : 1;
        int max = map.get("max") instanceof Number ? ((Number) map.get("max")).intValue() : 1;
        int amount = min + (max > min ? random.nextInt(max - min + 1) : 0);

        // Si el inventario está lleno, lo que no cabe cae al suelo en vez de perderse.
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(material, amount));
        for (ItemStack left : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    // ------------------------------------------------------------------
    //  Integración con SistemaClases (esencias de clase por porcentaje)
    // ------------------------------------------------------------------

    /**
     * Entrega 1 esencia de clase a un jugador, elegida entre las configuradas en
     * random-boss.sistema-clases.essences según su porcentaje. La entrega real la hace
     * SistemaClases mediante el comando de consola configurado (sin dependencia de compilación).
     *
     * @param force true = ignora "enabled" y "drop-chance" (lo usa /eventos esencia para probar la config)
     */
    public EssenceResult giveEssence(Player player, boolean force) {
        var config = plugin.getConfig();
        String base = getId() + ".sistema-clases";

        if (!force && !config.getBoolean(base + ".enabled", false)) {
            return EssenceResult.DISABLED;
        }

        String pluginName = config.getString(base + ".plugin-name", "SistemaClases");
        var target = plugin.getServer().getPluginManager().getPlugin(pluginName);
        if (target == null || !target.isEnabled()) {
            plugin.getLogger().warning("No se entregó esencia: el plugin '" + pluginName
                    + "' no está instalado o no está activo (revisa 'plugin-name' en config.yml; "
                    + "debe ser el nombre del plugin.yml, no el del repositorio).");
            return EssenceResult.PLUGIN_MISSING;
        }

        if (!force) {
            double dropChance = Math.max(0.0, Math.min(100.0, config.getDouble(base + ".drop-chance", 100.0)));
            if (random.nextDouble() * 100.0 >= dropChance) {
                return EssenceResult.NO_DROP;
            }
        }

        EssenceEntry essence = pickEssence(readEssences(base));
        if (essence == null) {
            plugin.getLogger().warning("random-boss.sistema-clases.essences está vacío o todos los porcentajes son 0.");
            return EssenceResult.NO_ESSENCES;
        }

        String command = config.getString(base + ".command", "");
        if (command == null || command.isBlank()) {
            plugin.getLogger().warning("random-boss.sistema-clases.command está vacío: no se sabe cómo entregar la esencia.");
            return EssenceResult.NO_COMMAND;
        }
        command = command.trim();
        if (command.startsWith("/")) command = command.substring(1);
        command = command.replace("{player}", player.getName()).replace("{class}", essence.id());

        boolean ok;
        try {
            ok = plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
        } catch (Exception e) {
            plugin.getLogger().warning("Error al ejecutar el comando de esencia '" + command + "': " + e.getMessage());
            return EssenceResult.COMMAND_FAILED;
        }
        if (!ok) {
            plugin.getLogger().warning("El comando de esencia '" + command + "' no se ejecutó correctamente. "
                    + "Pruébalo tú mismo en la consola y corrige 'command' en config.yml.");
            return EssenceResult.COMMAND_FAILED;
        }

        player.sendMessage(ChatColor.LIGHT_PURPLE + "¡Obtuviste la Esencia de " + essence.name() + "!");
        if (config.getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.LIGHT_PURPLE
                    + player.getName() + " obtuvo la Esencia de " + essence.name() + ".");
        }
        return EssenceResult.GIVEN;
    }

    private List<EssenceEntry> readEssences(String base) {
        List<EssenceEntry> entries = new ArrayList<>();
        List<?> list = plugin.getConfig().getList(base + ".essences");
        if (list == null) return entries;

        for (Object obj : list) {
            Object idObj;
            Object nameObj;
            Object chanceObj;

            if (obj instanceof Map<?, ?> map) {
                idObj = map.get("id");
                nameObj = map.get("name");
                chanceObj = map.get("chance");
            } else if (obj instanceof ConfigurationSection section) {
                idObj = section.get("id");
                nameObj = section.get("name");
                chanceObj = section.get("chance");
            } else {
                continue;
            }
            if (idObj == null) continue;

            String id = idObj.toString().trim();
            if (id.isEmpty()) continue;
            String name = nameObj != null ? nameObj.toString() : id.substring(0, 1).toUpperCase() + id.substring(1);
            double chance = chanceObj instanceof Number number ? number.doubleValue() : 0.0;
            entries.add(new EssenceEntry(id, name, chance));
        }
        return entries;
    }

    /** Elige exactamente 1 esencia con probabilidad proporcional a su "chance" (no hace falta que sumen 100). */
    private EssenceEntry pickEssence(List<EssenceEntry> entries) {
        double total = 0.0;
        for (EssenceEntry entry : entries) {
            if (entry.chance() > 0) total += entry.chance();
        }
        if (total <= 0) return null;

        double roll = random.nextDouble() * total;
        double cumulative = 0.0;
        EssenceEntry last = null;
        for (EssenceEntry entry : entries) {
            if (entry.chance() <= 0) continue;
            cumulative += entry.chance();
            last = entry;
            if (roll < cumulative) return entry;
        }
        return last;
    }

    // ------------------------------------------------------------------
    //  Fin del evento
    // ------------------------------------------------------------------

    private void cancelTasks() {
        if (endTask != null) endTask.cancel();
        if (watchdogTask != null) watchdogTask.cancel();
    }

    private void removeBoss() {
        if (currentBossId == null) return;
        Entity boss = plugin.getServer().getEntity(currentBossId);
        if (boss != null) boss.remove();
    }

    private void endEvent(String message, boolean removeBoss) {
        if (!running) return;
        running = false;
        cancelTasks();
        if (removeBoss) removeBoss();
        currentBossId = null;

        if (message != null && plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW + message);
        }
    }

    @Override
    public void stop() {
        endEvent("El evento del jefe ha terminado.", true);
    }
}
