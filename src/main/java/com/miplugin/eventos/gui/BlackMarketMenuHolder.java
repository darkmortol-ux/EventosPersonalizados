package com.miplugin.eventos.gui;

import com.miplugin.eventos.events.BlackMarketEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class BlackMarketMenuHolder implements InventoryHolder {

    private final Map<Integer, Integer> slotToOfferIndex = new HashMap<>();
    private Inventory inventory;
    private int page;
    private BlackMarketEvent sourceEvent;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Map<Integer, Integer> getSlotToOfferIndex() {
        return slotToOfferIndex;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public BlackMarketEvent getSourceEvent() {
        return sourceEvent;
    }

    public void setSourceEvent(BlackMarketEvent sourceEvent) {
        this.sourceEvent = sourceEvent;
    }
}
