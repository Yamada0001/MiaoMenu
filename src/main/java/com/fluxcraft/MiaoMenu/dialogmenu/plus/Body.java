package com.fluxcraft.MiaoMenu.dialogmenu.plus;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.integration.ItemResolver;
import com.fluxcraft.MiaoMenu.theme.CraftEngineImageRegistry;

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
    /** 主题图用 CE 标签渲染时的默认宽度（像素）；Dialog 正文可用宽度约 320。 */
    private static final int DEFAULT_IMAGE_WIDTH = 320;

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
        // CE 能拦截 Dialog 数据包，把 <image:...> 渲染成大图；
        // 主题图本来就是整屏尺寸，用标签比 32×32 的物品图标更接近"背景"。
        String themeId = themeImageId(material);
        if (shouldUseCraftEngineImage(material, plugin.useCraftEngineDialogImage())) {
            return buildCraftEngineImage(player, plugin, themeId);
        }
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

    /**
     * 用 CraftEngine 的 {@code <image:命名空间:路径>} 标签渲染主题图。
     *
     * <p>文本 body 只有宽度参数，图片高度由 CE 的 {@code images.yml} 里
     * {@code height} 决定（见 {@code settings.theme-pack.craftengine-image-height}）。</p>
     */
    private DialogBody buildCraftEngineImage(Player player, MiaoMenu plugin, String themeId) {
        Component component = plugin.getMenuTextParser().parse(player, craftEngineImageTag(themeId));
        int imageWidth = width > 0 ? width : DEFAULT_IMAGE_WIDTH;
        return DialogBody.plainMessage(component, imageWidth);
    }

    /**
     * 若 {@code material} 是 {@code image:<id>}，返回其中的 id；否则返回 {@code null}。
     */
    static @Nullable String themeImageId(@Nullable String material) {
        if (material == null) {
            return null;
        }
        String value = material.trim().toLowerCase(java.util.Locale.ROOT);
        if (!value.startsWith("image:")) {
            return null;
        }
        String id = value.substring("image:".length()).trim();
        return id.isEmpty() ? null : id;
    }

    /** 本 body 是否应该用 CE 的 {@code <image:...>} 标签渲染。 */
    static boolean shouldUseCraftEngineImage(@Nullable String material, boolean craftEngineDialogImage) {
        return craftEngineDialogImage && themeImageId(material) != null;
    }

    /** 构造 CE 图片标签，例如 {@code <image:miaomenu:halloween>}。 */
    static String craftEngineImageTag(String themeId) {
        return "<image:" + CraftEngineImageRegistry.imageKey(themeId) + ">";
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
