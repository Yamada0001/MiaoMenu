package com.fluxcraft.MiaoMenu.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.MiaoMenu;

public final class VariableStore {
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern GLOBAL_PLACEHOLDER = Pattern.compile("%miao_global_([A-Za-z0-9_.-]+)%");
    private static final Pattern PLAYER_PLACEHOLDER = Pattern.compile("%miao_player_([A-Za-z0-9_.-]+)%");
    private static final Pattern GLOBAL_BRACED = Pattern.compile("\\{global:([A-Za-z0-9_.-]+)}");
    private static final Pattern PLAYER_BRACED = Pattern.compile("\\{player:([A-Za-z0-9_.-]+)}");

    private final MiaoMenu plugin;
    private final File storageFile;
    private final Map<String, String> globalVariables = new LinkedHashMap<>();
    private final Map<UUID, Map<String, String>> playerVariables = new LinkedHashMap<>();
    /** 上次落盘失败时置位，供 flush() 重试。 */
    private volatile boolean dirty;

    public VariableStore(MiaoMenu plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "storage.yml");
    }

    public synchronized void load() {
        globalVariables.clear();
        playerVariables.clear();
        if (!storageFile.exists()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(storageFile);
        readValues(configuration.getConfigurationSection("global"), globalVariables);
        ConfigurationSection players = configuration.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String uuidString : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                Map<String, String> values = new LinkedHashMap<>();
                readValues(players.getConfigurationSection(uuidString), values);
                if (!values.isEmpty()) {
                    playerVariables.put(uuid, values);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid player UUID in storage.yml: " + uuidString);
            }
        }
    }

    public synchronized Optional<String> getGlobal(String key) {
        return Optional.ofNullable(globalVariables.get(normalizeKey(key)));
    }

    public synchronized Optional<String> getPlayer(UUID playerId, String key) {
        Map<String, String> values = playerVariables.get(playerId);
        return Optional.ofNullable(values != null ? values.get(normalizeKey(key)) : null);
    }

    public synchronized void setGlobal(String key, String value) {
        globalVariables.put(requireKey(key), value != null ? value : "");
        save();
    }

    public synchronized void setPlayer(UUID playerId, String key, String value) {
        playerVariables.computeIfAbsent(playerId, _ -> new LinkedHashMap<>())
                .put(requireKey(key), value != null ? value : "");
        save();
    }

    public synchronized boolean removeGlobal(String key) {
        boolean removed = globalVariables.remove(normalizeKey(key)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized boolean removePlayer(UUID playerId, String key) {
        Map<String, String> values = playerVariables.get(playerId);
        if (values == null || values.remove(normalizeKey(key)) == null) {
            return false;
        }
        if (values.isEmpty()) {
            playerVariables.remove(playerId);
        }
        save();
        return true;
    }

    public synchronized String addGlobal(String key, double amount) {
        String normalized = requireKey(key);
        String result = add(globalVariables.get(normalized), amount);
        globalVariables.put(normalized, result);
        save();
        return result;
    }

    public synchronized String addPlayer(UUID playerId, String key, double amount) {
        String normalized = requireKey(key);
        Map<String, String> values = playerVariables.computeIfAbsent(playerId, _ -> new LinkedHashMap<>());
        String result = add(values.get(normalized), amount);
        values.put(normalized, result);
        save();
        return result;
    }

    public synchronized Map<String, String> getGlobalVariables() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(globalVariables));
    }

    public synchronized Map<String, String> getPlayerVariables(UUID playerId) {
        Map<String, String> values = playerVariables.get(playerId);
        return values == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public synchronized String resolve(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String resolved = replace(GLOBAL_PLACEHOLDER, text, key -> globalVariables.getOrDefault(key, ""));
        resolved = replace(GLOBAL_BRACED, resolved, key -> globalVariables.getOrDefault(key, ""));
        if (player == null) {
            return resolved;
        }
        Map<String, String> values = playerVariables.getOrDefault(player.getUniqueId(), Collections.emptyMap());
        resolved = replace(PLAYER_PLACEHOLDER, resolved, key -> values.getOrDefault(key, ""));
        return replace(PLAYER_BRACED, resolved, key -> values.getOrDefault(key, ""));
    }

    public synchronized void flush() {
        save();
    }

    private void readValues(ConfigurationSection section, Map<String, String> target) {
        if (section == null) {
            return;
        }
        section.getValues(false).forEach((key, value) -> {
            if (value != null && KEY_PATTERN.matcher(key).matches()) {
                target.put(normalizeKey(key), String.valueOf(value));
            } else if (value != null) {
                plugin.getLogger().warning("Ignoring invalid variable key in storage.yml: '" + key + "'");
            }
        });
    }

    /**
     * 把当前状态写回磁盘。
     *
     * @return 是否成功；失败时保留脏标记，供 {@link #flush()} 重试
     */
    private boolean save() {
        try {
            Files.createDirectories(storageFile.toPath().getParent());
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("global", new LinkedHashMap<>(globalVariables));
            Map<String, Map<String, String>> players = new LinkedHashMap<>();
            playerVariables.forEach((uuid, values) -> players.put(uuid.toString(), new LinkedHashMap<>(values)));
            configuration.set("players", players);

            Path target = storageFile.toPath();
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(
                    temporary,
                    configuration.saveToString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            return true;
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not save persistent menu variables to " + storageFile.getName()
                            + "; changes stay in memory and will be retried on the next flush.", e);
            return false;
        }
    }

    private static String add(String current, double amount) {
        double base = 0.0d;
        if (current != null && !current.isBlank()) {
            try {
                base = Double.parseDouble(current);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Stored variable is not numeric: " + current, e);
            }
        }
        double result = base + amount;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Variable calculation produced a non-finite number");
        }
        if (result == Math.rint(result)) {
            return Long.toString((long) result);
        }
        return java.math.BigDecimal.valueOf(result).stripTrailingZeros().toPlainString();
    }

    private static String replace(Pattern pattern, String text, java.util.function.Function<String, String> resolver) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String key = normalizeKey(matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(resolver.apply(key)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String requireKey(String key) {
        String normalized = normalizeKey(key);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Variable key must contain 1-64 letters, digits, dots, underscores, or hyphens");
        }
        return normalized;
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
