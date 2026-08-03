package com.miplugin.eventos.commands;

import com.miplugin.eventos.EventosPlugin;
import com.miplugin.eventos.events.ServerEvent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

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
                boolean started = plugin.getEventManager().forceStart(args[1].toLowerCase());
                sender.sendMessage(started
                        ? ChatColor.GREEN + "Evento iniciado: " + args[1]
                        : ChatColor.RED + "No se pudo iniciar el evento (no existe o ya está activo).");
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
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Comandos de Eventos ===");
        sender.sendMessage(ChatColor.YELLOW + "/eventos list" + ChatColor.WHITE + " - Ver todos los eventos");
        sender.sendMessage(ChatColor.YELLOW + "/eventos start <evento>" + ChatColor.WHITE + " - Forzar inicio");
        sender.sendMessage(ChatColor.YELLOW + "/eventos stop <evento>" + ChatColor.WHITE + " - Forzar detener");
        sender.sendMessage(ChatColor.YELLOW + "/eventos reload" + ChatColor.WHITE + " - Recargar config.yml");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("list", "start", "stop", "reload"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop"))) {
            options.addAll(plugin.getEventManager().getEvents().keySet());
        }
        return options;
    }
}
