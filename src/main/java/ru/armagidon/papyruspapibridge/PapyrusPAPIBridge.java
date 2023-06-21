package ru.armagidon.papyruspapibridge;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.replacer.Replacer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.armagidon.papyrus.PapyrusAPI;
import ru.armagidon.papyruspapibridge.breitman.reflection.Reflection;

import java.lang.reflect.Field;
import java.util.function.Function;

public final class PapyrusPAPIBridge extends JavaPlugin implements Listener {


    @Override
    public void onEnable() {
        // Plugin startup logic
        getDataFolder().mkdirs();
        getServer().getPluginManager().registerEvents(this, this);


        Replacer PAPYRUS_REPLACER = (text, player, lookup) -> {
            if (player != null && player.isOnline())
                return PapyrusAPI.getApi().getGlobalLegacyParser().parseLegacy((Player) player, text);
            else
                return PapyrusAPI.getApi().getGlobalLegacyParser().parseLegacy(null, text);
        };


        try {
            Field REPLACER_FIELD = PlaceholderAPI.class.getDeclaredField("REPLACER_PERCENT");
            Reflection._set(REPLACER_FIELD, null, PAPYRUS_REPLACER);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

}
