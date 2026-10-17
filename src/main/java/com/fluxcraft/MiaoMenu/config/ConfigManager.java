package com.fluxcraft.MiaoMenu.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.utils.Lang;

public class ConfigManager {
    private static final int CONFIG_VERSION = 25;
    private static final int MENU_VERSION = 6;
    private static final String JAVA_MENUS_DIR = "java_menus";
    private static final String BEDROCK_MENUS_DIR = "bedrock_menus";
    private static final String MENU_VERSION_KEY = "menu-version";
    private static final String FALLBACK_MATERIAL_KEY = "settings.item-resolver.fallback-material";
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);

    private final MiaoMenu plugin;

    public ConfigManager(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        } else {
            plugin.reloadConfig();
            FileConfiguration currentConfig = plugin.getConfig();
            int currentVersion = currentConfig.getInt("config-version", 0);
            if (currentVersion != CONFIG_VERSION && backupConfig(configFile)) {
                try {
                    plugin.saveResource("config.yml", true);
                    plugin.getLogger().info(Lang.get("message.config-updated"));
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to replace config.yml after creating a backup.", e);
                }
            }
        }
        plugin.reloadConfig();
        initDirectory(JAVA_MENUS_DIR);
        initDirectory(BEDROCK_MENUS_DIR);
    }

    private boolean backupConfig(File configFile) {
        Path source = configFile.toPath();
        String timestamp = BACKUP_TIMESTAMP.format(Instant.now());
        for (int attempt = 0; attempt < 1000; attempt++) {
            String suffix = attempt == 0 ? "" : "." + attempt;
            Path backup = source.resolveSibling("config.yml." + timestamp + suffix + ".bak");
            try {
                Files.copy(source, backup);
                return true;
            } catch (FileAlreadyExistsException ignored) {
                // A rapid repeated migration can reuse the same millisecond timestamp.
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not create a backup for config.yml; keeping the existing configuration unchanged.", e);
                return false;
            }
        }
        plugin.getLogger().severe("Could not allocate a unique backup name for config.yml; keeping the existing configuration unchanged.");
        return false;
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public Material getCraftEngineFallbackMaterial() {
        String materialName = getConfig().getString(FALLBACK_MATERIAL_KEY, "STONE");
        Material material = Material.matchMaterial(materialName);
        return material != null ? material : Material.STONE;
    }

    public void checkAndRefreshMenus() {
        FileConfiguration config = plugin.getConfig();
        int currentVersion = config.getInt(MENU_VERSION_KEY, 0);
        if (currentVersion != MENU_VERSION) {
            try {
                initDirectory(JAVA_MENUS_DIR);
                initDirectory(BEDROCK_MENUS_DIR);
                saveResourceIfNotExists("bedrock_menus/test.yml");
                saveResourceIfNotExists("java_menus/test.yml");
                config.set(MENU_VERSION_KEY, MENU_VERSION);
                config.save(new File(plugin.getDataFolder(), "config.yml"));
                plugin.reloadConfig();
                plugin.getLogger().info(Lang.get("message.reloaded"));
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, Lang.get("log.config.menu-refresh-failed"), e);
            }
            return;
        }
        if (config.getBoolean("settings.auto-generate-examples", true)) {
            saveResourceIfNotExists("bedrock_menus/test.yml");
            saveResourceIfNotExists("java_menus/test.yml");
        }
    }

    private void saveResourceIfNotExists(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
    }

    private void initDirectory(String dirName) {
        File dir = new File(plugin.getDataFolder(), dirName);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning(Lang.get("message.io-error"));
        }
    }
}
