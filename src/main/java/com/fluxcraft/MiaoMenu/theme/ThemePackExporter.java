package com.fluxcraft.MiaoMenu.theme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;

/**
 * 把主题贴图导出到已有资源包插件的资源目录。
 *
 * <p>服务器装了 CraftEngine / ItemsAdder / Oraxen 时，没必要再让服主自己托管
 * 一个 zip：这些插件都会把自己的 {@code assets/} 合并成资源包下发给客户端。
 * 因此只要把主题的 {@code assets/miaomenu/**} 复制进它们的资源目录，
 * {@code image:<id>} 依赖的 {@code item_model} 就能正常渲染。</p>
 *
 * <p>这里只复制**纯资源**（模型 / 贴图 / 图集），不生成各插件的物品定义——
 * 那些格式随版本变化很大，而 {@code item_model} 是原版机制，跨版本稳定。</p>
 */
public final class ThemePackExporter {
    /** 支持的资源包插件。 */
    public enum Provider {
        CRAFTENGINE("CraftEngine", "CraftEngine/resources/miaomenu/resourcepack/assets"),
        ITEMSADDER("ItemsAdder", "ItemsAdder/data/resource_pack/assets"),
        ORAXEN("Oraxen", "Oraxen/pack/assets"),
        NONE("", "");

        private final String pluginName;
        private final String defaultPath;

        Provider(String pluginName, String defaultPath) {
            this.pluginName = pluginName;
            this.defaultPath = defaultPath;
        }

        public String pluginName() {
            return pluginName;
        }

        public String defaultPath() {
            return defaultPath;
        }
    }

    /**
     * 一次导出的结果。
     *
     * @param provider 实际使用的插件；{@code NONE} 表示没有可用插件
     * @param target   资源写入目录；未导出时为 {@code null}
     * @param files    复制的文件数
     * @param error    失败原因；成功时为 {@code null}
     */
    public record ExportResult(
            Provider provider,
            @Nullable Path target,
            int files,
            @Nullable String error
    ) {
        public boolean exported() {
            return provider != Provider.NONE && error == null && files > 0;
        }

        static ExportResult none() {
            return new ExportResult(Provider.NONE, null, 0, null);
        }

        static ExportResult failed(Provider provider, @Nullable Path target, String error) {
            return new ExportResult(provider, target, 0, error);
        }
    }

    private final MiaoMenu plugin;
    private final @Nullable Path pluginsFolder;
    private final @Nullable Provider forcedProvider;

    public ThemePackExporter(MiaoMenu plugin) {
        this.plugin = plugin;
        this.pluginsFolder = resolvePluginsFolder(plugin);
        this.forcedProvider = null;
    }

    /** 测试用：指定插件目录与提供方，避免依赖真实服务端。 */
    ThemePackExporter(@Nullable Path pluginsFolder, @Nullable Provider forcedProvider) {
        this.plugin = null;
        this.pluginsFolder = pluginsFolder;
        this.forcedProvider = forcedProvider;
    }

    /**
     * 检测当前生效的资源包插件。
     *
     * <p>{@code settings.theme-pack.provider} 可强制指定，默认 {@code auto}
     * 按 CraftEngine → ItemsAdder → Oraxen 顺序取第一个已启用的。</p>
     */
    public Provider detectProvider() {
        if (forcedProvider != null) {
            return forcedProvider;
        }
        Provider configured = configuredProvider();
        if (configured != null) {
            return configured;
        }
        PluginManager pluginManager = pluginManager();
        if (pluginManager == null) {
            return Provider.NONE;
        }
        for (Provider provider : List.of(Provider.CRAFTENGINE, Provider.ITEMSADDER, Provider.ORAXEN)) {
            Plugin candidate = pluginManager.getPlugin(provider.pluginName());
            if (candidate != null && candidate.isEnabled()) {
                return provider;
            }
        }
        return Provider.NONE;
    }

    /** 是否已经有插件在提供主题贴图（供 {@code image:} 判断要不要显示占位）。 */
    public boolean hasExportedPack() {
        Provider provider = detectProvider();
        if (provider == Provider.NONE) {
            return false;
        }
        Path target = targetFor(provider);
        return target != null && Files.isDirectory(target.resolve("miaomenu"));
    }

