package com.fluxcraft.MiaoMenu.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cn.handyplus.lib.adapter.HandySchedulerUtil;

/**
 * GitHub Release 更新检查。
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li>网络请求全部在异步线程执行，主线程只负责发消息；</li>
 *   <li>只做"检查"，从不下载或替换插件文件，是否升级由管理员决定；</li>
 *   <li>任何网络 / 解析异常都降级为一条可读提示，不会影响插件其它功能。</li>
 * </ul>
 */
public final class UpdateChecker {
    /** 能看到更新提示的权限。 */
    public static final String PERMISSION = "dgeysermenu.update";

    private static final String BUILD_INFO_RESOURCE = "build-info.properties";
    private static final String API_BASE = "https://api.github.com/repos/";
    private static final String DEFAULT_REPOSITORY = "Yamada0001/MiaoMenu";
    private static final int DEFAULT_TIMEOUT_MILLIS = 5000;
    private static final int MIN_TIMEOUT_MILLIS = 1000;
    private static final int MAX_TIMEOUT_MILLIS = 60000;
    /** 启动后延迟 5 秒再检查，避免和插件初始化抢带宽。 */
    private static final long STARTUP_DELAY_TICKS = 100L;
    private static final long TICKS_PER_HOUR = 72_000L;

    private final MiaoMenu plugin;
    private final String currentVersion;
    private final AtomicBoolean checking = new AtomicBoolean();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicInteger generation = new AtomicInteger();
    private final Set<UUID> notifiedPlayers = ConcurrentHashMap.newKeySet();

    private volatile boolean enabled;
    private volatile String repository;
    private volatile boolean includePrerelease;
    private volatile boolean notifyConsole;
    private volatile boolean notifyOnJoin;
    private volatile int intervalHours;
    private volatile Duration timeout;
    private volatile HttpClient httpClient;
    private volatile UpdateInfo lastResult;

    public UpdateChecker(MiaoMenu plugin) {
        this.plugin = plugin;
        this.currentVersion = detectCurrentVersion(plugin);
        reload();
    }

    /**
     * 重新读取配置。{@code interval-hours} 变化会让旧的定时链失效并重新排期。
     */
    public void reload() {
        var config = plugin.getConfig();
        this.enabled = config.getBoolean("settings.update-check.enabled", true);
        this.repository = sanitizeRepository(config.getString("settings.update-check.repository"));
        this.includePrerelease = config.getBoolean("settings.update-check.include-prerelease", false);
        this.notifyConsole = config.getBoolean("settings.update-check.notify-console", true);
        this.notifyOnJoin = config.getBoolean("settings.update-check.notify-on-join", true);
        this.intervalHours = Math.max(0, config.getInt("settings.update-check.interval-hours", 0));
        this.timeout = Duration.ofMillis(clamp(
                config.getInt("settings.update-check.timeout-millis", DEFAULT_TIMEOUT_MILLIS),
                MIN_TIMEOUT_MILLIS,
                MAX_TIMEOUT_MILLIS
        ));
        int currentGeneration = generation.incrementAndGet();
        if (!active.get() || !enabled || intervalHours <= 0) {
            return;
        }
        scheduleNext(currentGeneration);
    }

    /** 启动首次检查（异步、延迟执行）。 */
    public void start() {
        if (!enabled) {
            return;
        }
        int currentGeneration = generation.get();
        HandySchedulerUtil.runTaskLater(() -> {
            if (!active.get() || generation.get() != currentGeneration) {
                return;
            }
            checkNow(null);
        }, STARTUP_DELAY_TICKS);
    }

