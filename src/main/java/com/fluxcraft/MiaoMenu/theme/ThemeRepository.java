package com.fluxcraft.MiaoMenu.theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import com.fluxcraft.MiaoMenu.net.HttpFetcher;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.google.gson.JsonParser;

import cn.handyplus.lib.adapter.HandySchedulerUtil;

/**
 * 从 {@code MiaoMenu-theme} 仓库下载主题并安装到插件数据目录。
 *
 * <p>流程：拉取 {@code themes.json} 清单 → 下载主题文件到临时目录 → 校验 →
 * 合并进 {@code java_menus/}、{@code dialog_menus/}、{@code themes/} → 热重载菜单
 * → 重新下发资源包。</p>
 *
 * <p>所有网络请求都在异步线程执行；已存在的文件默认不覆盖，需要显式 {@code force}。
 * 下载源统一由 {@link HttpFetcher} 处理（默认走 gh-proxy，国内可直连）。</p>
 */
public final class ThemeRepository {
    public static final String PERMISSION = "dgeysermenu.download";

    private static final String DEFAULT_REPOSITORY = "Yamada0001/MiaoMenu-theme";
    private static final String DEFAULT_BRANCH = "main";
    private static final String MANIFEST_PATH = "themes.json";
    private static final int DEFAULT_TIMEOUT_MILLIS = 15000;
    /**
     * 默认下载源：gh-proxy 代理 GitHub raw。
     *
     * <p>直连 {@code raw.githubusercontent.com} 与 {@code cdn.jsdelivr.net} 在部分
     * 网络下会 TLS 握手失败或被重置，而 gh-proxy 可以稳定转发。</p>
     */
    private static final List<String> DEFAULT_SOURCES = List.of(
            "https://gh-proxy.com/https://raw.githubusercontent.com/{repository}/{branch}/{path}"
    );

    private final MiaoMenu plugin;
    private final String rawBaseOverride;
    private final AtomicBoolean busy = new AtomicBoolean();
    /** 清单拉取失败后的退避，避免 Tab 补全把网络请求打爆。 */
    private static final long MANIFEST_RETRY_MILLIS = 60_000L;
    private final AtomicBoolean loadingManifest = new AtomicBoolean();
    private volatile long lastManifestAttemptAt;
    private volatile @Nullable ThemeManifest cachedManifest;
    private volatile @Nullable HttpFetcher fetcher;
    private volatile @Nullable String lastManifestError;
    /** 插件禁用后置 false：异步任务不再发起请求，也不再回调插件。 */
    private volatile boolean active = true;
    /** 当前正在安装的主题（用于取消）。 */
    private volatile @Nullable String installingThemeId;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public ThemeRepository(MiaoMenu plugin) {
        this(plugin, null);
    }

    /**
     * @param rawBaseOverride 自定义仓库根地址（主要用于测试或自建镜像）；
     *                        {@code null} 表示按配置构造下载源
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

    /** 最近一次清单拉取失败的原因，供 {@code download} 直接回显给玩家。 */
    public @Nullable String lastManifestError() {
        return lastManifestError;
    }

    /** 当前正在安装的主题 ID；没有安装任务时为 {@code null}。 */
    public @Nullable String installingThemeId() {
        return installingThemeId;
    }

    /** 请求取消当前安装；下一个文件边界处生效。 */
    public boolean cancelInstall() {
        if (!busy.get()) {
            return false;
        }
        cancelled.set(true);
        return true;
    }