    /**
     * 把主题资源目录导出到资源包插件。
     *
     * @param themeAssets 主题的 {@code themes/assets} 目录
     */
    public ExportResult export(@Nullable Path themeAssets) {
        Provider provider = detectProvider();
        if (provider == Provider.NONE) {
            return ExportResult.none();
        }
        Path target = targetFor(provider);
        if (target == null) {
            return ExportResult.failed(provider, null, "plugins folder is unavailable");
        }
        if (themeAssets == null || !Files.isDirectory(themeAssets)) {
            return ExportResult.failed(provider, target, "theme assets are missing");
        }
        try {
            int copied = copyTree(themeAssets, target);
            // CraftEngine 只把带 pack.yml 的目录当成"包"；没有它，包内的
            // configuration/images.yml 不会被加载，<image:...> 也就渲染不出来。
            if (provider == Provider.CRAFTENGINE && ensureCraftEnginePackManifest(target)) {
                copied++;
            }
            return new ExportResult(provider, target, copied, null);
        } catch (IOException e) {
            return ExportResult.failed(provider, target, e.getMessage());
        }
    }

    /**
     * 补齐 CraftEngine 的 {@code pack.yml}（已存在则不动）。
     *
     * @return 是否新写入
     */
    private static boolean ensureCraftEnginePackManifest(Path resourcepackAssets) throws IOException {
        // target 是 .../resources/miaomenu/resourcepack/assets，包根在往上两层
        Path packRoot = resourcepackAssets.getParent() == null
                ? null
                : resourcepackAssets.getParent().getParent();
        if (packRoot == null) {
            return false;
        }
        Path manifest = packRoot.resolve("pack.yml");
        if (Files.isRegularFile(manifest)) {
            return false;
        }
        Files.createDirectories(packRoot);
        Files.writeString(manifest, """
                author: MiaoMenu
                version: 1.0
                description: MiaoMenu theme images
                namespace: miaomenu
                """, java.nio.charset.StandardCharsets.UTF_8);
        return true;
    }

    /** 导出后让资源包插件重新生成包（命令可配置，留空则只提示）。 */
    public void reloadProvider(Provider provider) {
        if (plugin == null || provider == Provider.NONE) {
            return;
        }
        String command = plugin.getConfig().getString("settings.theme-pack.reload-command", "");
        if (command == null || command.isBlank()) {
            plugin.getLogger().info("Theme assets exported for " + provider.pluginName()
                    + "; reload that plugin to rebuild its resource pack.");
            return;
        }
        try {
            org.bukkit.Bukkit.getGlobalRegionScheduler().run(plugin, _ ->
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), command.trim()));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not run theme-pack reload command '" + command + "': " + e);
        }
    }

    private @Nullable Provider configuredProvider() {
        if (plugin == null) {
            return null;
        }
        String configured = plugin.getConfig().getString("settings.theme-pack.provider", "auto");
        if (configured == null || configured.isBlank() || "auto".equalsIgnoreCase(configured)) {
            return null;
        }
        return switch (configured.trim().toLowerCase(Locale.ROOT)) {
            case "craftengine", "ce" -> Provider.CRAFTENGINE;
            case "itemsadder", "ia" -> Provider.ITEMSADDER;
            case "oraxen" -> Provider.ORAXEN;
            case "none", "off", "disabled" -> Provider.NONE;
            default -> null;
        };
    }

    private @Nullable Path targetFor(Provider provider) {
        if (pluginsFolder == null) {
            return null;
        }
        String configuredPath = plugin == null ? "" : plugin.getConfig()
                .getString("settings.theme-pack." + provider.name().toLowerCase(Locale.ROOT) + "-path", "");
        String relative = configuredPath == null || configuredPath.isBlank()
                ? provider.defaultPath()
                : configuredPath;
        return pluginsFolder.resolve(relative).normalize();
    }

    private @Nullable PluginManager pluginManager() {
        try {
            return plugin == null || plugin.getServer() == null
                    ? null
                    : plugin.getServer().getPluginManager();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable Path resolvePluginsFolder(MiaoMenu plugin) {
        try {
            return plugin.getServer() == null ? null : plugin.getServer().getPluginsFolder().toPath();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 递归复制，保留相对结构；同名文件覆盖（主题更新需要覆盖旧贴图）。 */
    private static int copyTree(Path source, Path target) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(source)) {
            stream.filter(Files::isRegularFile).forEach(sources::add);
        }
        int copied = 0;
        for (Path file : sources) {
            Path relative = source.relativize(file);
            Path destination = target.resolve(relative).normalize();
            if (!destination.startsWith(target)) {
                throw new IOException("Refusing to write outside the pack directory: " + relative);
            }
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
            copied++;
        }
        return copied;
    }
}
