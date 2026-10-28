package com.miplugin.eventos.events;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Una oferta del Mercado Negro. El stock es COMPARTIDO entre todos los jugadores:
 * "remaining" baja para todo el servidor cada vez que alguien compra, sin importar
 * quién sea, hasta llegar a 0 (agotado para todos).
 */
public class BlackMarketOffer {

    private final Material material;
    private final int amount;
    private final double price;
    private final int totalStock;
    private final Map<Enchantment, Integer> enchantments;

    private int remaining;

    public BlackMarketOffer(Material material, int amount, double price, int totalStock, Map<Enchantment, Integer> enchantments) {
        this.material = material;
        this.amount = amount;
        this.price = price;
        this.totalStock = totalStock;
        this.enchantments = enchantments != null ? new LinkedHashMap<>(enchantments) : new LinkedHashMap<>();
        this.remaining = totalStock;
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public double getPrice() {
        return price;
    }

    public int getTotalStock() {
        return totalStock;
    }

    public int getRemaining() {
        return remaining;
    }

    public Map<Enchantment, Integer> getEnchantments() {
        return enchantments;
    }

    public boolean isAvailable() {
        return remaining > 0;
    }

    /** Descuenta 1 del stock compartido. Devuelve false si ya estaba agotado. */
    public boolean takeOne() {
        if (remaining <= 0) return false;
        remaining--;
        return true;
    }

    public void resetStock() {
        this.remaining = totalStock;
    }

    /** Devuelve 1 unidad al stock compartido (ej. si falló el cobro tras reservarla). Nunca pasa de totalStock. */
    public void resetStockPlusOne() {
        if (remaining < totalStock) {
            remaining++;
        }
    }
}
