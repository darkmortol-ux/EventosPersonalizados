package com.miplugin.eventos.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Identifica de forma segura el inventario del menú de eventos (para no confundirlo
 * con cofres u otros inventarios al escuchar InventoryClickEvent), y guarda qué
 * evento corresponde a cada slot clickeable.
 */
public class EventosMenuHolder implements InventoryHolder {

    private final Map<Integer, String> slotToEventId = new HashMap<>();
    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Map<Integer, String> getSlotToEventId() {
        return slotToEventId;
    }
}
