package com.fluxcraft.MiaoMenu.dialogmenu.plus;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.integration.ItemResolver;

import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.ItemDialogBody;
import net.kyori.adventure.text.Component;

/**
 * 对话框正文中的一项（文本或物品展示）。
 *
 * <p>与 {@link Button} 一样，实例本身不保存玩家状态；所有文本都在
 * {@link #build(Player, MiaoMenu, ItemResolver)} 时按玩家解析。</p>
 */
public record Body(
        Kind kind,
        @Nullable String text,
        @Nullable String material,
        @Nullable String displayName,
        List<String> lore,
        @Nullable String description,
        boolean showDecorations,
        boolean showTooltip,
        int width,
        int height
) {
    /** 正文类型。 */
    public enum Kind {
        TEXT,
        ITEM
    }

    public static final String DEFAULT_MATERIAL = "STONE";

    public Body {
        lore = lore == null ? List.of() : List.copyOf(lore);
    }

    public DialogBody build(Player player, MiaoMenu plugin, ItemResolver itemResolver) {
        if (kind == Kind.ITEM) {
            return buildItem(player, plugin, itemResolver);
        }
        Component component = plugin.getMenuTextParser().parse(player, text == null ? "" : text);
        return width > 0
                ? DialogBody.plainMessage(component, width)
                : DialogBody.plainMessage(component);
    }

    private DialogBody buildItem(Player player, MiaoMenu plugin, ItemResolver itemResolver) {
        ItemStack stack = itemResolver.resolve(material == null || material.isBlank() ? DEFAULT_MATERIAL : material, 0);
        applyMeta(player, plugin, stack);
        ItemDialogBody.Builder builder = DialogBody.item(stack)
                .showDecorations(showDecorations)
                .showTooltip(showTooltip);
        if (width > 0) {
            builder.width(width);
        }
        if (height > 0) {
            builder.height(height);
        }
        if (description != null && !description.isBlank()) {
            builder.description(DialogBody.plainMessage(plugin.getMenuTextParser().parse(player, description)));
        }
        return builder.build();
    }

    private void applyMeta(Player player, MiaoMenu plugin, ItemStack stack) {
        if ((displayName == null || displayName.isBlank()) && lore.isEmpty()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        if (displayName != null && !displayName.isBlank()) {
            meta.displayName(plugin.getMenuTextParser().parse(player, displayName));
        }
        if (!lore.isEmpty()) {
            List<Component> lines = lore.stream()
                    .map(line -> plugin.getMenuTextParser().parse(player, line))
                    .toList();
            meta.lore(lines);
        }
        stack.setItemMeta(meta);
    }
}
