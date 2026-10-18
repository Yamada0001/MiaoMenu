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

/**
 * 为主题生成"整屏背景"的 Java 菜单。
 *
 * <p>原生 Dialog 没有背景图概念，真正能铺满背景的是 Java 菜单：6 行箱子共 54 格，
 * 用主题贴图填满每一格就是一张完整背景，再在少数格子上叠按钮。</p>
 *
 * <p>生成的文件名固定为 {@code <主题菜单名>-bg.yml}，只在该文件不存在时写入，
 * 避免覆盖服主自己改过的版本。</p>
 */
public final class JavaBackgroundMenuWriter {
    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9_.-]{1,64}");
    /** 6 行 = 54 格。 */
    private static final int SLOTS = 54;

    private JavaBackgroundMenuWriter() {
    }

    /**
     * 为给定主题生成背景菜单。
     *
     * @param javaMenusDir {@code java_menus/} 目录
     * @param themeId      主题 ID，同时用作 {@code image:<id>} 与菜单名
     * @return 是否新写入
     */
    public static boolean write(Path javaMenusDir, String themeId) throws IOException {
        if (javaMenusDir == null || themeId == null) {
            return false;
        }
        String id = themeId.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_NAME.matcher(id).matches()) {
            return false;
        }
        Path file = javaMenusDir.resolve("theme-" + id + "-bg.yml");
        if (Files.exists(file)) {
            return false;
        }
        Files.createDirectories(javaMenusDir);
        Files.writeString(file, menu(id), StandardCharsets.UTF_8);
        return true;
    }

    /** 扫描贴图目录，为每个主题各生成一份背景菜单。 */
    public static int writeAll(Path javaMenusDir, Path texturesRoot) throws IOException {
        if (texturesRoot == null || !Files.isDirectory(texturesRoot)) {
            return 0;
        }
        int written = 0;
        for (String id : collectIds(texturesRoot)) {
            if (write(javaMenusDir, id)) {
                written++;
            }
        }
        return written;
    }

    /**
     * 菜单内容：54 格全部铺主题图，中心放标题与两个按钮。
     *
     * <p>背景格用 {@code image:<id>}，与 Dialog 用的是同一张图。Java 菜单的
     * {@code items.<id>.slot} 只接受单个整数，所以背景要逐格展开。</p>
     */
    private static String menu(String id) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("""
                # 由 MiaoMenu 自动生成：6 行箱子铺满主题贴图作为背景。
                # 想改布局直接编辑本文件；插件不会覆盖已存在的同名菜单。
                menu_title: "&d&l主题背景 &7| &f%s"
                rows: 6

                items:
                """.formatted(id));

        // 背景：整屏 54 格逐格铺开（title/close/open_dialog 会覆盖对应格子）
        for (int slot = 0; slot < SLOTS; slot++) {
            yaml.append("  bg_").append(slot).append(":\n");
            yaml.append("    slot: ").append(slot).append('\n');
            yaml.append("    material: \"image:").append(id).append("\"\n");
            yaml.append("    display_name: \" \"\n");
        }

        // 标题与按钮
        yaml.append("""
                  title:
                    slot: 13
                    material: NETHER_STAR
                    display_name: "&d&l%s"
                    lore:
                      - "&7主题背景菜单"
                    left_click_commands:
                      - "[message] &7这是由主题贴图铺满的背景菜单。"

                  close:
                    slot: 49
                    material: BARRIER
                    display_name: "&c关闭"
                    left_click_commands:
                      - "[close]"

                  open_dialog:
                    slot: 40
                    material: BOOK
                    display_name: "&b打开对话框版本"
                    lore:
                      - "&7同一主题的原生 Dialog 菜单"
                    left_click_commands:
                      - "[menu] dialog:theme-%s"
                """.formatted(id, id));
        return yaml.toString();
    }

    private static Set<String> collectIds(Path texturesRoot) throws IOException {
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
                if (SAFE_NAME.matcher(id).matches()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }
}
