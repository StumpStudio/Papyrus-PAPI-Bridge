package ru.armagidon.papyruspapibridge;

import javassist.*;
import javassist.bytecode.AccessFlag;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import ru.armagidon.papyrus.PapyrusAPI;

import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

public final class PapyrusPAPIBridge extends JavaPlugin implements Listener {


    @Override
    public void onEnable() {
        // Plugin startup logic
        getDataFolder().mkdirs();
        getServer().getPluginManager().registerEvents(this, this);
        try {
            init();
        } catch (IOException | NotFoundException | CannotCompileException | InvalidPluginException |
                 InvalidDescriptionException e) {
            getLogger().info("Failed to hijack PlaceholderAPI. Tough nut, huh");
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static Object makeReplacer() throws ClassNotFoundException {
        return Proxy.newProxyInstance(PapyrusPAPIBridge.class.getClassLoader(), new Class[]{Class.forName("me.clip.placeholderapi.replacer.Replacer")},
                (proxy, method, args) -> switch (method.getName()) {
            case "apply" -> {
                String text = (String) args[0];
                OfflinePlayer offlinePlayer = (OfflinePlayer) args[1];
                Bukkit.getLogger().info("USE");
                if (offlinePlayer != null && offlinePlayer.isOnline())
                    yield PapyrusAPI.getApi().getGlobalLegacyParser().parseLegacy((Player) offlinePlayer, text);
                else
                    yield PapyrusAPI.getApi().getGlobalLegacyParser().parseLegacy(null, text);
            }
            case "toString" -> "";
            case "equals" -> proxy == args[0];
            case "hashCode" -> 0;
            default -> null;
        });
    }

/*
    @EventHandler(ignoreCancelled = true)
    public void onPAPIEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("PlaceholderAPI")) {
            File papiFile = getPlaceholderAPIFile(getDataFolder());
            if (papiFile == null) {
                getLogger().info("Temporal folder is empty");
                return;
            }
            try {
                getLogger().info("Returning PlaceholderAPI plugin");
                Files.move(papiFile.toPath(), getDataFolder().getParentFile().toPath().resolve(papiFile.getName()), StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("PlaceholderAPI returned.");
            } catch (IOException e) {
                getLogger().severe("Failed to return PlaceholderAPI plugin");
                e.printStackTrace();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onServerLoad(ServerLoadEvent event) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        File papiFile = getPlaceholderAPIFile(getDataFolder());
        if (papiFile == null) {
            getLogger().info("Temporal folder is empty");
            return;
        }
        try {
            getLogger().info("Returning PlaceholderAPI plugin");
            Files.move(papiFile.toPath(), getDataFolder().getParentFile().toPath().resolve(papiFile.getName()), StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("PlaceholderAPI returned.");
        } catch (IOException e) {
            getLogger().severe("Failed to return PlaceholderAPI plugin");
            e.printStackTrace();
        }
    }
*/

    private File getPlaceholderAPIFile(File directory) {
        final Pattern fileFilter = Pattern.compile("\\.jar$");
        File[] matches = directory.listFiles((file) -> {
            if (!fileFilter.matcher(file.getName()).find()) return false;
            try {
                PluginDescriptionFile pdf = getPluginLoader().getPluginDescription(file);
                if (!pdf.getName().equals("PlaceholderAPI")) return false;
            } catch (InvalidDescriptionException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        });
        if (matches == null) {
            getLogger().info("IO Error");
            return null;
        } else if (matches.length == 0) {
            getLogger().info("Placeholder not found");
            return null;
        } else if (matches.length > 1) {
            getLogger().info("There must be only one provider for PlaceholderAPI");
            return null;
        }
        return matches[0];
    }

    private String toEntryPath(String classPath) {
        return classPath.replace('.', '/').concat(".class");
    }

    private Map<String, byte[]> unpackJarFile(File file) throws IOException {
        Map<String, byte[]> entryMap = new HashMap<>();
        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                InputStream stream = new BufferedInputStream(jar.getInputStream(entry));
                byte[] bytes = stream.readAllBytes();
                entryMap.put(entry.getName(), bytes);
                getLogger().info("Unpacked: " + entry.getName());
            }
        }
        return entryMap;
    }

    private void performMagic(Map<String, byte[]> entryMap) throws NotFoundException, CannotCompileException, IOException {
        String papiClass = "me.clip.placeholderapi.PlaceholderAPI";
        String replacerClass = "me.clip.placeholderapi.replacer.Replacer";
        String placeholderAPIPath = toEntryPath(papiClass);
        String replacerPath = toEntryPath(replacerClass);

        byte[] placeholderAPIBytes = entryMap.get(placeholderAPIPath);
        byte[] replacerBytes = entryMap.get(replacerPath);

        ClassPool classPool = ClassPool.getDefault();
        classPool.importPackage("ru.armagidon.papyruspapibridge");
        classPool.insertClassPath(new ByteArrayClassPath(papiClass, placeholderAPIBytes));
        classPool.insertClassPath(new ByteArrayClassPath(replacerClass, replacerBytes));
        classPool.insertClassPath(new ClassClassPath(PapyrusPAPIBridge.class));
        classPool.importPackage("me.clip.placeholderapi.replacer");
        CtClass placeholderAPIClass = classPool.get(papiClass);
        placeholderAPIClass.removeField(placeholderAPIClass.getDeclaredField("REPLACER_PERCENT"));
        CtField field = new CtField(classPool.get(replacerClass), "REPLACER_PERCENT", placeholderAPIClass);
        placeholderAPIClass.addField(field, CtField.Initializer.byCallWithParams(classPool.get(PapyrusPAPIBridge.class.getCanonicalName()), "makeReplacer"));
        placeholderAPIBytes = placeholderAPIClass.toBytecode();
        entryMap.put(placeholderAPIPath, placeholderAPIBytes);
    }

    private void pack(Map<String, byte[]> entries, File target) throws IOException {
        var copy = new HashMap<>(entries);
        Manifest manifest = new Manifest(new ByteArrayInputStream(copy.remove(JarFile.MANIFEST_NAME)));
        try (FileOutputStream fos = new FileOutputStream(target); JarOutputStream jos = new JarOutputStream(fos, manifest)) {
            for (Map.Entry<String, byte[]> e : copy.entrySet()) {
                String name = e.getKey();
                byte[] bytes = e.getValue();
                JarEntry entry = new JarEntry(name);
                jos.putNextEntry(entry);
                jos.write(bytes);
                jos.closeEntry();
                jos.flush();
                getLogger().info("Packed: " + name);
            }
        }
    }

    private void init() throws IOException, NotFoundException, CannotCompileException, InvalidPluginException, InvalidDescriptionException {
        // Find PlaceholderAPI jar file
        getLogger().info("Looking for PlaceholderAPI");
        File papiFile = getPlaceholderAPIFile(getDataFolder().getParentFile());
        if (papiFile == null) {
            return;
        }
        getLogger().info("Found file: " + papiFile.getPath());

        getLogger().info("Creating a backup for PlaceholderAPI");
        Files.copy(papiFile.toPath(), getDataFolder().toPath().resolve(papiFile.getName()),
                StandardCopyOption.REPLACE_EXISTING);

        getLogger().info("Unpacking...");
        Map<String, byte[]> entryMap = unpackJarFile(papiFile);
        getLogger().info("Unpacking done");

        getLogger().info("Performing bytecode magic...");
        performMagic(entryMap);
        getLogger().info("Magic performed");

        getLogger().info("Packing...");
        pack(entryMap, papiFile);
        getLogger().info("Packing done");
    }


}
