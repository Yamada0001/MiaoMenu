package com.fluxcraft.MiaoMenu.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.utils.Lang;

public class ConfigManager {
    private static final int CONFIG_VERSION = 28;
    private static final int MENU_VERSION = 8;
    private static final String JAVA_MENUS_DIR = "java_menus";
    private static final String BEDROCK_MENUS_DIR = "bedrock_menus";
    private static final String DIALOG_MENUS_DIR = "dialog_menus";
    private static final String MENU_VERSION_KEY = "menu-version";
    private static final String CONFIG_VERSION_KEY = "config-version";
    private static final String FALLBACK_MATERIAL_KEY = "settings.item-resolver.fallback-material";
    private static final String RATE_LIMIT_WINDOW_KEY = "settings.rate-limit.window-millis";
    private static final String RATE_LIMIT_MAX_KEY = "settings.rate-limit.max-events";
    private static final int DEFAULT_RATE_LIMIT_WINDOW_MILLIS = 2000;
    private static final int DEFAULT_RATE_LIMIT_MAX_EVENTS = 10;
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
            int currentVersion = currentConfig.getInt(CONFIG_VERSION_KEY, 0);
            if (currentVersion != CONFIG_VERSION) {
                migrateConfig(configFile, currentVersion);
            }
        }
        plugin.reloadConfig();
        initDirectory(JAVA_MENUS_DIR);
        initDirectory(BEDROCK_MENUS_DIR);
        initDirectory(DIALOG_MENUS_DIR);
    }

    /**
     * 版本迁移：先备份，再按"缺失键补齐"的方式合并，绝不丢弃用户已有的设置。
     */
    private void migrateConfig(File configFile, int fromVersion) {
        if (!backupConfig(configFile)) {
            plugin.getLogger().severe(Lang.get("log.config.migration-skipped"));
            return;
        }
        FileConfiguration current = plugin.getConfig();
        YamlConfiguration defaults = readBundledDefaults();
        if (defaults == null) {
            plugin.getLogger().severe(Lang.get("log.config.migration-defaults-missing"));
            return;
        }
        int added = 0;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || current.contains(key)) {
                continue;
            }
            current.set(key, defaults.get(key));
            added++;
        }
        current.set(CONFIG_VERSION_KEY, CONFIG_VERSION);
        try {
            current.save(configFile);
            plugin.reloadConfig();
            plugin.getLogger().info(Lang.get("log.config.migrated")
                    .replace("{0}", String.valueOf(fromVersion))
                    .replace("{1}", String.valueOf(CONFIG_VERSION))
                    .replace("{2}", String.valueOf(added)));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, Lang.get("log.config.migration-failed"), e);
        }
    }

    private YamlConfiguration readBundledDefaults() {
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, Lang.get("log.config.migration-defaults-missing"), e);
            return null;
        }
    }

    private boolean backupConfig(File configFile) {
        Path source = configFile.toPath();
        String timestamp = BACKUP_TIMESTAMP.format(Instant.now());
        for (int attempt = 0; attempt < 1000; attempt++) {
            String suffix = attempt == 0 ? "" : "." + attempt;
            Path backup = source.resolveSibling("config.yml." + timestamp + suffix + ".bak");
            try {
                Files.copy(source, backup);
                plugin.getLogger().info(Lang.get("log.config.backup-created").replace("{0}", backup.getFileName().toString()));
                return true;
            } catch (FileAlreadyExistsException ignored) {
                // 同一毫秒内重复迁移会撞名，换一个后缀重试。
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, Lang.get("log.config.backup-create-failed"), e);
                return false;
            }
        }
        plugin.getLogger().severe(Lang.get("log.config.backup-create-failed"));
        return false;
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public Material getCraftEngineFallbackMaterial() {
        Material material = Material.matchMaterial(Objects.requireNonNullElse(
                getConfig().getString(FALLBACK_MATERIAL_KEY), "STONE"));
        return material != null ? material : Material.STONE;
    }

    public int getRateLimitWindowMillis() {
        int configured = getConfig().getInt(RATE_LIMIT_WINDOW_KEY, DEFAULT_RATE_LIMIT_WINDOW_MILLIS);
        return Math.max(50, configured);
    }

    public int getRateLimitMaxEvents() {
        int configured = getConfig().getInt(RATE_LIMIT_MAX_KEY, DEFAULT_RATE_LIMIT_MAX_EVENTS);
        return Math.max(1, configured);
    }

    public void checkAndRefreshMenus() {
        FileConfiguration config = plugin.getConfig();
        int currentVersion = config.getInt(MENU_VERSION_KEY, 0);
        if (currentVersion != MENU_VERSION) {
            try {
                initDirectory(JAVA_MENUS_DIR);
                initDirectory(BEDROCK_MENUS_DIR);
                initDirectory(DIALOG_MENUS_DIR);
                saveExampleMenus();
                config.set(MENU_VERSION_KEY, MENU_VERSION);
                config.save(new File(plugin.getDataFolder(), "config.yml"));
                plugin.reloadConfig();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, Lang.get("log.config.menu-refresh-failed"), e);
            }
            return;
        }
        if (config.getBoolean("settings.auto-generate-examples", true)) {
            saveExampleMenus();
        }
    }

    private void saveExampleMenus() {
        saveResourceIfNotExists("java_menus/test.yml");
        saveResourceIfNotExists("bedrock_menus/test.yml");
        saveResourceIfNotExists("dialog_menus/test.yml");
        saveResourceIfNotExists("java_menus/theme-spring.yml");
        saveResourceIfNotExists("dialog_menus/theme-spring.yml");
        saveResourceIfNotExists("commands.yml");
        saveResourceIfNotExists("tasks.yml");
    }

    private void saveResourceIfNotExists(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (file.exists()) {
            return;
        }
        try {
            plugin.saveResource(path, false);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, Lang.get("log.config.example-missing").replace("{0}", path), e);
        }
    }

    private void initDirectory(String dirName) {
        File dir = new File(plugin.getDataFolder(), dirName);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning(Lang.get("message.io-error"));
        }
    }
}