    /** 关闭后台资源；不会取消插件其它任务。 */
    public void shutdown() {
        active.set(false);
        generation.incrementAndGet();
        HttpClient client = this.httpClient;
        if (client != null) {
            try {
                client.shutdownNow();
            } catch (RuntimeException ignored) {
                // 关闭阶段失败无需处理
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String currentVersion() {
        return currentVersion;
    }

    public @Nullable UpdateInfo lastResult() {
        return lastResult;
    }

    /**
     * 立即检查一次。
     *
     * @param requester 触发者；为 {@code null} 表示后台定时检查，不回复消息
     */
    public void checkNow(@Nullable CommandSender requester) {
        if (!enabled) {
            reply(requester, Lang.get("update.disabled"));
            return;
        }
        if (!checking.compareAndSet(false, true)) {
            reply(requester, Lang.get("update.already-running"));
            return;
        }
        reply(requester, Lang.get("update.checking"));
        HandySchedulerUtil.runTaskAsynchronously(() -> {
            try {
                UpdateInfo info = fetch();
                lastResult = info;
                deliver(() -> report(info, requester));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                deliver(() -> reportFailure(requester, "interrupted"));
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.FINE, "Update check failed", e);
                deliver(() -> reportFailure(requester, e.getMessage()));
            } finally {
                checking.set(false);
            }
        });
    }

    /** 玩家进服时补发一次提示（每个玩家每次运行只提示一次）。 */
    public void notifyOnJoin(Player player) {
        if (!enabled || !notifyOnJoin || player == null) {
            return;
        }
        UpdateInfo info = lastResult;
        if (info == null || !info.updateAvailable()) {
            return;
        }
        if (!player.hasPermission(PERMISSION) || !notifiedPlayers.add(player.getUniqueId())) {
            return;
        }
        player.sendMessage(availableMessage(info));
    }

    private UpdateInfo fetch() throws IOException, InterruptedException {
        String endpoint = includePrerelease ? "/releases?per_page=10" : "/releases/latest";
        URI uri = URI.create(API_BASE + repository + endpoint);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MiaoMenu-UpdateChecker")
                .GET()
                .build();
        HttpResponse<String> response = client().send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        int status = response.statusCode();
        if (status == 404) {
            // 仓库还没有发布任何 Release
            return UpdateInfo.noRelease(currentVersion);
        }
        if (status == 403 || status == 429) {
            throw new IOException("GitHub API rate limit reached (HTTP " + status + ")");
        }
        if (status != 200) {
            throw new IOException("GitHub API returned HTTP " + status);
        }
        // 用实例方法解析：编译期类路径上 Geyser 内置的旧版 Gson 没有静态 parseString
        JsonElement root = new JsonParser().parse(response.body());
        return includePrerelease
                ? fromReleaseList(root, currentVersion, true)
                : fromSingleRelease(root, currentVersion);
    }

    private HttpClient client() {
        HttpClient existing = this.httpClient;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (this.httpClient == null) {
                this.httpClient = HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            return this.httpClient;
        }
    }

    private void report(UpdateInfo info, @Nullable CommandSender requester) {
        if (info.latestVersion() == null) {
            reply(requester, Lang.get("update.no-releases"));
            return;
        }
        if (!info.updateAvailable()) {
            reply(requester, Lang.get("update.up-to-date").replace("{0}", info.currentVersion()));
            return;
        }
        String message = availableMessage(info);
        if (notifyConsole) {
            plugin.getLogger().info(stripColorCodes(message));
        }
        reply(requester, message);
        notifyPermissionHolders(message);
    }

    private void reportFailure(@Nullable CommandSender requester, @Nullable String reason) {
        String message = Lang.get("update.failed").replace("{0}", reason == null ? "unknown" : reason);
        if (notifyConsole) {
            plugin.getLogger().warning(stripColorCodes(message));
        }
        reply(requester, message);
    }

    private String availableMessage(UpdateInfo info) {
        return Lang.get("update.available")
                .replace("{0}", info.displayVersion())
                .replace("{1}", info.currentVersion())
                .replace("{2}", info.releaseUrl() == null ? "" : info.releaseUrl());
    }

    private void notifyPermissionHolders(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission(PERMISSION)) {
                continue;
            }
            if (notifiedPlayers.add(player.getUniqueId())) {
                player.sendMessage(message);
            }
        }
    }

    private void reply(@Nullable CommandSender requester, String message) {
        if (requester != null) {
            requester.sendMessage(message);
        }
    }

    private void deliver(Runnable task) {
        if (!active.get()) {
            return;
        }
        HandySchedulerUtil.runTask(task);
    }

    private void scheduleNext(int myGeneration) {
        HandySchedulerUtil.runTaskLater(() -> {
            if (!active.get() || generation.get() != myGeneration) {
                return;
            }
            checkNow(null);
            scheduleNext(myGeneration);
        }, intervalHours * TICKS_PER_HOUR);
    }

    /**
     * 读取打包时写入的 {@code build-info.properties}；本地构建没有该文件时退回插件版本。
     */
    static String detectCurrentVersion(Plugin plugin) {
        try (InputStream stream = plugin.getResource(BUILD_INFO_RESOURCE)) {
            if (stream != null) {
                Properties properties = new Properties();
                properties.load(stream);
                String version = properties.getProperty("version");
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // 打包信息缺失不是错误，退回 plugin.yml 的版本
        }
        return plugin.getPluginMeta().getVersion();
    }

    /** 解析 {@code /releases/latest} 的响应。 */
    static UpdateInfo fromSingleRelease(JsonElement root, String currentVersion) {
        if (root == null || !root.isJsonObject()) {
            return UpdateInfo.noRelease(currentVersion);
        }
        JsonObject release = root.getAsJsonObject();
        if (isTruthy(release, "draft")) {
            return UpdateInfo.noRelease(currentVersion);
        }
        return toInfo(release, currentVersion);
    }

    /** 解析 {@code /releases} 列表的响应：取第一条非草稿（必要时跳过预发布）的版本。 */
    static UpdateInfo fromReleaseList(JsonElement root, String currentVersion, boolean allowPrerelease) {
        if (root == null || !root.isJsonArray()) {
            return UpdateInfo.noRelease(currentVersion);
        }
        JsonArray releases = root.getAsJsonArray();
        for (JsonElement element : releases) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject release = element.getAsJsonObject();
            if (isTruthy(release, "draft")) {
                continue;
            }
            if (!allowPrerelease && isTruthy(release, "prerelease")) {
                continue;
            }
            return toInfo(release, currentVersion);
        }
        return UpdateInfo.noRelease(currentVersion);
    }

    private static UpdateInfo toInfo(JsonObject release, String currentVersion) {
        String tag = string(release, "tag_name");
        String name = string(release, "name");
        String url = string(release, "html_url");
        boolean newer = PluginVersion.isNewer(tag, currentVersion);
        return new UpdateInfo(currentVersion, tag, name, url, newer);
    }

    private static @Nullable String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean isTruthy(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static String sanitizeRepository(@Nullable String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_REPOSITORY;
        }
        String value = configured.trim();
        // 只允许 owner/repo 形式，避免把用户输入拼进 URL 路径
        if (!value.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+")) {
            return DEFAULT_REPOSITORY;
        }
        return value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    /** 控制台不渲染颜色码。 */
    private static String stripColorCodes(String message) {
        return message.replaceAll("\u00a7[0-9a-fk-orx]", "");
    }
}
