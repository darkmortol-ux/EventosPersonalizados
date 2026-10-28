package com.miplugin.eventos.gui;

import com.miplugin.eventos.EventosPlugin;
import com.miplugin.eventos.events.BlackMarketEvent;
import com.miplugin.eventos.events.BlackMarketOffer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class BlackMarketMenuListener implements Listener {

    private final EventosPlugin plugin;

    public BlackMarketMenuListener(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlackMarketMenuHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        BlackMarketEvent marketEvent = holder.getSourceEvent();
        if (marketEvent == null || !marketEvent.isRunning()) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        // Navegación
        if (slot == 45 && holder.getPage() > 0) {
            plugin.getBlackMarketMenu().open(player, marketEvent, holder.getPage() - 1);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot == 53) {
            plugin.getBlackMarketMenu().open(player, marketEvent, holder.getPage() + 1);
            return;
        }

        // Compra
        Integer offerIndex = holder.getSlotToOfferIndex().get(slot);
        if (offerIndex == null) return;

        List<BlackMarketOffer> offers = marketEvent.getOffers();
        if (offerIndex < 0 || offerIndex >= offers.size()) return;
        BlackMarketOffer offer = offers.get(offerIndex);

        handlePurchase(player, offer);

        // Refresca el menú (actualiza el stock mostrado para este jugador y para quien más lo tenga abierto)
        plugin.getBlackMarketMenu().open(player, marketEvent, holder.getPage());
    }

    private void handlePurchase(Player player, BlackMarketOffer offer) {
        if (!offer.isAvailable()) {
            player.sendMessage(ChatColor.RED + "Ese producto ya se agotó.");
            return;
        }

        if (!plugin.getEconomyManager().isEnabled()) {
            player.sendMessage(ChatColor.RED + "La economía (Vault) no está disponible en este servidor.");
            return;
        }
        if (!plugin.getEconomyManager().has(player, offer.getPrice())) {
            player.sendMessage(ChatColor.RED + "No tienes suficiente dinero. Necesitas "
                    + plugin.getEconomyManager().format(offer.getPrice()) + ".");
            return;
        }

        // Reserva el stock ANTES de cobrar, para que dos compras simultáneas nunca dejen stock negativo.
        if (!offer.takeOne()) {
            player.sendMessage(ChatColor.RED + "Ese producto se agotó justo antes de tu compra.");
            return;
        }

        if (!plugin.getEconomyManager().withdraw(player, offer.getPrice())) {
            // Si por algún motivo falla el cobro, se devuelve el stock reservado.
            offer.resetStockPlusOne();
            player.sendMessage(ChatColor.RED + "Ocurrió un error al cobrar. Intenta de nuevo.");
            return;
        }

        ItemStack item = buildPurchasedItem(offer);
        var leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage(ChatColor.YELLOW + "Tu inventario estaba lleno, el ítem se dejó caer en el suelo.");
        }

        player.sendMessage(ChatColor.GREEN + "Compraste " + offer.getAmount() + "x "
                + offer.getMaterial().name().toLowerCase().replace('_', ' ')
                + " por " + plugin.getEconomyManager().format(offer.getPrice()) + ".");
    }

    private ItemStack buildPurchasedItem(BlackMarketOffer offer) {
        ItemStack item = new ItemStack(offer.getMaterial(), offer.getAmount());
        if (!offer.getEnchantments().isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            offer.getEnchantments().forEach((ench, level) -> meta.addEnchant(ench, level, true));
            item.setItemMeta(meta);
        }
        return item;
    }
}
