package com.fluxcraft.MiaoMenu.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.MiaoMenu;

/**
 * 持久化菜单变量。
 *
 * <p>内存是权威数据源，磁盘只是快照：变更只更新内存并标脏，落盘由后台线程
 * 合并后执行，避免"每次点击都全量重写 storage.yml"堵住主线程。写盘沿用
 * 临时文件 + 原子替换，失败时保留脏状态并按退避重试；{@link #shutdown()}
 * 会在插件禁用时做一次同步的最终落盘。</p>
 *
 * <p>玩家数据按需从磁盘惰性回填：内存缓存只保留"最近用过"的玩家，被淘汰的
 * 条目本身已经落盘，下次访问时会重新读取，因此淘汰不会丢数据。脏条目永不
 * 淘汰。</p>
 */
public final class VariableStore {
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern GLOBAL_PLACEHOLDER = Pattern.compile("%miao_global_([A-Za-z0-9_.-]+)%");
    private static final Pattern PLAYER_PLACEHOLDER = Pattern.compile("%miao_player_([A-Za-z0-9_.-]+)%");
    private static final Pattern GLOBAL_BRACED = Pattern.compile("\\{global:([A-Za-z0-9_.-]+)}");
    private static final Pattern PLAYER_BRACED = Pattern.compile("\\{player:([A-Za-z0-9_.-]+)}");

    private static final long DEFAULT_SAVE_DELAY_MILLIS = 1000L;
    private static final long MIN_RETRY_DELAY_MILLIS = 1_000L;
    private static final long MAX_RETRY_DELAY_MILLIS = 30_000L;
    private static final int DEFAULT_CACHE_MAX_PLAYERS = 2000;
    private static final long DEFAULT_CACHE_TTL_MINUTES = 60L;

    /**
     * 延时调度抽象：生产实现使用后台单线程，测试可手工驱动。
     */
    interface SaveScheduler {
        void schedule(Runnable task, long delayMillis);

        default void shutdown() {
        }
    }

    private final MiaoMenu plugin;
    private final File storageFile;
    private final SaveScheduler scheduler;
    private final long saveDelayMillis;
    private final int cacheMaxPlayers;
    private final long cacheTtlMillis;

    private final Map<String, String> globalVariables = new LinkedHashMap<>();
    private final Map<UUID, PlayerData> playerVariables = new LinkedHashMap<>();

    /** 串行化落盘；只包住"取快照 + 写文件"，不会与内存锁形成环。 */
    private final Object ioLock = new Object();
    private final AtomicBoolean saveScheduled = new AtomicBoolean();

    /** 内存变更版本号；{@code version > savedVersion} 即存在未落盘的改动。 */
    private long version;
    private long savedVersion;
    private volatile boolean dirty;
    private volatile boolean active = true;
    /** 连续失败次数，用于退避；由 ioLock 保护。 */
    private long failureCount;
    private long retryDelayMillis = MIN_RETRY_DELAY_MILLIS;

    public VariableStore(MiaoMenu plugin) {
        this(
                plugin,
                new ExecutorSaveScheduler(),
                readLong(plugin, "settings.variables.save-delay-millis",
                        DEFAULT_SAVE_DELAY_MILLIS, 0L, 60_000L),
                (int) readLong(plugin, "settings.variables.cache.max-players",
                        DEFAULT_CACHE_MAX_PLAYERS, 0L, 1_000_000L),
                readLong(plugin, "settings.variables.cache.ttl-minutes",
                        DEFAULT_CACHE_TTL_MINUTES, 0L, 525_600L) * 60_000L
        );
    }

