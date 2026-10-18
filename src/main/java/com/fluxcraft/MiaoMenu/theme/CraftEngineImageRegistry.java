package com.fluxcraft.MiaoMenu.theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 把主题贴图注册成 CraftEngine 的 {@code images} 条目。
 *
 * <p>CraftEngine 的 {@code <image:命名空间:路径>} 标签走字体渲染，可以在 Dialog
 * 的正文文本里显示大图（原生 Dialog 没有"背景图"概念，这是唯一的做法）。
 * 要让它生效，图片必须先写进 CE 的 {@code configuration/images.yml}。</p>
 *
 * <p>写入是**合并**而不是覆盖：服务器上已有的条目（例如手写的
 * {@code miaomenu:logo}）会被完整保留。</p>
 */
public final class CraftEngineImageRegistry {
    /** 主题图在 CE 里注册时使用的命名空间，与资源包命名空间一致。 */
    private static final String NAMESPACE = ThemeAssets.NAMESPACE;
    /** CE 字体 id：每张主题图会被分配到这个字体里的一个字符位。 */
    private static final String FONT = NAMESPACE + ":theme";
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9_.-]{1,64}");

    private final Path imagesFile;
    private final int height;
    private final int ascent;

    /**
     * @param imagesFile CE 的 {@code configuration/images.yml}
     * @param height     图片显示高度（像素）；Dialog 正文建议 160~260
     * @param ascent     基线偏移；必须 {@code <= height}，越小越向下延伸
     */
    public CraftEngineImageRegistry(Path imagesFile, int height, int ascent) {
        this.imagesFile = imagesFile;
        this.height = Math.max(1, height);
        this.ascent = Math.max(0, Math.min(this.height, ascent));
    }

    /**
     * 主题图在 CE 里的图片 ID，可直接写进 {@code <image:...>} 标签。
     *
     * <p>例如 {@code imageKey("halloween")} → {@code miaomenu:halloween}。</p>
     */
    public static String imageKey(String themeId) {
        String id = themeId == null ? "" : themeId.trim().toLowerCase(Locale.ROOT);
        return NAMESPACE + ":" + id;
    }

    /**
     * 为每个主题贴图补齐 images 条目。
     *
     * @param texturesRoot {@code assets/miaomenu/textures} 目录
     * @return 新增或更新的条目数
     */
    public int register(Path texturesRoot) throws IOException {
        if (imagesFile == null || texturesRoot == null || !Files.isDirectory(texturesRoot)) {
            return 0;
        }
        Set<String> ids = collectThemeIds(texturesRoot);
        if (ids.isEmpty()) {
            return 0;
        }
        YamlConfiguration config = Files.isRegularFile(imagesFile)
                ? YamlConfiguration.loadConfiguration(imagesFile.toFile())
                : new YamlConfiguration();

        int changed = 0;
        for (String id : ids) {
            String key = NAMESPACE + ":" + id;
            String file = NAMESPACE + ":item/menu/" + id + ".png";
            ConfigurationSection section = config.getConfigurationSection("images." + key);
            if (section != null
                    && height == section.getInt("height", -1)
                    && ascent == section.getInt("ascent", -1)
                    && file.equals(section.getString("file"))
                    && FONT.equals(section.getString("font"))) {
                continue;
            }
            config.set("images." + key + ".height", height);
            config.set("images." + key + ".ascent", ascent);
            config.set("images." + key + ".font", FONT);
            config.set("images." + key + ".file", file);
            changed++;
        }
        if (changed == 0) {
            return 0;
        }
        Path parent = imagesFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(imagesFile, config.saveToString(), StandardCharsets.UTF_8);
        return changed;
    }

    /** 收集 {@code textures/**}{@code /menu/<id>.png} 的 id。 */
    private static Set<String> collectThemeIds(Path texturesRoot) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.walk(texturesRoot)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".png")) {
                    continue;
                }
                Path parent = file.getParent();
                if (parent == null || !"menu".equals(parent.getFileName().toString())) {
                    continue;
                }
                String id = name.substring(0, name.length() - 4);
                if (SAFE_ID.matcher(id).matches()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }
}
