package com.fluxcraft.MiaoMenu.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Lang {
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final String DEFAULT_LANG = "en-us";
    private static final String LANG_DIR = "lang";
    /** 随包发布的语言文件，首次启动时全部释放到数据目录。 */
    private static final List<String> BUNDLED_LANGS = List.of("en-us", "zh-cn", "zh-tw", "vi-vn", "ja-jp");
    private static final Set<String> SUPPORTED_LANGS = Set.copyOf(BUNDLED_LANGS);

    private static Plugin plugin;
    private static volatile YamlConfiguration langConfig;
    private static volatile YamlConfiguration defaultConfig;
    private static final Set<String> reportedMissingKeys = ConcurrentHashMap.newKeySet();

    private Lang() {
    }

    public static void init(Plugin plugin) {
        Lang.plugin = plugin;
        loadLanguage();
    }

    private static void loadLanguage() {
        if (plugin == null) {
            return;
        }
        File langFolder = new File(plugin.getDataFolder(), LANG_DIR);
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create language directory: " + langFolder.getPath());
        }
        releaseBundledLanguages(langFolder);

        String configured = Objects.requireNonNullElse(
                plugin.getConfig().getString("settings.lang"), DEFAULT_LANG);
        String langCode = configured.trim().toLowerCase(Locale.ROOT);
        if (langCode.isEmpty()) {
            langCode = DEFAULT_LANG;
        }
        if (!SUPPORTED_LANGS.contains(langCode)) {
            plugin.getLogger().warning("Unknown language '" + configured + "'; falling back to " + DEFAULT_LANG + ".");
            langCode = DEFAULT_LANG;
        }

        File langFile = new File(langFolder, langCode + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file " + langCode + ".yml is missing; falling back to " + DEFAULT_LANG + ".");
            langFile = new File(langFolder, DEFAULT_LANG + ".yml");
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        defaultConfig = readBundled(DEFAULT_LANG);
        reportedMissingKeys.clear();
    }

    private static void releaseBundledLanguages(File langFolder) {
        for (String code : BUNDLED_LANGS) {
            File target = new File(langFolder, code + ".yml");
            if (target.exists()) {
                continue;
            }
            try (InputStream stream = plugin.getResource(LANG_DIR + "/" + code + ".yml")) {
                if (stream != null) {
                    Files.copy(stream, target.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to release language file: " + code, e);
            }
        }
    }

    private static YamlConfiguration readBundled(String code) {
        try (InputStream stream = plugin.getResource(LANG_DIR + "/" + code + ".yml")) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load bundled default language file", e);
            return null;
        }
    }

    public static void reload() {
        loadLanguage();
    }

    public static String get(String key) {
        if (langConfig == null) {
            return key;
        }
        String message = langConfig.getString(key);
        if (message == null && defaultConfig != null) {
            message = defaultConfig.getString(key);
        }
        if (message == null) {
            warnMissingKey(key);
            return key;
        }
        return SECTION.serialize(AMPERSAND.deserialize(message));
    }

    /**
     * 读取一个文案段落，并统一转换成 {@code §} 格式，避免调用方混用序列化器。
     */
    public static Map<String, String> getStringSection(String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        collectSection(langConfig, prefix, result);
        if (result.isEmpty()) {
            collectSection(defaultConfig, prefix, result);
        }
        return result;
    }

    private static void collectSection(YamlConfiguration config, String prefix, Map<String, String> target) {
        if (config == null) {
            return;
        }
        ConfigurationSection section = config.getConfigurationSection(prefix);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (section.isString(key)) {
                String raw = section.getString(key);
                if (raw != null) {
                    target.put(key, SECTION.serialize(AMPERSAND.deserialize(raw)));
                }
            }
        }
    }

    private static void warnMissingKey(String key) {
        if (plugin == null || !reportedMissingKeys.add(key)) {
            return;
        }
        plugin.getLogger().warning("Missing language key: " + key);
    }
}
