package com.fluxcraft.MiaoMenu.theme;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.google.gson.JsonParser;

import cn.handyplus.lib.adapter.HandySchedulerUtil;

/**
 * 从 {@code MiaoMenu-theme} 仓库下载主题并安装到插件数据目录。
 *
 * <p>流程：拉取 {@code themes.json} 清单 → 下载主题文件到临时目录 → 校验 →
 * 合并进 {@code java_menus/}、{@code dialog_menus/}、{@code themes/} → 热重载菜单。
 * 所有网络请求都在异步线程执行；已存在的文件默认不覆盖，需要显式 {@code force}。</p>
 */
public final class ThemeRepository {
    public static final String PERMISSION = "dgeysermenu.download";

    private static final String DEFAULT_REPOSITORY = "Yamada0001/MiaoMenu-theme";
    private static final String DEFAULT_BRANCH = "main";
    private static final String MANIFEST_PATH = "themes.json";
    private static final int DEFAULT_TIMEOUT_MILLIS = 8000;
    private static final String RAW_BASE = "https://raw.githubusercontent.com/";

    private final MiaoMenu plugin;
    private final String rawBaseOverride;
    private final AtomicBoolean busy = new AtomicBoolean();
    /** 清单拉取失败后的退避，避免 Tab 补全把网络请求打爆。 */
    private static final long MANIFEST_RETRY_MILLIS = 60_000L;
    private final AtomicBoolean loadingManifest = new AtomicBoolean();
    private volatile long lastManifestAttemptAt;
    private volatile @Nullable ThemeManifest cachedManifest;
    private volatile @Nullable HttpClient httpClient;

    public ThemeRepository(MiaoMenu plugin) {
        this(plugin, null);
    }

    /**
     * @param rawBaseOverride 自定义仓库根地址（主要用于测试或自建镜像）；
     *                        {@code null} 表示按配置拼出 GitHub raw 地址
     */
    public ThemeRepository(MiaoMenu plugin, @Nullable String rawBaseOverride) {
        this.plugin = plugin;
        this.rawBaseOverride = rawBaseOverride;
    }

    /** 已缓存的主题清单（可能为 {@code null}，表示还没拉取过）。 */
    public @Nullable ThemeManifest cachedManifest() {
        return cachedManifest;
    }

