package com.fluxcraft.MiaoMenu.theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 把主题贴图整理成"物品模型能真正采样到"的标准结构。
 *
 * <p>Minecraft 的物品模型只能从 {@code minecraft:atlases/blocks.json} 覆盖的目录
 * 取纹理，也就是 {@code textures/item/} 与 {@code textures/block/}。主题仓库把
 * 贴图放在 {@code textures/gui/sprites/menu/}（那是给 Dialog 的 sprite 图标用的
 * 自定义图集），直接给 {@code item_model} 引用会因为纹理不在图集里而渲染失败。</p>
 *
 * <p>这里在安装/启动时补一份标准副本：</p>
 * <ul>
 *   <li>{@code textures/item/menu/<id>.png} —— 复制自任意 {@code menu/<id>.png}</li>
 *   <li>{@code models/item/menu/<id>.json} —— 生成 {@code item/generated} 模型，
 *       layer0 指向上面那张图</li>
 * </ul>
 *
 * <p>原始 {@code gui/sprites/menu/} 保留不动，Dialog 按钮的 sprite 图标仍然依赖
 * 它和 {@code atlases/menu.json}。</p>
 */
public final class ThemeAssets {
    /** 物品模型的命名空间。 */
    public static final String NAMESPACE = "miaomenu";
    /** 物品模型路径前缀，最终模型 ID 形如 {@code miaomenu:item/menu/<id>}。 */
    public static final String MODEL_PATH_PREFIX = "item/menu/";
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9_.-]{1,64}");

    private ThemeAssets() {
    }

    /** 贴图所在的目录名；只有这个目录下的 png 会被当成主题图。 */
    private static final String MENU_DIR = "menu";

    /**
     * 扫描 {@code themes/} 下的主题贴图并补齐标准物品模型。
     *
     * @param themesRoot 数据目录里的 {@code themes/}
     * @return 新写入或更新的文件数
     */
    public static int ensure(Path themesRoot) throws IOException {
        if (themesRoot == null || !Files.isDirectory(themesRoot)) {
            return 0;
        }
        Path assets = themesRoot.resolve("assets").resolve(NAMESPACE);
        if (!Files.isDirectory(assets)) {
            return 0;
        }
        ensureSpriteAtlas(assets);

        Set<String> ids = collectIds(assets.resolve("textures"));
        if (ids.isEmpty()) {
            return 0;
        }
        Path textureTarget = assets.resolve("textures").resolve("item").resolve(MENU_DIR);
        Path modelTarget = assets.resolve("models").resolve("item").resolve(MENU_DIR);
        Files.createDirectories(textureTarget);
        Files.createDirectories(modelTarget);

        int written = 0;
        for (String id : ids) {
            Path source = findSource(assets.resolve("textures"), id);
            if (source == null) {
                continue;
            }
            Path texture = textureTarget.resolve(id + ".png");
            if (needsCopy(source, texture)) {
                Files.copy(source, texture, StandardCopyOption.REPLACE_EXISTING);
                written++;
            }
            Path model = modelTarget.resolve(id + ".json");
            String json = modelJson(id);
            if (needsWrite(model, json)) {
                Files.writeString(model, json, StandardCharsets.UTF_8);
                written++;
            }
        }
        return written;
    }

    /** 物品模型的完整 ID，例如 {@code miaomenu:item/menu/spring_sakura}。 */
    public static String modelId(String id) {
        return NAMESPACE + ":" + MODEL_PATH_PREFIX + id;
    }

    private static String modelJson(String id) {
        return """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "%s"
                  }
                }
                """.formatted(modelId(id));
    }

    /**
     * Dialog 按钮的 sprite 图标依赖这张图集；文件丢了就补回来。
     */
    private static void ensureSpriteAtlas(Path assets) throws IOException {
        Path atlas = assets.resolve("atlases").resolve("menu.json");
        if (Files.isRegularFile(atlas)) {
            return;
        }
        Files.createDirectories(atlas.getParent());
        Files.writeString(atlas, """
                {
                  "sources": [
                    {
                      "type": "directory",
                      "source": "gui/sprites/menu",
                      "prefix": "menu/"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
    }

    /** 收集所有 {@code .../menu/<id>.png} 的 id。 */
    private static Set<String> collectIds(Path texturesRoot) throws IOException {
        if (!Files.isDirectory(texturesRoot)) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.walk(texturesRoot)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
                    continue;
                }
                Path parent = file.getParent();
                if (parent == null || !MENU_DIR.equals(parent.getFileName().toString())) {
                    continue;
                }
                String name = file.getFileName().toString();
                String id = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
                if (SAFE_ID.matcher(id).matches()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static Path findSource(Path texturesRoot, String id) throws IOException {
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(texturesRoot)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.equals(id + ".png")) {
                    continue;
                }
                Path parent = file.getParent();
                if (parent == null || !MENU_DIR.equals(parent.getFileName().toString())) {
                    continue;
                }
                // 优先用 gui/sprites 下的原图，避免把已经生成好的 item/ 副本当来源。
                if (file.toString().replace('\\', '/').contains("/textures/item/")) {
                    continue;
                }
                candidates.add(file);
            }
        }
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    private static boolean needsCopy(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return true;
        }
        return Files.size(source) != Files.size(target) || !sameContent(source, target);
    }

    private static boolean sameContent(Path left, Path right) throws IOException {
        return java.util.Arrays.equals(Files.readAllBytes(left), Files.readAllBytes(right));
    }

    private static boolean needsWrite(Path target, String content) throws IOException {
        if (!Files.isRegularFile(target)) {
            return true;
        }
        return !content.equals(Files.readString(target, StandardCharsets.UTF_8));
    }
}
