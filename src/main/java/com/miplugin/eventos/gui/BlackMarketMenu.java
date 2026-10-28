package com.miplugin.eventos.gui;

import com.miplugin.eventos.EventosPlugin;
import com.miplugin.eventos.events.BlackMarketEvent;
import com.miplugin.eventos.events.BlackMarketOffer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BlackMarketMenu {

    private static final int PAGE_SIZE = 45; // slots 0-44; la fila 45-53 es la barra de navegación
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 53;

    private final EventosPlugin plugin;

    public BlackMarketMenu(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, BlackMarketEvent event) {
        open(player, event, 0);
    }

    public void open(Player player, BlackMarketEvent event, int page) {
        List<BlackMarketOffer> offers = event.getOffers();
        int maxPage = offers.isEmpty() ? 0 : (offers.size() - 1) / PAGE_SIZE;
        page = Math.max(0, Math.min(page, maxPage));

        BlackMarketMenuHolder holder = new BlackMarketMenuHolder();
        holder.setPage(page);
        holder.setSourceEvent(event);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_PURPLE + "Mercado Negro " + ChatColor.GRAY + "(" + (page + 1) + "/" + (maxPage + 1) + ")");
        holder.setInventory(inv);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, offers.size());

        for (int i = start; i < end; i++) {
            int slot = i - start;
            inv.setItem(slot, buildOfferItem(offers.get(i)));
            holder.getSlotToOfferIndex().put(slot, i);
        }
        for (int slot = end - start; slot < PAGE_SIZE; slot++) {
            inv.setItem(slot, fillerItem());
        }
        for (int slot = PAGE_SIZE; slot < 54; slot++) {
            inv.setItem(slot, fillerItem());
        }

        if (page > 0) {
            inv.setItem(SLOT_PREV, navItem(Material.ARROW, ChatColor.YELLOW + "« Página anterior"));
        }
        inv.setItem(SLOT_CLOSE, navItem(Material.BARRIER, ChatColor.RED + "Cerrar"));
        if (page < maxPage) {
            inv.setItem(SLOT_NEXT, navItem(Material.ARROW, ChatColor.YELLOW + "Página siguiente »"));
        }

        player.openInventory(inv);
    }

    /** Cierra el menú a cualquier jugador que lo tenga abierto (se usa cuando el evento termina). */
    public void closeAllViewers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BlackMarketMenuHolder) {
                player.closeInventory();
            }
        }
    }

    private ItemStack buildOfferItem(BlackMarketOffer offer) {
        boolean available = offer.isAvailable();
        Material material = available ? offer.getMaterial() : Material.BARRIER;
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(offer.getAmount(), 64)));
        ItemMeta meta = item.getItemMeta();

        String name = formatMaterialName(offer.getMaterial());
        meta.setDisplayName((available ? ChatColor.AQUA : ChatColor.RED) + name
                + (offer.getAmount() > 1 ? ChatColor.GRAY + " x" + offer.getAmount() : ""));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GOLD + "Precio: " + ChatColor.WHITE + "$" + formatPrice(offer.getPrice()));
        lore.add(ChatColor.YELLOW + "Stock: " + ChatColor.WHITE + offer.getRemaining() + "/" + offer.getTotalStock()
                + ChatColor.GRAY + " (compartido entre todos)");

        if (!offer.getEnchantments().isEmpty()) {
            lore.add("");
            for (var entry : offer.getEnchantments().entrySet()) {
                lore.add(ChatColor.LIGHT_PURPLE + formatEnchantName(entry.getKey()) + " " + toRoman(entry.getValue()));
            }
        }

        lore.add("");
        lore.add(available ? ChatColor.GREEN + "▶ Click para comprar" : ChatColor.RED + "AGOTADO");
        meta.setLore(lore);

        if (available && !offer.getEnchantments().isEmpty()) {
            for (var entry : offer.getEnchantments().entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack fillerItem() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);
        return filler;
    }

    private String formatMaterialName(Material material) {
        String[] parts = material.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private String formatEnchantName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private String formatPrice(double price) {
        if (price == Math.floor(price)) {
            return String.format("%,d", (long) price);
        }
        return String.format("%,.2f", price);
    }

    private String toRoman(int number) {
        String[] romans = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV"};
        if (number >= 0 && number < romans.length) return romans[number];
        return String.valueOf(number);
    }
}