    /** 供 Tab 补全使用的主题 ID 列表；未加载时返回空列表。 */
    public List<String> cachedThemeIds() {
        ThemeManifest manifest = cachedManifest;
        if (manifest == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(manifest.themes().size());
        for (ThemeManifest.Theme theme : manifest.themes()) {
            ids.add(theme.id());
        }
        return ids;
    }

    /**
     * 在后台刷新清单（用于 Tab 补全预热与 {@code download list}）。
     */
    public CompletableFuture<@Nullable ThemeManifest> loadManifestAsync() {
        lastManifestAttemptAt = System.currentTimeMillis();
        loadingManifest.set(true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                ThemeManifest manifest = fetchManifest();
                cachedManifest = manifest;
                return manifest;
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.FINE, "Could not load the theme manifest", e);
                return null;
            } finally {
                loadingManifest.set(false);
            }
        });
    }

    /**
     * Tab 补全用：按需触发一次清单加载。
     *
     * <p>已有清单、正在加载、或距离上次失败不足 {@link #MANIFEST_RETRY_MILLIS} 时都不会重复请求。</p>
     */
    public void ensureManifestLoading() {
        if (cachedManifest != null || loadingManifest.get()) {
            return;
        }
        if (System.currentTimeMillis() - lastManifestAttemptAt < MANIFEST_RETRY_MILLIS) {
            return;
        }
        loadManifestAsync();
    }

    /**
     * 下载并安装指定主题。
     *
     * @param requester 触发者，用于回复进度与结果
     * @param themeId   主题 ID
     * @param force     是否覆盖已存在的菜单/资源文件
     */
    public void installAsync(CommandSender requester, String themeId, boolean force) {
        if (!busy.compareAndSet(false, true)) {
            reply(requester, Lang.get("theme.busy"));
            return;
        }
        reply(requester, Lang.get("theme.downloading").replace("{0}", themeId));
        HandySchedulerUtil.runTaskAsynchronously(() -> {
            InstallReport report;
            try {
                report = install(themeId, force);
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Theme install failed: " + themeId, e);
                report = InstallReport.failed(e.getMessage());
            } finally {
                busy.set(false);
            }
            InstallReport finalReport = report;
            HandySchedulerUtil.runTask(() -> finish(requester, themeId, finalReport));
        });
    }

    /** 同步安装实现（包内可见，便于测试直接驱动）。 */
    InstallReport install(String themeId, boolean force) throws IOException {
        ThemeManifest manifest = cachedManifest != null ? cachedManifest : fetchManifest();
        cachedManifest = manifest;
        ThemeManifest.Theme theme = manifest.find(themeId);
        if (theme == null) {
            return InstallReport.failed(Lang.get("theme.unknown")
                    .replace("{0}", themeId)
                    .replace("{1}", Lang.get("command.main-alias")));
        }

        Path dataFolder = plugin.getDataFolder().toPath();
        Path staging = Files.createTempDirectory("miaomenu-theme-");
        List<String> written = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        try {
            for (ThemeManifest.FileEntry entry : theme.files()) {
                byte[] content = download(entry.source());
                if (entry.sha256() != null && !matchesSha256(content, entry.sha256())) {
                    throw new IOException("Checksum mismatch for " + entry.source());
                }
                Path target = dataFolder.resolve(entry.target()).normalize();
                if (!target.startsWith(dataFolder)) {
                    throw new IOException("Refusing to write outside the data folder: " + entry.target());
                }
                if (Files.exists(target) && !force) {
                    skipped.add(entry.target());
                    continue;
                }
                Files.createDirectories(target.getParent());
                Path staged = staging.resolve(entry.target().replace('/', '_'));
                Files.write(staged, content);
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                written.add(entry.target());
            }
        } finally {
            deleteQuietly(staging);
        }
        return new InstallReport(true, null, theme, List.copyOf(written), List.copyOf(skipped));
    }

    /** 安装完成后的收尾：热重载 + 回复结果。 */
    private void finish(CommandSender requester, String themeId, InstallReport report) {
        if (!report.success()) {
            reply(requester, Lang.get("theme.failed").replace("{0}", themeId)
                    .replace("{1}", report.error() == null ? "unknown" : report.error()));
            return;
        }
        ThemeManifest.Theme theme = report.theme();
        if (!report.written().isEmpty()) {
            plugin.reloadAllMenus();
        }
        reply(requester, Lang.get("theme.installed")
                .replace("{0}", theme.displayName())
                .replace("{1}", String.valueOf(report.written().size())));
        if (!report.skipped().isEmpty()) {
            reply(requester, Lang.get("theme.skipped")
                    .replace("{0}", String.valueOf(report.skipped().size())));
        }
        if (theme.javaMenu() != null) {
            reply(requester, Lang.get("theme.open-hint")
                    .replace("{0}", theme.javaMenu()));
        }
        if (theme.dialogMenu() != null) {
            reply(requester, Lang.get("theme.open-dialog-hint")
                    .replace("{0}", theme.dialogMenu()));
        }
    }

    private ThemeManifest fetchManifest() throws IOException {
        String url = rawBase() + MANIFEST_PATH;
        byte[] body = httpGet(url);
        ThemeManifest manifest = ThemeManifest.parse(
                new JsonParser().parse(new String(body, StandardCharsets.UTF_8))
        );
        if (manifest == null) {
            throw new IOException("Invalid theme manifest at " + url);
        }
        return manifest;
    }

    private byte[] download(String source) throws IOException {
        return httpGet(rawBase() + source);
    }

    private byte[] httpGet(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout())
                .header("User-Agent", "MiaoMenu-ThemeDownloader")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = client().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                throw new IOException("Not found: " + url);
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, e);
        }
    }

    private String rawBase() {
        if (rawBaseOverride != null) {
            return rawBaseOverride.endsWith("/") ? rawBaseOverride : rawBaseOverride + "/";
        }
        String repository = sanitizeRepository(plugin.getConfig()
                .getString("settings.theme-repository.repository", DEFAULT_REPOSITORY));
        String branch = sanitizeBranch(plugin.getConfig()
                .getString("settings.theme-repository.branch", DEFAULT_BRANCH));
        return RAW_BASE + repository + "/" + branch + "/";
    }

    private Duration timeout() {
        int millis = plugin.getConfig().getInt("settings.theme-repository.timeout-millis", DEFAULT_TIMEOUT_MILLIS);
        return Duration.ofMillis(Math.min(60000, Math.max(1000, millis)));
    }

    private HttpClient client() {
        HttpClient existing = this.httpClient;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (this.httpClient == null) {
                this.httpClient = HttpClient.newBuilder()
                        .connectTimeout(timeout())
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            return this.httpClient;
        }
    }

    private void reply(CommandSender requester, String message) {
        if (requester != null) {
            requester.sendMessage(message);
        }
    }

    static boolean matchesSha256(byte[] content, String expected) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest).equalsIgnoreCase(expected.trim());
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private static String sanitizeRepository(@Nullable String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_REPOSITORY;
        }
        String value = configured.trim();
        return value.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+") ? value : DEFAULT_REPOSITORY;
    }

    private static String sanitizeBranch(@Nullable String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_BRANCH;
        }
        String value = configured.trim().toLowerCase(Locale.ROOT);
        return value.matches("[a-z0-9._/-]{1,128}") ? value : DEFAULT_BRANCH;
    }

    private static void deleteQuietly(Path directory) {
        try (var stream = Files.walk(directory)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响安装结果
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
    }

    /**
     * 一次安装的结果。
     *
     * @param written 实际写入的相对路径
     * @param skipped 因已存在而跳过的相对路径
     */
    public record InstallReport(
            boolean success,
            @Nullable String error,
            ThemeManifest.@Nullable Theme theme,
            List<String> written,
            List<String> skipped
    ) {
        public InstallReport {
            written = written == null ? List.of() : List.copyOf(written);
            skipped = skipped == null ? List.of() : List.copyOf(skipped);
        }

        static InstallReport failed(@Nullable String error) {
            return new InstallReport(false, error, null, List.of(), List.of());
        }
    }
}
