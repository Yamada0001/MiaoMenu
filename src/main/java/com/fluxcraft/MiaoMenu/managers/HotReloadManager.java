package com.fluxcraft.MiaoMenu.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

/**
 * 基于 WatchService 的文件热重载。
 *
 * <p>采用"尾随防抖"：每次事件都会把重载时间点往后推，只有在最后一次事件之后
 * 静默 {@value #DEBOUNCE_DELAY_MS} 毫秒才真正执行，避免读到半写入的文件。</p>
 */
public class HotReloadManager {
    private static final String JAVA_MENU_DIR = "java_menus";
    private static final String BEDROCK_MENU_DIR = "bedrock_menus";
    private static final String DIALOG_MENU_DIR = "dialog_menus";
    private static final String LANG_DIR = "lang";
    private static final String CONFIG_FILE = "config.yml";
    private static final String FILE_EXTENSION = ".yml";
    private static final long DEBOUNCE_DELAY_MS = 500;
    private static final long TICK_MILLIS = 50;

    private final MiaoMenu plugin;
    private final Map<WatchKey, Path> keys = new ConcurrentHashMap<>();
    private final Set<ReloadKind> pendingKinds = EnumSet.noneOf(ReloadKind.class);
    private final AtomicBoolean reloadScheduled = new AtomicBoolean(false);
    private volatile long lastEventAt;
    private WatchService watchService;
    private volatile boolean running = false;
    private Thread watcherThread;

    public HotReloadManager(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    private enum ReloadKind {
        CONFIG,
        JAVA_MENUS,
        BEDROCK_MENUS,
        DIALOG_MENUS,
        LANG
    }

    public synchronized void initialize() throws IOException {
        if (running) {
            return;
        }
        WatchService service = FileSystems.getDefault().newWatchService();
        try {
            registerDirectory(service, plugin.getDataFolder().toPath());
            registerDirectory(service, new File(plugin.getDataFolder(), JAVA_MENU_DIR).toPath());
            registerDirectory(service, new File(plugin.getDataFolder(), BEDROCK_MENU_DIR).toPath());
            registerDirectory(service, new File(plugin.getDataFolder(), DIALOG_MENU_DIR).toPath());
            registerDirectory(service, new File(plugin.getDataFolder(), LANG_DIR).toPath());
        } catch (IOException | RuntimeException e) {
            // 注册失败时回滚，避免留下无人消费的 WatchService。
            keys.clear();
            try {
                service.close();
            } catch (IOException ignored) {
                // 关闭失败无补救手段，忽略。
            }
            throw e;
        }
        this.watchService = service;
        this.running = true;
        plugin.getLogger().info(Lang.get("hot-reload.initialized"));
        startWatcher();
    }

    private void registerDirectory(WatchService service, Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        WatchKey key = dir.register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
        );
        keys.put(key, dir);
    }

    @SuppressWarnings("unchecked")
    private void startWatcher() {
        watcherThread = new Thread(() -> {
            while (running) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException e) {
                    break;
                }
                boolean stopWatching = false;
                try {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }
                        Path filename = ((WatchEvent<Path>) event).context();
                        Path dir = keys.get(key);
                        if (dir != null) {
                            dispatch(dir, filename);
                        }
                    }
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, Lang.get("log.hot-reload.loop-failed"), e);
                } finally {
                    // reset 必须执行，否则该目录会永久失联。
                    if (!key.reset()) {
                        keys.remove(key);
                        stopWatching = keys.isEmpty();
                    }
                }
                if (stopWatching) {
                    break;
                }
            }
        }, "MiaoMenu-HotReload-Thread");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private void dispatch(Path dir, Path filename) {
        String fileName = filename.toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(FILE_EXTENSION)) {
            return;
        }
        Path dataFolder = plugin.getDataFolder().toPath();
        if (dir.equals(dataFolder)) {
            if (fileName.equals(CONFIG_FILE)) {
                requestReload(ReloadKind.CONFIG);
            }
            // 根目录下的其它 yml（commands.yml / tasks.yml / storage.yml）不触发菜单重载。
            return;
        }
        String dirName = dir.getFileName() == null ? "" : dir.getFileName().toString().toLowerCase(Locale.ROOT);
        switch (dirName) {
            case JAVA_MENU_DIR -> requestReload(ReloadKind.JAVA_MENUS);
            case BEDROCK_MENU_DIR -> requestReload(ReloadKind.BEDROCK_MENUS);
            case DIALOG_MENU_DIR -> requestReload(ReloadKind.DIALOG_MENUS);
            case LANG_DIR -> requestReload(ReloadKind.LANG);
            default -> {
                // 其它目录不参与热重载。
            }
        }
    }

    private void requestReload(ReloadKind kind) {
        synchronized (pendingKinds) {
            pendingKinds.add(kind);
        }
        lastEventAt = System.currentTimeMillis();
        if (reloadScheduled.compareAndSet(false, true)) {
            scheduleDebounced();
        }
    }

    /**
     * 尾随防抖：距离最后一次事件不足窗口时继续等待。
     */
    private void scheduleDebounced() {
        long elapsed = System.currentTimeMillis() - lastEventAt;
        long remaining = DEBOUNCE_DELAY_MS - elapsed;
        long delayTicks = remaining <= 0 ? 1 : Math.max(1, (remaining + TICK_MILLIS - 1) / TICK_MILLIS);
        runLater(() -> {
            if (System.currentTimeMillis() - lastEventAt < DEBOUNCE_DELAY_MS) {
                scheduleDebounced();
                return;
            }
            reloadScheduled.set(false);
            Set<ReloadKind> kinds;
            synchronized (pendingKinds) {
                kinds = EnumSet.copyOf(pendingKinds);
                pendingKinds.clear();
            }
            execute(kinds);
        }, delayTicks);
    }

    private void execute(Set<ReloadKind> kinds) {
        if (kinds.isEmpty()) {
            return;
        }
        plugin.getLogger().info(Lang.get("hot-reload.detected")
                .replace("{0}", kinds.toString().toLowerCase(Locale.ROOT)));
        try {
            if (kinds.contains(ReloadKind.CONFIG)) {
                plugin.reloadAll();
            } else {
                if (kinds.contains(ReloadKind.LANG)) {
                    Lang.reload();
                    PlaceholderUtils.reset();
                }
                plugin.reloadAllMenus();
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, Lang.get("log.hot-reload.reload-failed"), e);
            return;
        }
        plugin.getLogger().info(Lang.get("message.reloaded"));
    }

    private void runLater(Runnable task, long delayTicks) {
        if (FoliaFactory.isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, _ -> task.run(), Math.max(1, delayTicks));
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, delayTicks));
    }

    public synchronized void shutdown() {
        running = false;
        if (watcherThread != null) {
            watcherThread.interrupt();
            watcherThread = null;
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
                // 关闭失败无补救手段，忽略。
            }
            watchService = null;
        }
        keys.clear();
        synchronized (pendingKinds) {
            pendingKinds.clear();
        }
        reloadScheduled.set(false);
    }
}
