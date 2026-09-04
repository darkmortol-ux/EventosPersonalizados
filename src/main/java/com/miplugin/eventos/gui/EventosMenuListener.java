package com.miplugin.eventos.gui;

import com.miplugin.eventos.EventosPlugin;
import com.miplugin.eventos.events.EventManager;
import com.miplugin.eventos.events.ServerEvent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class EventosMenuListener implements Listener {

    private final EventosPlugin plugin;

    public EventosMenuListener(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EventosMenuHolder holder)) return;

        // Nunca se pueden sacar ni mover los items del menú.
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        String eventId = holder.getSlotToEventId().get(slot);
        if (eventId == null) return; // click en un espacio relleno, no en un evento

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ServerEvent target = plugin.getEventManager().getEvents().get(eventId);
        if (target == null) return;

        if (target.isRunning()) {
            plugin.getEventManager().forceStop(eventId);
            player.sendMessage(ChatColor.YELLOW + "Evento pausado: " + target.getDisplayName());
        } else {
            EventManager.StartResult result = plugin.getEventManager().forceStart(eventId);
            switch (result) {
                case STARTED -> player.sendMessage(ChatColor.GREEN + "Evento iniciado: " + target.getDisplayName());
                case OTHER_EVENT_ACTIVE -> player.sendMessage(ChatColor.RED
                        + "Ya hay otro evento activo. Solo puede haber 1 evento a la vez.");
                case CANNOT_START -> player.sendMessage(ChatColor.RED
                        + "No se puede iniciar ese evento ahora mismo (revisa si requiere un mínimo de jugadores).");
                case ALREADY_RUNNING, NOT_FOUND -> {
                    // No debería pasar desde el menú, se ignora silenciosamente.
                }
            }
        }

        // Refresca el menú para reflejar el nuevo estado inmediatamente.
        plugin.getEventosMenu().open(player);
    }
}
