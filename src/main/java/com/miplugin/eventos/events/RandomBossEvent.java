package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Jefe Aleatorio: aparece un jefe cerca de un jugador al azar. Quien lo derrota recibe
 * dinero (Vault), ítems y, con la probabilidad "essence-drop-chance", una Esencia de Clase
 * aleatoria de SistemaClases (pedida por reflection, sin dependencia al compilar).
 *
 * Como solo puede haber 1 evento activo a la vez, el jefe SIEMPRE tiene un límite de
 * tiempo (duration-seconds) y un vigilante que termina el evento si el jefe desaparece;
 * así nunca puede quedar el evento "pegado" bloqueando a los demás.
 */
public class RandomBossEvent implements ServerEvent, Listener {

    /** Nombre del plugin en su plugin.yml (name:), no el del repositorio. */
    private static final String SISTEMA_CLASES = "SistemaClases";

    /** Resultado de intentar entregar una esencia (se usa también en /eventos esencia). */
    public enum EssenceResult {
        GIVEN,
        DISABLED,
        NO_DROP,
        PLUGIN_MISSING,
        FAILED
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

        giveEssence(killer, false);
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
        giveOrDrop(player, new ItemStack(material, amount));
    }

    /** Si el inventario está lleno, lo que no cabe cae al suelo en vez de perderse. */
    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack left : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    // ------------------------------------------------------------------
    //  Esencias de SistemaClases (reflection, sin dependencia al compilar)
    // ------------------------------------------------------------------

    /**
     * Probabilidad (0.0 a 1.0) de que el jefe suelte una esencia. En config.yml:
     * essence-drop-chance: 0.15 = 15%. Si se escribe un número mayor a 1 se lee como
     * porcentaje (15 = 15%). 0 la desactiva.
     */
    private double readDropChance() {
        double chance = plugin.getConfig().getDouble(getId() + ".essence-drop-chance", 0.15);
        if (chance > 1.0) chance = chance / 100.0;
        return Math.max(0.0, Math.min(1.0, chance));
    }

    /**
     * Entrega una Esencia de Clase aleatoria (la elige SistemaClases) a un jugador.
     *
     * @param force true = ignora el porcentaje de drop (lo usa /eventos esencia para probar la conexión)
     */
    public EssenceResult giveEssence(Player player, boolean force) {
        double chance = readDropChance();
        if (!force && chance <= 0.0) return EssenceResult.DISABLED;

        Plugin sistemaClases = plugin.getServer().getPluginManager().getPlugin(SISTEMA_CLASES);
        if (sistemaClases == null || !sistemaClases.isEnabled()) {
            plugin.getLogger().warning("SistemaClases no está instalado o no está activo; se omitió el drop de esencia del jefe.");
            return EssenceResult.PLUGIN_MISSING;
        }

        if (!force && random.nextDouble() >= chance) return EssenceResult.NO_DROP;

        ItemStack essence = createRandomEssence(sistemaClases);
        if (essence == null) return EssenceResult.FAILED;

        giveOrDrop(player, essence);
        String name = essence.hasItemMeta() && essence.getItemMeta().hasDisplayName()
                ? essence.getItemMeta().getDisplayName()
                : "una esencia de clase";
        player.sendMessage(ChatColor.LIGHT_PURPLE + "¡El jefe dejó caer " + name + ChatColor.LIGHT_PURPLE + "!");
        return EssenceResult.GIVEN;
    }

    /**
     * SistemaClases.getEsenciaService().crearAleatoria() vía reflection.
     * Devuelve null si algo falla (el motivo queda en la consola).
     */
    private ItemStack createRandomEssence(Plugin sistemaClases) {
        try {
            Method getService = sistemaClases.getClass().getMethod("getEsenciaService");
            Object service = getService.invoke(sistemaClases);
            if (service == null) {
                plugin.getLogger().warning("SistemaClases devolvió un EsenciaService nulo.");
                return null;
            }
            Method create = service.getClass().getMethod("crearAleatoria");
            Object result = create.invoke(service);
            if (result instanceof ItemStack item) return item;

            plugin.getLogger().warning("SistemaClases.crearAleatoria() no devolvió un ItemStack.");
            return null;
        } catch (Exception e) {
            Throwable cause = (e instanceof InvocationTargetException ite && ite.getCause() != null) ? ite.getCause() : e;
            plugin.getLogger().warning("No se pudo obtener la esencia de SistemaClases: "
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            return null;
        }
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
