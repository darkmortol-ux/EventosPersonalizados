package com.miplugin.eventos.gui;

import com.miplugin.eventos.EventosPlugin;
import com.miplugin.eventos.events.ServerEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Menú gráfico (inventario) para iniciar o pausar los eventos manualmente,
 * respetando siempre la regla de que solo puede haber 1 evento activo a la vez.
 */
public class EventosMenu {

    private static final String TITLE = ChatColor.DARK_GRAY + "Eventos del Servidor";
    private static final int SIZE = 27;
    private static final int[] EVENT_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private static final Map<String, Material> ICONS = new LinkedHashMap<>();
    static {
        ICONS.put("meteor-shower", Material.FIRE_CHARGE);
        ICONS.put("mob-invasion", Material.ZOMBIE_HEAD);
        ICONS.put("eternal-night", Material.CLOCK);
        ICONS.put("toxic-storm", Material.LINGERING_POTION);
        ICONS.put("supreme-chest", Material.CHEST);
        ICONS.put("random-boss", Material.WITHER_SKELETON_SKULL);
        ICONS.put("mercado-negro", Material.EMERALD);
    }

    private final EventosPlugin plugin;

    public EventosMenu(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        EventosMenuHolder holder = new EventosMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inv);

        ItemStack filler = fillerItem();
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler);
        }

        int slotIndex = 0;
        for (ServerEvent event : plugin.getEventManager().getEvents().values()) {
            if (slotIndex >= EVENT_SLOTS.length) break;
            int slot = EVENT_SLOTS[slotIndex++];
            inv.setItem(slot, buildIcon(event));
            holder.getSlotToEventId().put(slot, event.getId());
        }

        player.openInventory(inv);
    }

    private ItemStack buildIcon(ServerEvent event) {
        Material material = ICONS.getOrDefault(event.getId(), Material.PAPER);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(event.getDisplayName());

        boolean running = event.isRunning();
        boolean otherActive = !running && plugin.getEventManager().isAnyEventRunning();

        List<String> lore = new ArrayList<>();
        if (running) {
            lore.add(ChatColor.GREEN + "Estado: ACTIVO");
            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Click para PAUSAR");
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else if (otherActive) {
            lore.add(ChatColor.GRAY + "Estado: Inactivo");
            lore.add("");
            lore.add(ChatColor.RED + "Hay otro evento activo ahora mismo.");
            lore.add(ChatColor.RED + "Solo puede haber 1 evento a la vez.");
        } else {
            lore.add(ChatColor.GRAY + "Estado: Inactivo");
            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Click para INICIAR");
        }

        meta.setLore(lore);
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
}
