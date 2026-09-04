package com.miplugin.eventos.commands;

import com.miplugin.eventos.EventosPlugin;
import com.miplugin.eventos.events.EventManager;
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
            case "menu", "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Este comando solo puede usarse en el juego.");
                    return true;
                }
                plugin.getEventosMenu().open(player);
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Comandos de Eventos ===");
        sender.sendMessage(ChatColor.YELLOW + "/eventos list" + ChatColor.WHITE + " - Ver todos los eventos");
        sender.sendMessage(ChatColor.YELLOW + "/eventos start <evento>" + ChatColor.WHITE + " - Forzar inicio");
        sender.sendMessage(ChatColor.YELLOW + "/eventos stop <evento>" + ChatColor.WHITE + " - Forzar detener");
        sender.sendMessage(ChatColor.YELLOW + "/eventos reload" + ChatColor.WHITE + " - Recargar config.yml");
        sender.sendMessage(ChatColor.YELLOW + "/eventos menu" + ChatColor.WHITE + " - Abrir el menú gráfico de eventos");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("list", "start", "stop", "reload", "menu"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop"))) {
            options.addAll(plugin.getEventManager().getEvents().keySet());
        }
        return options;
    }
}
