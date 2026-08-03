package com.miplugin.eventos.utils;

import com.miplugin.eventos.EventosPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Envuelve la API de Vault para dar/quitar dinero a jugadores.
 * Si Vault o un plugin de economía no están instalados, los métodos
 * simplemente no hacen nada (fallan de forma silenciosa) para que el
 * resto del plugin siga funcionando sin errores.
 */
public class EconomyManager {

    private final EventosPlugin plugin;
    private Economy economy;
    private boolean enabled = false;

    public EconomyManager(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    /** Intenta enlazar con Vault. Se llama en onEnable(), después de que todos los plugins cargaron. */
    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault no está instalado. Las recompensas en dinero quedarán desactivadas.");
            enabled = false;
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager()
                .getRegistration(Economy.class);

        if (rsp == null) {
            plugin.getLogger().warning("Vault está instalado pero no hay ningún plugin de economía registrado (ej: EssentialsX Economy). Las recompensas en dinero quedarán desactivadas.");
            enabled = false;
            return false;
        }

        economy = rsp.getProvider();
        enabled = true;
        plugin.getLogger().info("Conectado correctamente con Vault (" + economy.getName() + ").");
        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Deposita dinero al jugador. Si Vault no está disponible, no hace nada
     * y devuelve false (el resto del plugin no debe romperse por esto).
     */
    public boolean deposit(Player player, double amount) {
        if (!enabled || economy == null || amount <= 0) {
            return false;
        }
        try {
            economy.depositPlayer(player, amount);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Error al depositar dinero con Vault a " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /** Formatea una cantidad usando el formato del plugin de economía, si está disponible. */
    public String format(double amount) {
        if (enabled && economy != null) {
            try {
                return economy.format(amount);
            } catch (Exception ignored) {
                // sigue al formato por defecto
            }
        }
        return String.valueOf((long) amount);
    }
}
