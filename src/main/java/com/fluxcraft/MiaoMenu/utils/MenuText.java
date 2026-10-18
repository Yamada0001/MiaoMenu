package com.fluxcraft.MiaoMenu.utils;

import org.jspecify.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 可点击文本工具。
 *
 * <p>插件里不少提示都希望"点一下就执行"，但 {@code sendMessage(String)} 无法
 * 携带点击事件。这里统一把 {@code &} 颜色文本转成组件，再挂上
 * {@link ClickEvent#runCommand(String)}。</p>
 */
public final class MenuText {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private MenuText() {
    }

    /**
     * 生成一个可点击执行命令的文本组件。
     *
     * @param text      展示文本（支持 {@code &} 颜色）
     * @param command   点击后执行的命令，必须以 {@code /} 开头
     * @param hoverText 悬停提示（支持 {@code &} 颜色），可为 {@code null}
     */
    public static Component clickableCommand(String text, String command, @Nullable String hoverText) {
        Component component = LEGACY.deserialize(text == null ? "" : text)
                .clickEvent(ClickEvent.runCommand(command == null ? "" : command));
        if (hoverText != null && !hoverText.isBlank()) {
            component = component.hoverEvent(HoverEvent.showText(LEGACY.deserialize(hoverText)));
        }
        return component;
    }

    /** 生成一个可点击打开链接的文本组件。 */
    public static Component clickableUrl(String text, String url, @Nullable String hoverText) {
        Component component = LEGACY.deserialize(text == null ? "" : text)
                .clickEvent(ClickEvent.openUrl(url == null ? "" : url));
        if (hoverText != null && !hoverText.isBlank()) {
            component = component.hoverEvent(HoverEvent.showText(LEGACY.deserialize(hoverText)));
        }
        return component;
    }

    /** 生成一个可点击补全命令的文本组件（不直接执行，便于玩家确认）。 */
    public static Component suggestCommand(String text, String command, @Nullable String hoverText) {
        Component component = LEGACY.deserialize(text == null ? "" : text)
                .clickEvent(ClickEvent.suggestCommand(command == null ? "" : command));
        if (hoverText != null && !hoverText.isBlank()) {
            component = component.hoverEvent(HoverEvent.showText(LEGACY.deserialize(hoverText)));
        }
        return component;
    }
}
