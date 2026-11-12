package com.miplugin.eventos.commands;

import com.miplugin.eventos.EventosPlugin;
import com.miplugin.eventos.events.EventManager;
import com.miplugin.eventos.events.RandomBossEvent;
import com.miplugin.eventos.events.ServerEvent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class EventosCommand implements CommandExecutor, TabCompleter {

    private final EventosPlugin plugin;

    public EventosCommand(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eventos.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> {
                sender.sendMessage(ChatColor.GOLD + "=== Eventos disponibles ===");
                for (ServerEvent event : plugin.getEventManager().getEvents().values()) {
                    String status = event.isRunning()
                            ? ChatColor.GREEN + "ACTIVO"
                            : ChatColor.GRAY + "inactivo";
                    sender.sendMessage(ChatColor.YELLOW + "- " + event.getId() + " (" + status + ChatColor.YELLOW + ")");
                }
            }
            case "start" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Uso: /eventos start <evento>");
                    return true;
                }
                EventManager.StartResult result = plugin.getEventManager().forceStart(args[1].toLowerCase());
                sender.sendMessage(mensajeParaResultado(result, args[1]));
            }
            case "stop" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Uso: /eventos stop <evento>");
                    return true;
                }
                boolean stopped = plugin.getEventManager().forceStop(args[1].toLowerCase());
                sender.sendMessage(stopped
                        ? ChatColor.GREEN + "Evento detenido: " + args[1]
                        : ChatColor.RED + "No se pudo detener el evento (no existe o no está activo).");
            }
            case "reload" -> {
                plugin.getEventManager().reload();
                sender.sendMessage(ChatColor.GREEN + "Configuración de eventos recargada.");
            }
            case "esencia" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Uso: /eventos esencia <jugador>");
                    return true;
                }
                Player objetivo = plugin.getServer().getPlayerExact(args[1]);
                if (objetivo == null) {
                    sender.sendMessage(ChatColor.RED + "Ese jugador no está conectado.");
                    return true;
                }
                ServerEvent evento = plugin.getEventManager().getEvents().get("random-boss");
                if (!(evento instanceof RandomBossEvent jefe)) {
                    sender.sendMessage(ChatColor.RED + "El evento del jefe no está disponible.");
                    return true;
                }
                sender.sendMessage(mensajeParaEsencia(jefe.giveEssence(objetivo, true), objetivo.getName()));
            }
            case "menu", "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Este comando solo puede usarse en el juego.");
                    return true;
                }
                plugin.getEventosMenu().open(player);
            }
            case "setloc" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Este comando solo puede usarse en el juego.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Uso: /eventos setloc <evento>");
                    return true;
                }
                String eventId = args[1].toLowerCase();
                if (!plugin.getEventManager().getEvents().containsKey(eventId)) {
                    sender.sendMessage(ChatColor.RED + "Ese evento no existe.");
                    return true;
                }
                plugin.getLocationManager().setLocation(eventId, player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Ubicación de '" + eventId + "' asignada a tu posición actual "
                        + ChatColor.GRAY + "(" + player.getLocation().getBlockX() + ", "
                        + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ() + ").");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private String mensajeParaResultado(EventManager.StartResult result, String eventoNombre) {
        return switch (result) {
            case STARTED -> ChatColor.GREEN + "Evento iniciado: " + eventoNombre;
            case NOT_FOUND -> ChatColor.RED + "Ese evento no existe.";
            case ALREADY_RUNNING -> ChatColor.YELLOW + "Ese evento ya está activo.";
            case OTHER_EVENT_ACTIVE -> ChatColor.RED + "Ya hay otro evento activo. "
                    + ChatColor.GRAY + "Solo puede haber 1 evento a la vez; detén el actual con /eventos stop <evento>.";
            case CANNOT_START -> ChatColor.RED + "No se puede iniciar ese evento ahora mismo "
                    + ChatColor.GRAY + "(ej. faltan jugadores conectados para el mínimo requerido).";
        };
    }

    private String mensajeParaEsencia(RandomBossEvent.EssenceResult result, String jugador) {
        return switch (result) {
            case GIVEN -> ChatColor.GREEN + "Prueba correcta: se ejecutó la entrega de una esencia a " + jugador + ".";
            case DISABLED -> ChatColor.RED + "La integración está desactivada.";
            case NO_DROP -> ChatColor.YELLOW + "El sorteo del porcentaje de drop no salió.";
            case PLUGIN_MISSING -> ChatColor.RED + "SistemaClases no está instalado o su 'plugin-name' en config.yml no coincide. Revisa la consola.";
            case NO_ESSENCES -> ChatColor.RED + "La lista 'essences' está vacía o todos los porcentajes son 0.";
            case NO_COMMAND -> ChatColor.RED + "Falta el 'command' en random-boss.sistema-clases del config.yml.";
            case COMMAND_FAILED -> ChatColor.RED + "El comando de entrega falló. Revisa la consola: ahí aparece el comando exacto que se intentó.";
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Comandos de Eventos ===");
        sender.sendMessage(ChatColor.YELLOW + "/eventos list" + ChatColor.WHITE + " - Ver todos los eventos");
        sender.sendMessage(ChatColor.YELLOW + "/eventos start <evento>" + ChatColor.WHITE + " - Forzar inicio");
        sender.sendMessage(ChatColor.YELLOW + "/eventos stop <evento>" + ChatColor.WHITE + " - Forzar detener");
        sender.sendMessage(ChatColor.YELLOW + "/eventos reload" + ChatColor.WHITE + " - Recargar config.yml");
        sender.sendMessage(ChatColor.YELLOW + "/eventos menu" + ChatColor.WHITE + " - Abrir el menú gráfico de eventos");
        sender.sendMessage(ChatColor.YELLOW + "/eventos esencia <jugador>" + ChatColor.WHITE + " - Probar la entrega de esencia del jefe");
        sender.sendMessage(ChatColor.YELLOW + "/eventos setloc <evento>" + ChatColor.WHITE + " - Asignar tu ubicación actual a un evento (ej. mercado-negro)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("list", "start", "stop", "reload", "menu", "setloc", "esencia"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop")
                || args[0].equalsIgnoreCase("setloc"))) {
            options.addAll(plugin.getEventManager().getEvents().keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("esencia")) {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                options.add(online.getName());
            }
        }
        return options;
    }
}
