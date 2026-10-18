package com.fluxcraft.MiaoMenu.theme;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * {@code MiaoMenu-theme} 仓库根目录 {@code themes.json} 的解析结果。
 *
 * <p>清单结构（schema 1）：</p>
 * <pre>
 * {
 *   "schema": 1,
 *   "themes": [
 *     {
 *       "id": "spring_sakura",
 *       "name": "春日樱花",
 *       "description": "樱花飘落的春季主题",
 *       "version": "1.0.0",
 *       "menus": { "java": "theme-spring", "dialog": "theme-spring" },
 *       "files": [
 *         { "source": "themes/spring_sakura/java_menus/theme-spring.yml",
 *           "target": "java_menus/theme-spring.yml",
 *           "sha256": "可选，用于校验下载结果" }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 */
public record ThemeManifest(int schema, List<Theme> themes) {
    public static final int SUPPORTED_SCHEMA = 1;

    public ThemeManifest {
        themes = themes == null ? List.of() : List.copyOf(themes);
    }

    /** 按 ID 查找主题（大小写不敏感）。 */
    public @Nullable Theme find(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (Theme theme : themes) {
            if (theme.id().equals(key)) {
                return theme;
            }
        }
        return null;
    }

    /**
     * 解析清单；结构不合法时返回 {@code null}，由调用方给出可读错误。
     */
    public static @Nullable ThemeManifest parse(JsonElement root) {
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        int schema = object.has("schema") && object.get("schema").isJsonPrimitive()
                ? object.get("schema").getAsInt()
                : SUPPORTED_SCHEMA;
        if (schema > SUPPORTED_SCHEMA) {
            return null;
        }
        JsonElement themesElement = object.get("themes");
        if (themesElement == null || !themesElement.isJsonArray()) {
            return null;
        }
        JsonArray array = themesElement.getAsJsonArray();
        List<Theme> themes = new java.util.ArrayList<>();
        for (JsonElement element : array) {
            Theme theme = Theme.parse(element);
            if (theme != null) {
                themes.add(theme);
            }
        }
        return new ThemeManifest(schema, themes);
    }

    /**
     * 一个可选主题。
     *
     * @param id          主题 ID（小写，用作目录名与 {@code image:} 键）
     * @param name        展示名
     * @param description 一句话描述
     * @param version     主题版本
     * @param javaMenu    该主题提供的 Java 菜单名（可能为 {@code null}）
     * @param dialogMenu  该主题提供的 Dialog 菜单名（可能为 {@code null}）
     * @param files       需要下载的文件
     */
    public record Theme(
            String id,
            String name,
            String description,
            String version,
            @Nullable String javaMenu,
            @Nullable String dialogMenu,
            List<FileEntry> files
    ) {
        public Theme {
            files = files == null ? List.of() : List.copyOf(files);
        }

        /** 用于消息展示的名字：优先展示中文名。 */
        public String displayName() {
            return name == null || name.isBlank() ? id : name;
        }

        static @Nullable Theme parse(JsonElement element) {
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id");
            if (id == null || !isSafeId(id)) {
                return null;
            }
            String name = string(object, "name");
            String description = string(object, "description");
            String version = string(object, "version");
            String javaMenu = null;
            String dialogMenu = null;
            JsonElement menus = object.get("menus");
            if (menus != null && menus.isJsonObject()) {
                javaMenu = string(menus.getAsJsonObject(), "java");
                dialogMenu = string(menus.getAsJsonObject(), "dialog");
            }
            List<FileEntry> files = new java.util.ArrayList<>();
            JsonElement filesElement = object.get("files");
            if (filesElement != null && filesElement.isJsonArray()) {
                for (JsonElement fileElement : filesElement.getAsJsonArray()) {
                    FileEntry entry = FileEntry.parse(fileElement);
                    if (entry != null) {
                        files.add(entry);
                    }
                }
            }
            if (files.isEmpty()) {
                return null;
            }
            return new Theme(
                    id.toLowerCase(Locale.ROOT),
                    name,
                    description,
                    version,
                    javaMenu,
                    dialogMenu,
                    files
            );
        }
    }

    /**
     * 单个下载项。
     *
     * @param source 仓库内相对路径
     * @param target 安装到插件数据目录的相对路径
     * @param sha256 可选的完整性校验值
     */
    public record FileEntry(String source, String target, @Nullable String sha256) {
        static @Nullable FileEntry parse(JsonElement element) {
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            String source = string(object, "source");
            String target = string(object, "target");
            if (!isSafeRelativePath(source) || !isSafeRelativePath(target) || !isAllowedTarget(target)) {
                return null;
            }
            return new FileEntry(source, target, string(object, "sha256"));
        }
    }

    /** 主题 ID 只允许小写字母、数字、下划线与短横线。 */
    private static boolean isSafeId(String id) {
        return id.matches("[a-z0-9_-]{1,64}");
    }

    /**
     * 拒绝绝对路径与 {@code ..} 片段，避免清单把文件写到数据目录之外。
     */
    static boolean isSafeRelativePath(@Nullable String path) {
        if (path == null || path.isBlank() || path.length() > 256) {
            return false;
        }
        if (path.startsWith("/") || path.startsWith("\\") || path.contains(":")) {
            return false;
        }
        for (String segment : path.split("[/\\\\]")) {
            if (segment.isEmpty() || segment.equals("..") || segment.equals(".")) {
                return false;
            }
        }
        return true;
    }

    /** 只允许写入这些目录，防止主题包覆盖配置或插件本体。 */
    static boolean isAllowedTarget(String target) {
        return target.startsWith("java_menus/")
                || target.startsWith("dialog_menus/")
                || target.startsWith("bedrock_menus/")
                || target.startsWith("themes/")
                || target.startsWith("lang/");
    }

    private static @Nullable String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