    /**
     * 在后台刷新清单（用于 Tab 补全预热与 {@code download list}）。
     */
    public CompletableFuture<@Nullable ThemeManifest> loadManifestAsync() {
        if (!active) {
            return CompletableFuture.completedFuture(null);
        }
        lastManifestAttemptAt = System.currentTimeMillis();
        loadingManifest.set(true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                ThemeManifest manifest = fetchManifest();
                if (!active) {
                    return null;
                }
                cachedManifest = manifest;
                lastManifestError = null;
                return manifest;
            } catch (IOException | RuntimeException e) {
                lastManifestError = e.getMessage();
                if (active) {
                    plugin.getLogger().log(Level.FINE, "Could not load the theme manifest", e);
                }
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
     * 插件禁用时调用：停止后续请求与回调。
     *
     * <p>已经在进行的网络请求无法中断，但完成时不会再访问插件对象。</p>
     */
    public void shutdown() {
        active = false;
        cancelled.set(true);
        busy.set(false);
        loadingManifest.set(false);
        installingThemeId = null;
    }

    /**
     * 下载并安装指定主题。
     *
     * @param requester 触发者，用于回复进度与结果
     * @param themeId   主题 ID
     * @param force     是否覆盖已存在的菜单/资源文件
     */
    public void installAsync(CommandSender requester, String themeId, boolean force) {
        if (!active) {
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            reply(requester, Lang.get("theme.busy"));
            return;
        }
        cancelled.set(false);
        installingThemeId = themeId;
        reply(requester, Lang.get("theme.downloading").replace("{0}", themeId));
        ThemeProgress progress = new ThemeProgress(plugin, requester, themeId);
        progress.start();
        HandySchedulerUtil.runTaskAsynchronously(() -> {
            InstallReport report;
            ThemePackExporter.ExportResult packExport = ThemePackExporter.ExportResult.none();
            try {
                report = install(themeId, force, progress);
                packExport = exportThemePack(report);
            } catch (HttpFetcher.CancelledException e) {
                report = InstallReport.cancelledReport();
            } catch (IOException | RuntimeException e) {
                if (active) {
                    plugin.getLogger().log(Level.WARNING, "Theme install failed: " + themeId, e);
                }
                report = InstallReport.failed(e.getMessage());
            } finally {
                busy.set(false);
                installingThemeId = null;
            }
            InstallReport finalReport = report;
            ThemePackExporter.ExportResult finalPackExport = packExport;
            if (!active) {
                return;
            }
            HandySchedulerUtil.runTask(() -> {
                progress.finish();
                if (active) {
                    finish(requester, themeId, finalReport, finalPackExport);
                }
            });
        });
    }

    /**
     * 把主题贴图交给 CraftEngine / ItemsAdder / Oraxen 打包。
     *
     * <p>服务器已有资源包插件时，服主不需要再自己托管 zip：这些插件会把
     * {@code assets/} 合并进自己的资源包。</p>
     */
    private ThemePackExporter.ExportResult exportThemePack(InstallReport report) {
        if (!report.success() || report.written().isEmpty()) {
            return ThemePackExporter.ExportResult.none();
        }
        ThemePackExporter exporter = plugin.getThemePackExporter();
        if (exporter == null) {
            return ThemePackExporter.ExportResult.none();
        }
        ThemePackExporter.ExportResult result = exporter.export(
                plugin.getDataFolder().toPath().resolve("themes").resolve("assets"));
        if (result.exported()) {
            exporter.reloadProvider(result.provider());
        } else if (result.error() != null && active) {
            plugin.getLogger().warning("Could not export theme assets to "
                    + result.provider().pluginName() + ": " + result.error());
        }
        return result;
    }

    /** 同步安装实现（包内可见，便于测试直接驱动）。 */
    InstallReport install(String themeId, boolean force) throws IOException {
        return install(themeId, force, null);
    }

    private InstallReport install(String themeId, boolean force, @Nullable ThemeProgress progress) throws IOException {
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
        List<ThemeManifest.FileEntry> pending = new ArrayList<>();
        for (ThemeManifest.FileEntry entry : theme.files()) {
            Path target = dataFolder.resolve(entry.target()).normalize();
            if (!target.startsWith(dataFolder)) {
                throw new IOException("Refusing to write outside the data folder: " + entry.target());
            }
            if (Files.exists(target) && !force) {
                skipped.add(entry.target());
                continue;
            }
            pending.add(entry);
        }

        long totalBytes = -1L;
        int index = 0;
        try {
            for (ThemeManifest.FileEntry entry : pending) {
                throwIfCancelled();
                if (progress != null) {
                    progress.updateFile(index, pending.size(), entry.target(), totalBytes);
                }
                byte[] content = download(entry.source(), progress, index, pending.size());
                if (entry.sha256() != null && !matchesSha256(content, entry.sha256())) {
                    throw new IOException("Checksum mismatch for " + entry.source());
                }
                Path target = dataFolder.resolve(entry.target()).normalize();
                Files.createDirectories(target.getParent());
                Path staged = staging.resolve(entry.target().replace('/', '_'));
                Files.write(staged, content);
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                written.add(entry.target());
                index++;
            }
        } finally {
            deleteQuietly(staging);
        }
        return new InstallReport(true, null, theme, List.copyOf(written), List.copyOf(skipped), false);
    }

    /** 安装完成后的收尾：热重载 + 资源包重发 + 回复结果。 */
    private void finish(
            CommandSender requester,
            String themeId,
            InstallReport report,
            ThemePackExporter.ExportResult packExport
    ) {
        if (report.cancelledInstall()) {
            reply(requester, Lang.get("theme.cancelled").replace("{0}", themeId));
            return;
        }
        if (!report.success()) {
            reply(requester, Lang.get("theme.failed").replace("{0}", themeId)
                    .replace("{1}", report.error() == null ? "unknown" : report.error()));
            return;
        }
        ThemeManifest.Theme theme = report.theme();
        if (theme == null) {
            return;
        }
        if (!report.written().isEmpty()) {
            plugin.reloadAllMenus();
            // 主题图片来自资源包：重载菜单后立刻重发，避免玩家还要重进服务器。
            plugin.getResourcePackManager().resendToAll();
        }
        reply(requester, Lang.get("theme.installed")
                .replace("{0}", theme.displayName())
                .replace("{1}", String.valueOf(report.written().size())));
        if (!report.skipped().isEmpty()) {
            reply(requester, Lang.get("theme.skipped")
                    .replace("{0}", String.valueOf(report.skipped().size())));
        }
        String mainAlias = Lang.get("command.main-alias");
        if (theme.javaMenu() != null) {
            reply(requester, Lang.get("theme.open-hint")
                    .replace("{0}", mainAlias)
                    .replace("{1}", theme.javaMenu()));
        }
        if (theme.dialogMenu() != null) {
            reply(requester, Lang.get("theme.open-dialog-hint")
                    .replace("{0}", mainAlias)
                    .replace("{1}", theme.dialogMenu()));
        }
        replyPackStatus(requester, packExport, mainAlias);
        sendOpenButton(requester, theme, mainAlias);
    }

    /** 告诉服主贴图是怎么生效的：交给资源包插件，还是需要自己配资源包。 */
    private void replyPackStatus(
            CommandSender requester,
            ThemePackExporter.ExportResult packExport,
            String mainAlias
    ) {
        if (packExport.exported()) {
            reply(requester, Lang.get("theme.pack-exported")
                    .replace("{0}", packExport.provider().pluginName())
                    .replace("{1}", String.valueOf(packExport.files()))
                    .replace("{2}", packExport.target() == null ? "-" : packExport.target().toString()));
            return;
        }
        if (!plugin.getResourcePackManager().isEnabled()) {
            reply(requester, Lang.get("theme.pack-hint").replace("{0}", mainAlias));
        }
    }

    /**
     * 安装完成后给一个可点击的打开入口。
     *
     * <p>只给玩家发可点击组件；控制台只能看文字提示，避免出现点不动的按钮。</p>
     */
    private void sendOpenButton(CommandSender requester, ThemeManifest.Theme theme, String mainAlias) {
        String dialogMenu = theme.dialogMenu();
        String javaMenu = theme.javaMenu();
        String target;
        if (dialogMenu != null) {
            target = "dialog:" + dialogMenu;
        } else if (javaMenu != null) {
            target = javaMenu;
        } else {
            return;
        }
        if (!(requester instanceof org.bukkit.entity.Player player)) {
            return;
        }
        String command = "/" + mainAlias + " open " + target;
        player.sendMessage(com.fluxcraft.MiaoMenu.utils.MenuText.clickableCommand(
                Lang.get("theme.open-button").replace("{0}", theme.displayName()),
                command,
                Lang.get("theme.open-button-hover").replace("{0}", command)
        ));
    }

    private void throwIfCancelled() throws HttpFetcher.CancelledException {
        if (cancelled.get() || !active) {
            throw new HttpFetcher.CancelledException("Theme install cancelled");
        }
    }

    private ThemeManifest fetchManifest() throws IOException {
        String body = fetcher().fetchText(MANIFEST_PATH);
        ThemeManifest manifest = ThemeManifest.parse(new JsonParser().parse(body));
        if (manifest == null) {
            throw new IOException("Invalid theme manifest at " + fetcher().resolve(MANIFEST_PATH));
        }
        return manifest;
    }

    private byte[] download(String source, @Nullable ThemeProgress progress, int index, int total)
            throws IOException {
        return fetcher().fetch(
                source,
                progress == null ? null : (done, size) -> progress.updateBytes(index, total, done, size),
                cancelled::get
        );
    }

    /** 已配置的下载源模板（按优先级排列，仓库与分支占位符已替换）。 */
    List<String> sourceTemplates() {
        if (rawBaseOverride != null) {
            String base = rawBaseOverride.endsWith("/") ? rawBaseOverride : rawBaseOverride + "/";
            return List.of(base + "{path}");
        }
        String repository = sanitizeRepository(plugin.getConfig()
                .getString("settings.theme-repository.repository", DEFAULT_REPOSITORY));
        String branch = sanitizeBranch(plugin.getConfig()
                .getString("settings.theme-repository.branch", DEFAULT_BRANCH));
        List<String> configured = plugin.getConfig()
                .getStringList("settings.theme-repository.sources");
        if (configured.isEmpty()) {
            configured = DEFAULT_SOURCES;
        }
        List<String> templates = new ArrayList<>();
        for (String raw : configured) {
            String template = buildTemplate(raw);
            if (template != null) {
                templates.add(template
                        .replace("{repository}", repository)
                        .replace("{branch}", branch));
            }
        }
        return templates.isEmpty() ? List.of() : List.copyOf(templates);
    }

    /** 用当前配置解析某个仓库内路径的下载地址（取优先级最高的源）。 */
    String resolveUrl(String path) {
        return fetcher().resolve(path);
    }

    private HttpFetcher fetcher() {
        HttpFetcher existing = this.fetcher;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (this.fetcher == null) {
                List<String> templates = sourceTemplates();
                this.fetcher = new HttpFetcher(
                        // 模板里的仓库/分支已在 sourceTemplates() 中替换，这里不再重复。
                        templates.isEmpty() ? DEFAULT_SOURCES : templates,
                        sanitizeRepository(plugin.getConfig()
                                .getString("settings.theme-repository.repository", DEFAULT_REPOSITORY)),
                        sanitizeBranch(plugin.getConfig()
                                .getString("settings.theme-repository.branch", DEFAULT_BRANCH)),
                        timeoutMillis()
                );
            }
            return this.fetcher;
        }
    }

    private int timeoutMillis() {
        return plugin.getConfig().getInt("settings.theme-repository.timeout-millis", DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * 把配置里的源模板补全成可用模板：没有 {@code {path}} 的条目按前缀处理。
     */
    private static @Nullable String buildTemplate(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!value.startsWith("https://") && !value.startsWith("http://")) {
            return null;
        }
        if (!value.contains("{repository}") && !value.contains("{branch}") && !value.contains("{path}")) {
            // 允许直接写一个目录前缀，例如自建镜像 "https://example.com/themes/"
            value = (value.endsWith("/") ? value : value + "/") + "{path}";
        }
        if (!value.contains("{path}")) {
            return null;
        }
        return value;
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
            List<String> skipped,
            boolean cancelledInstall
    ) {
        public InstallReport {
            written = written == null ? List.of() : List.copyOf(written);
            skipped = skipped == null ? List.of() : List.copyOf(skipped);
        }

        static InstallReport failed(@Nullable String error) {
            return new InstallReport(false, error, null, List.of(), List.of(), false);
        }

        static InstallReport cancelledReport() {
            return new InstallReport(false, null, null, List.of(), List.of(), true);
        }
    }
}
