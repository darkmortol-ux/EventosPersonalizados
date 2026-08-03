package com.miplugin.eventos.events;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ParkourRaceEvent implements ServerEvent, Listener {

    private final EventosPlugin plugin;
    private final Random random = new Random();
    private BukkitTask endTask;
    private boolean running = false;
    private boolean listenerRegistered = false;

    private List<Location> checkpointLocations;
    private final Map<Player, Integer> playerProgress = new LinkedHashMap<>();
    private final Map<Player, Long> finishTimes = new LinkedHashMap<>();
    private long raceStartMillis;

    public ParkourRaceEvent(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "parkour-race";
    }

    @Override
    public String getDisplayName() {
        return ChatColor.BLUE + "Carrera de Parkour" + ChatColor.RESET;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean canStart() {
        int minPlayers = plugin.getConfig().getInt(getId() + ".min-players", 2);
        return plugin.getServer().getOnlinePlayers().size() >= minPlayers;
    }

    @Override
    public void start() {
        if (running) return;
        List<Player> players = new java.util.ArrayList<>(plugin.getServer().getOnlinePlayers());
        if (players.isEmpty()) return;

        running = true;
        var config = plugin.getConfig();
        int durationSeconds = config.getInt(getId() + ".duration-seconds", 180);
        int checkpoints = config.getInt(getId() + ".checkpoints", 8);

        // Genera la pista a partir del primer jugador conectado
        Player origin = players.get(0);
        World world = origin.getWorld();
        Location start = origin.getLocation().add(0, 1, 5);
        checkpointLocations = generateTrack(world, start, checkpoints);

        playerProgress.clear();
        finishTimes.clear();
        raceStartMillis = System.currentTimeMillis();

        for (Player player : players) {
            player.teleport(checkpointLocations.get(0).clone().add(0, 1, 0));
            playerProgress.put(player, 0);
            player.sendMessage(ChatColor.BLUE + "¡La carrera de parkour ha comenzado! Llega a la última plataforma de lana roja.");
        }

        if (!listenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }

        endTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::stop, durationSeconds * 20L);
    }

    private List<Location> generateTrack(World world, Location start, int checkpoints) {
        List<Location> track = new java.util.ArrayList<>();
        Location current = start.clone();
        track.add(placePlatform(current, Material.LIME_WOOL));

        for (int i = 1; i < checkpoints; i++) {
            double dx = 2 + random.nextInt(3);
            double dy = random.nextInt(3) - 1;
            current = current.clone().add(dx, dy, 0);
            Material color = (i == checkpoints - 1) ? Material.RED_WOOL : Material.WHITE_WOOL;
            track.add(placePlatform(current, color));
        }
        return track;
    }

    private Location placePlatform(Location loc, Material material) {
        Location blockLoc = loc.clone();
        blockLoc.getBlock().setType(material);
        return blockLoc;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!running || checkpointLocations == null) return;
        Player player = event.getPlayer();
        if (!playerProgress.containsKey(player)) return;
        if (finishTimes.containsKey(player)) return;

        int progress = playerProgress.get(player);
        if (progress >= checkpointLocations.size() - 1) return;

        Location next = checkpointLocations.get(progress + 1);
        Location playerLoc = player.getLocation();

        if (playerLoc.getBlockX() == next.getBlockX()
                && playerLoc.getBlockZ() == next.getBlockZ()
                && Math.abs(playerLoc.getBlockY() - next.getBlockY()) <= 2) {

            playerProgress.put(player, progress + 1);

            if (progress + 1 == checkpointLocations.size() - 1) {
                long timeMillis = System.currentTimeMillis() - raceStartMillis;
                finishTimes.put(player, timeMillis);
                announceFinish(player, timeMillis);

                if (finishTimes.size() >= playerProgress.size()) {
                    stop();
                }
            }
        }
    }

    private void announceFinish(Player player, long timeMillis) {
        int place = finishTimes.size();
        double seconds = timeMillis / 1000.0;
        String placeText = switch (place) {
            case 1 -> ChatColor.GOLD + "1er lugar";
            case 2 -> ChatColor.GRAY + "2do lugar";
            case 3 -> net.md_5.bungee.api.ChatColor.of("#CD7F32") + "3er lugar";
            default -> place + "° lugar";
        };

        plugin.getServer().broadcastMessage(ChatColor.BLUE + "[Parkour] " + ChatColor.WHITE
                + player.getName() + " terminó en " + placeText + ChatColor.WHITE
                + " (" + String.format("%.1f", seconds) + "s)");

        var config = plugin.getConfig();
        int reward = switch (place) {
            case 1 -> config.getInt(getId() + ".reward-first", 0);
            case 2 -> config.getInt(getId() + ".reward-second", 0);
            case 3 -> config.getInt(getId() + ".reward-third", 0);
            default -> 0;
        };
        if (reward > 0) {
            boolean paid = plugin.getEconomyManager().deposit(player, reward);
            if (paid) {
                player.sendMessage(ChatColor.GREEN + "¡Ganaste " + plugin.getEconomyManager().format(reward) + "!");
            } else {
                player.sendMessage(ChatColor.GREEN + "¡Buen tiempo! "
                        + ChatColor.GRAY + "(recompensa en dinero no disponible: instala Vault + un plugin de economía)");
            }
        }
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        if (endTask != null) endTask.cancel();

        // Limpia las plataformas
        if (checkpointLocations != null) {
            for (Location loc : checkpointLocations) {
                loc.getBlock().setType(Material.AIR);
            }
        }

        playerProgress.clear();
        finishTimes.clear();
        checkpointLocations = null;

        if (plugin.getConfig().getBoolean("broadcast-events", true)) {
            plugin.getServer().broadcastMessage(ChatColor.GOLD + "[Evento] " + ChatColor.YELLOW
                    + "La carrera de parkour ha terminado.");
        }
    }
                }
