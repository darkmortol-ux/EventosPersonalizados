package com.miplugin.eventos;

import com.miplugin.eventos.commands.EventosCommand;
import com.miplugin.eventos.events.EventManager;
import com.miplugin.eventos.gui.BlackMarketMenu;
import com.miplugin.eventos.gui.BlackMarketMenuListener;
import com.miplugin.eventos.gui.EventosMenu;
import com.miplugin.eventos.gui.EventosMenuListener;
import com.miplugin.eventos.location.LocationManager;
import com.miplugin.eventos.utils.EconomyManager;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class EventosPlugin extends JavaPlugin {

    private static EventosPlugin instance;
    private EventManager eventManager;
    private EconomyManager economyManager;
    private EventosMenu eventosMenu;
    private LocationManager locationManager;
    private BlackMarketMenu blackMarketMenu;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.economyManager = new EconomyManager(this);

        // Se conecta a Vault cuando TODOS los plugins ya cargaron,
        // así no importa si el plugin de economía (Essentials, etc.) carga después que este.
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onServerLoad(ServerLoadEvent event) {
                economyManager.setup();
            }
        }, this);

        this.locationManager = new LocationManager(this);
        this.locationManager.load();

        this.eventManager = new EventManager(this);
        this.eventManager.registerAllEvents();
        this.eventManager.scheduleAll();

        this.eventosMenu = new EventosMenu(this);
        getServer().getPluginManager().registerEvents(new EventosMenuListener(this), this);

        this.blackMarketMenu = new BlackMarketMenu(this);
        getServer().getPluginManager().registerEvents(new BlackMarketMenuListener(this), this);

        var cmd = getCommand("eventos");
        if (cmd != null) {
            EventosCommand executor = new EventosCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("EventosPersonalizados habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.stopAll();
        }
        getLogger().info("EventosPersonalizados deshabilitado.");
    }

    public static EventosPlugin getInstance() {
        return instance;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public EventosMenu getEventosMenu() {
        return eventosMenu;
    }

    public LocationManager getLocationManager() {
        return locationManager;
    }

    public BlackMarketMenu getBlackMarketMenu() {
        return blackMarketMenu;
    }
}
