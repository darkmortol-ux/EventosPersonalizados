package com.miplugin.eventos.integration;

import com.miplugin.eventos.EventosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class SistemaClasesHook {

    private final EventosPlugin plugin;

    public SistemaClasesHook(EventosPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("SistemaClases") != null;
    }

    /** Pide un ItemStack de la Estrella del Cambio a SistemaClases vía reflexión. Null si no está disponible. */
    public ItemStack crearEstrellaCambio() {
        Plugin sistemaClases = Bukkit.getPluginManager().getPlugin("SistemaClases");
        if (sistemaClases == null) return null;

        try {
            Method getEstrellaService = sistemaClases.getClass().getMethod("getEstrellaCambioService");
            Object estrellaService = getEstrellaService.invoke(sistemaClases);

            Method crear = estrellaService.getClass().getMethod("crear");
            return (ItemStack) crear.invoke(estrellaService);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo obtener la Estrella del Cambio de SistemaClases: " + e.getMessage());
            return null;
        }
    }

    /** Pide una Esencia de Clase aleatoria a SistemaClases vía reflexión. Null si no está disponible. */
    public ItemStack crearEsenciaAleatoria() {
        Plugin sistemaClases = Bukkit.getPluginManager().getPlugin("SistemaClases");
        if (sistemaClases == null) return null;

        try {
            Method getEsenciaService = sistemaClases.getClass().getMethod("getEsenciaService");
            Object esenciaService = getEsenciaService.invoke(sistemaClases);

            Method crearAleatoria = esenciaService.getClass().getMethod("crearAleatoria");
            return (ItemStack) crearAleatoria.invoke(esenciaService);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo obtener la Esencia de Clase de SistemaClases: " + e.getMessage());
            return null;
        }
    }
}
