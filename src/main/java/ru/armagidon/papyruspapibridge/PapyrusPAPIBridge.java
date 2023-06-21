package ru.armagidon.papyruspapibridge;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class PapyrusPAPIBridge extends JavaPlugin implements Listener {


    @Override
    public void onEnable() {
        // Plugin startup logic
        getDataFolder().mkdirs();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

}