    VariableStore(
            MiaoMenu plugin,
            SaveScheduler scheduler,
            long saveDelayMillis,
            int cacheMaxPlayers,
            long cacheTtlMillis
    ) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "storage.yml");
        this.scheduler = scheduler;
        this.saveDelayMillis = Math.max(0L, saveDelayMillis);
        this.cacheMaxPlayers = Math.max(0, cacheMaxPlayers);
        this.cacheTtlMillis = Math.max(0L, cacheTtlMillis);
    }

    public synchronized void load() {
        globalVariables.clear();
        playerVariables.clear();
        version = 0L;
        savedVersion = 0L;
        dirty = false;
        if (!storageFile.exists()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(storageFile);
        readValues(configuration.getConfigurationSection("global"), globalVariables);
        ConfigurationSection players = configuration.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String uuidString : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                Map<String, String> values = new LinkedHashMap<>();
                readValues(players.getConfigurationSection(uuidString), values);
                if (!values.isEmpty()) {
                    PlayerData data = new PlayerData();
                    data.values.putAll(values);
                    data.lastAccessMillis = now;
                    data.lastModifiedVersion = 0L;
                    playerVariables.put(uuid, data);
                }
            } catch (IllegalArgumentException e) {
                logger().warning("Ignoring invalid player UUID in storage.yml: " + uuidString);
            }
        }
    }

    public synchronized Optional<String> getGlobal(String key) {
        return Optional.ofNullable(globalVariables.get(normalizeKey(key)));
    }

    public Optional<String> getPlayer(UUID playerId, String key) {
        if (playerId == null) {
            return Optional.empty();
        }
        ensureLoaded(playerId);
        synchronized (this) {
            PlayerData data = playerVariables.get(playerId);
            return Optional.ofNullable(data != null ? data.values.get(normalizeKey(key)) : null);
        }
    }

    public synchronized void setGlobal(String key, String value) {
        globalVariables.put(requireKey(key), value != null ? value : "");
        markChangedLocked();
    }

    public synchronized void setPlayer(UUID playerId, String key, String value) {
        PlayerData data = playerDataLocked(playerId);
        data.values.put(requireKey(key), value != null ? value : "");
        markChangedLocked();
    }

    public synchronized boolean removeGlobal(String key) {
        boolean removed = globalVariables.remove(normalizeKey(key)) != null;
        if (removed) {
            markChangedLocked();
        }
        return removed;
    }

    public synchronized boolean removePlayer(UUID playerId, String key) {
        if (playerId == null) {
            return false;
        }
        PlayerData data = playerVariables.get(playerId);
        if (data == null || data.values.remove(normalizeKey(key)) == null) {
            return false;
        }
        if (data.values.isEmpty()) {
            playerVariables.remove(playerId);
        }
        markChangedLocked();
        return true;
    }

    public synchronized String addGlobal(String key, double amount) {
        String normalized = requireKey(key);
        String result = add(globalVariables.get(normalized), amount);
        globalVariables.put(normalized, result);
        markChangedLocked();
        return result;
    }

    public synchronized String addPlayer(UUID playerId, String key, double amount) {
        String normalized = requireKey(key);
        PlayerData data = playerDataLocked(playerId);
        String result = add(data.values.get(normalized), amount);
        data.values.put(normalized, result);
        markChangedLocked();
        return result;
    }

    public synchronized Map<String, String> getGlobalVariables() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(globalVariables));
    }

    public Map<String, String> getPlayerVariables(UUID playerId) {
        if (playerId == null) {
            return Collections.emptyMap();
        }
        ensureLoaded(playerId);
        synchronized (this) {
            PlayerData data = playerVariables.get(playerId);
            return data == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(data.values));
        }
    }

    public String resolve(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (player != null) {
            // 惰性回填可能读盘，必须在拿内存锁之前完成。
            ensureLoaded(player.getUniqueId());
        }
        synchronized (this) {
            String resolved = replace(GLOBAL_PLACEHOLDER, text, key -> globalVariables.getOrDefault(key, ""));
            resolved = replace(GLOBAL_BRACED, resolved, key -> globalVariables.getOrDefault(key, ""));
            if (player == null) {
                return resolved;
            }
            PlayerData data = playerVariables.get(player.getUniqueId());
            Map<String, String> values = data != null ? data.values : Collections.emptyMap();
            resolved = replace(PLAYER_PLACEHOLDER, resolved, key -> values.getOrDefault(key, ""));
            return replace(PLAYER_BRACED, resolved, key -> values.getOrDefault(key, ""));
        }
    }

    /**
     * 立即同步落盘（不做合并、不延迟）。
     *
     * @return 是否成功；失败时内存改动仍然保留，并会在后续调度中重试
     */
    public boolean flush() {
        return save(false);
    }

    /**
     * 插件禁用时调用：停止后台调度并做最后一次同步落盘。
     */
    public void shutdown() {
        active = false;
        saveScheduled.set(false);
        scheduler.shutdown();
        save(false);
    }

    /** 是否存在尚未落盘的改动（诊断与测试用）。 */
    public boolean hasPendingChanges() {
        return dirty;
    }

    // ------------------------------------------------------------------
    // 落盘
    // ------------------------------------------------------------------

    private boolean save(boolean fromScheduler) {
        synchronized (ioLock) {
            if (fromScheduler && !active) {
                return true;
            }
            Snapshot snapshot;
            long snapshotVersion;
            synchronized (this) {
                if (version == savedVersion) {
                    dirty = false;
                    // 没有待写改动时也允许清理：此时 savedVersion 已是最新。
                    cleanupCacheLocked();
                    return true;
                }
                snapshot = snapshotLocked();
                snapshotVersion = version;
            }
            boolean written = write(snapshot);
            synchronized (this) {
                if (written) {
                    savedVersion = Math.max(savedVersion, snapshotVersion);
                }
                // 写盘期间可能又有变更，用版本号判断而不是简单清标志，避免漏写。
                dirty = version > savedVersion;
                // 淘汰必须在 savedVersion 更新之后：否则刚落盘的条目仍被判为脏。
                cleanupCacheLocked();
            }
            if (written) {
                failureCount = 0L;
                retryDelayMillis = MIN_RETRY_DELAY_MILLIS;
            } else {
                failureCount++;
                retryDelayMillis = Math.min(
                        MAX_RETRY_DELAY_MILLIS,
                        MIN_RETRY_DELAY_MILLIS << (int) Math.min(failureCount - 1, 5)
                );
            }
            return written;
        }
    }

    private Snapshot snapshotLocked() {
        Map<String, String> globals = new LinkedHashMap<>(globalVariables);
        Map<UUID, Map<String, String>> players = new LinkedHashMap<>();
        playerVariables.forEach((uuid, data) -> players.put(uuid, new LinkedHashMap<>(data.values)));
        return new Snapshot(globals, players);
    }

    private boolean write(Snapshot snapshot) {
        try {
            Path target = storageFile.toPath();
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("global", new LinkedHashMap<>(snapshot.globals()));
            Map<String, Map<String, String>> players = new LinkedHashMap<>();
            snapshot.players().forEach((uuid, values) -> players.put(uuid.toString(), values));
            configuration.set("players", players);

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
            return true;
        } catch (IOException | RuntimeException e) {
            logger().log(Level.SEVERE,
                    "Could not save persistent menu variables to " + storageFile.getName()
                            + "; changes stay in memory and will be retried.", e);
            return false;
        }
    }

    private void markChangedLocked() {
        version++;
        dirty = true;
        scheduleSaveLocked(saveDelayMillis);
    }

    private void scheduleSaveLocked(long delayMillis) {
        if (!active) {
            return;
        }
        if (!saveScheduled.compareAndSet(false, true)) {
            return;
        }
        scheduler.schedule(this::runScheduledSave, Math.max(0L, delayMillis));
    }

    private void runScheduledSave() {
        saveScheduled.set(false);
        if (!active) {
            return;
        }
        boolean written = save(true);
        if (!active) {
            return;
        }
        if (!written) {
            // 失败：保留脏状态，按退避重试。
            scheduleSaveLocked(retryDelayMillisLocked());
            return;
        }
        if (dirty) {
            // 写盘期间又有新变更，再合并一次。
            scheduleSaveLocked(saveDelayMillis);
        }
    }

    private long retryDelayMillisLocked() {
        synchronized (ioLock) {
            return retryDelayMillis;
        }
    }

    // ------------------------------------------------------------------
    // 玩家缓存
    // ------------------------------------------------------------------

    private PlayerData playerDataLocked(UUID playerId) {
        PlayerData data = playerVariables.get(playerId);
        if (data == null) {
            data = new PlayerData();
            playerVariables.put(playerId, data);
        }
        data.lastAccessMillis = System.currentTimeMillis();
        data.lastModifiedVersion = version + 1;
        return data;
    }

    /**
     * 惰性回填：内存里没有该玩家时从磁盘读取单条记录。
     *
     * <p>读盘在锁外进行，避免持锁做 IO；并发情况下以先写入内存的为准。</p>
     */
    private void ensureLoaded(UUID playerId) {
        if (playerId == null) {
            return;
        }
        synchronized (this) {
            PlayerData data = playerVariables.get(playerId);
            if (data != null) {
                data.lastAccessMillis = System.currentTimeMillis();
                return;
            }
        }
        Map<String, String> loaded = readPlayerFromDisk(playerId);
        if (loaded.isEmpty()) {
            return;
        }
        synchronized (this) {
            PlayerData existing = playerVariables.get(playerId);
            if (existing != null) {
                existing.lastAccessMillis = System.currentTimeMillis();
                return;
            }
            PlayerData data = new PlayerData();
            data.values.putAll(loaded);
            data.lastAccessMillis = System.currentTimeMillis();
            data.lastModifiedVersion = savedVersion;
            playerVariables.put(playerId, data);
        }
    }

    private Map<String, String> readPlayerFromDisk(UUID playerId) {
        if (!storageFile.isFile()) {
            return Collections.emptyMap();
        }
        try {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(storageFile);
            ConfigurationSection section = configuration.getConfigurationSection("players." + playerId);
            if (section == null) {
                return Collections.emptyMap();
            }
            Map<String, String> values = new LinkedHashMap<>();
            readValues(section, values);
            return values;
        } catch (RuntimeException e) {
            logger().log(Level.WARNING, "Could not reload stored variables for " + playerId, e);
            return Collections.emptyMap();
        }
    }

    /** 淘汰已经落盘、且不再活跃的玩家缓存；脏条目一律保留。 */
    private void cleanupCacheLocked() {
        if (playerVariables.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (cacheTtlMillis > 0L) {
            playerVariables.entrySet().removeIf(entry ->
                    isEvictable(entry.getValue()) && now - entry.getValue().lastAccessMillis >= cacheTtlMillis);
        }
        if (cacheMaxPlayers > 0 && playerVariables.size() > cacheMaxPlayers) {
            int excess = playerVariables.size() - cacheMaxPlayers;
            List<Map.Entry<UUID, PlayerData>> candidates = new ArrayList<>();
            for (Map.Entry<UUID, PlayerData> entry : playerVariables.entrySet()) {
                if (isEvictable(entry.getValue())) {
                    candidates.add(entry);
                }
            }
            candidates.sort(Comparator.comparingLong(entry -> entry.getValue().lastAccessMillis));
            for (int index = 0; index < excess && index < candidates.size(); index++) {
                playerVariables.remove(candidates.get(index).getKey());
            }
        }
    }

    private boolean isEvictable(PlayerData data) {
        return data.lastModifiedVersion <= savedVersion;
    }

    private static final class PlayerData {
        private final Map<String, String> values = new LinkedHashMap<>();
        private long lastAccessMillis;
        /** 该条目最后一次被修改时的全局版本号；小于等于 savedVersion 表示已落盘。 */
        private long lastModifiedVersion;
    }

    private record Snapshot(Map<String, String> globals, Map<UUID, Map<String, String>> players) {
    }

    // ------------------------------------------------------------------
    // 读写辅助
    // ------------------------------------------------------------------

    private void readValues(ConfigurationSection section, Map<String, String> target) {
        if (section == null) {
            return;
        }
        section.getValues(false).forEach((key, value) -> {
            if (value != null && KEY_PATTERN.matcher(key).matches()) {
                target.put(normalizeKey(key), String.valueOf(value));
            } else if (value != null) {
                logger().warning("Ignoring invalid variable key in storage.yml: '" + key + "'");
            }
        });
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

    private static String replace(Pattern pattern, String text, Function<String, String> resolver) {
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

    private Logger logger() {
        Logger logger = plugin != null ? plugin.getLogger() : null;
        return logger != null ? logger : Logger.getLogger("MiaoMenu");
    }

    private static long readLong(MiaoMenu plugin, String key, long fallback, long min, long max) {
        FileConfiguration config = configOf(plugin);
        if (config == null) {
            return fallback;
        }
        try {
            long value = config.getLong(key, fallback);
            return Math.min(max, Math.max(min, value));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static FileConfiguration configOf(MiaoMenu plugin) {
        try {
            return plugin == null ? null : plugin.getConfig();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 生产实现：单线程守护线程池，与 Bukkit/Folia 调度器解耦（纯磁盘 IO）。
     */
    private static final class ExecutorSaveScheduler implements SaveScheduler {
        private ScheduledExecutorService executor;
        private boolean closed;

        @Override
        public synchronized void schedule(Runnable task, long delayMillis) {
            if (closed) {
                return;
            }
            if (executor == null) {
                executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "MiaoMenu-VariableStore");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            executor.schedule(() -> {
                try {
                    task.run();
                } catch (RuntimeException | LinkageError e) {
                    // 后台线程不能让异常逃逸，否则后续保存全部失效。
                    Logger.getLogger("MiaoMenu").log(Level.WARNING,
                            "Variable store background save failed", e);
                }
            }, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
        }

        @Override
        public synchronized void shutdown() {
            closed = true;
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
        }
    }
}
