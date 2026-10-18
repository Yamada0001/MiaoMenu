package com.fluxcraft.MiaoMenu.dialogmenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.Body;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.Button;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.Button.Icon;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogFZ;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogFZ.Grid;
import com.fluxcraft.MiaoMenu.dialogmenu.plus.DialogFZ.GridStyle;
import com.fluxcraft.MiaoMenu.integration.ItemResolver;
import com.fluxcraft.MiaoMenu.menu.action.ActionRegistry;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementResult;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.object.ObjectContents;

public final class DialogMenu {
    private static final int GRID_SPACING = 2;
    private static final int ITEM_SPRITE_SIZE = 8;
    private static final int PLAIN_MESSAGE_PADDING = 4;
    private static final int FONT_LINE_HEIGHT = 9;

    private final MiaoMenu plugin;
    private final ItemResolver itemResolver;
    private final RequirementService requirementService;
    private final DialogFZ definition;
    private final DialogDTLB dynamicButtons;
    private final DialogInter interaction;

    public DialogMenu(
            DialogFZ definition,
            MiaoMenu plugin,
            ItemResolver itemResolver,
            ActionRegistry actionRegistry,
            RequirementService requirementService
    ) {
        this.plugin = plugin;
        this.itemResolver = itemResolver;
        this.requirementService = requirementService;
        this.definition = definition;
        this.dynamicButtons = new DialogDTLB(plugin);
        this.interaction = new DialogInter(plugin, actionRegistry, definition);
    }

    public void open(Player player) {
        player.showDialog(buildDialog(player));
    }

    public RequirementResult checkViewRequirement(Player player) {
        return requirementService.checkViewRequirement(
                player,
                definition.name(),
                definition.requirementBlocks(),
                definition.viewRequirements(),
                definition.denyMessage(),
                definition.fallbackMenu()
        );
    }

    private Dialog buildDialog(Player player) {
        List<Button> effectiveDefinitions = new ArrayList<>();
        effectiveDefinitions.addAll(definition.buttons());
        effectiveDefinitions.addAll(dynamicButtons.expand(player, definition.dynamicLists()));
        List<DialogBody> body = new ArrayList<>();
        for (Body entry : definition.bodies()) {
            body.add(entry.build(player, plugin, itemResolver));
        }
        if (isItemOverlay()) {
            body.add(buildItemOverlay(player, effectiveDefinitions, definition.grid()));
        }
        List<DialogInput> inputs = definition.inputs().stream()
                .map(entry -> entry.build(player, plugin))
                .toList();

        DialogBase.Builder baseBuilder = DialogBase.builder(component(player, definition.title()))
                .canCloseWithEscape(definition.canCloseWithEscape())
                .pause(definition.pause())
                .afterAction(definition.afterAction())
                .body(body)
                .inputs(inputs);
        if (definition.externalTitle() != null && !definition.externalTitle().isBlank()) {
            baseBuilder.externalTitle(component(player, definition.externalTitle()));
        }

        DialogBase base = baseBuilder.build();
        DialogType type = buildType(player, effectiveDefinitions);
        return Dialog.create(builder -> builder.empty().base(base).type(type));
    }

    private DialogType buildType(Player player, List<Button> effectiveDefinitions) {
        if (isItemOverlay()) {
            return DialogType.notice();
        }
        List<ActionButton> buttons = definition.grid() == null
                ? buildButtons(player, effectiveDefinitions)
                : buildGridButtons(player, effectiveDefinitions, definition.grid());
        ActionButton exitButton = definition.exitButton() == null
                ? null
                : buildButton(player, definition.exitButton(), false, null);

        return switch (definition.layoutType()) {
            case NOTICE -> buttons.isEmpty() ? DialogType.notice() : DialogType.notice(buttons.getFirst());
            case CONFIRMATION -> buildConfirmation(buttons);
            case MULTI_ACTION -> buttons.isEmpty()
                    ? (exitButton == null ? DialogType.notice() : DialogType.notice(exitButton))
                    : DialogType.multiAction(buttons, exitButton, definition.columns());
        };
    }

    /**
     * 确认框必须有且只有两个按钮。
     *
     * <p>配置期已经校验过数量，但按钮可能因为 {@code hide_when_locked} 在渲染期被隐藏，
     * 这时降级成单按钮提示框，保证对话框仍然能打开而不是抛越界异常。</p>
     */
    private DialogType buildConfirmation(List<ActionButton> buttons) {
        if (buttons.size() >= 2) {
            return DialogType.confirmation(buttons.get(0), buttons.get(1));
        }
        plugin.getLogger().warning("Dialog menu '" + definition.name()
                + "' uses layout confirmation but only " + buttons.size()
                + " button(s) are visible for this player; falling back to a notice dialog.");
        return buttons.isEmpty() ? DialogType.notice() : DialogType.notice(buttons.getFirst());
    }

    private boolean isItemOverlay() {
        return definition.grid() != null && definition.grid().style() == GridStyle.ITEM_OVERLAY;
    }

    private DialogBody buildItemOverlay(Player player, List<Button> definitions, Grid grid) {
        Button[] slots = new Button[grid.size()];
        int nextSlot = 0;
        for (Button button : definitions) {
            int slot;
            if (button.slot() != null) {
                slot = button.slot();
                if (slot < 0 || slot >= slots.length) {
                    plugin.getLogger().warning("Dialog menu '" + definition.name() + "' declares slot " + slot
                            + " outside the grid of " + slots.length + " cells; the button was skipped.");
                    continue;
                }
            } else {
                while (nextSlot < slots.length && slots[nextSlot] != null) {
                    nextSlot++;
                }
                if (nextSlot >= slots.length) {
                    break;
                }
                slot = nextSlot++;
            }
            slots[slot] = button;
        }

        int columns = definition.columns();
        int rows = (grid.size() + columns - 1) / columns;
        int outerWidth = columns * grid.buttonWidth() + (columns - 1) * GRID_SPACING;
        int innerWidth = Math.max(1, outerWidth - PLAIN_MESSAGE_PADDING * 2);
        int lastLine = overlayLine(grid, rows - 1);
        Component overlay = Component.empty();
        for (int line = 0; line <= lastLine; line++) {
            int row = overlayRowAtLine(grid, rows, line);
            overlay = overlay.append(row < 0
                    ? shift(player, innerWidth)
                    : buildOverlayRow(player, slots, row, columns, grid, innerWidth));
            if (line < lastLine) {
                overlay = overlay.append(Component.newline());
            }
        }
        return DialogBody.plainMessage(overlay, outerWidth);
    }

    private Component buildOverlayRow(
            Player player,
            Button[] slots,
            int row,
            int columns,
            Grid grid,
            int innerWidth
    ) {
        Component line = Component.empty();
        int advance = 0;
        int cellPitch = grid.buttonWidth() + GRID_SPACING;
        for (int column = 0; column < columns; column++) {
            int slot = row * columns + column;
            if (slot >= slots.length || slots[slot] == null) {
                continue;
            }
            Button button = slots[slot];
            RequirementResult renderedRequirement = button.evaluate(
                    player,
                    requirementService,
                    definition.name(),
                    definition.requirementBlocks()
            );
            boolean locked = !renderedRequirement.allowed();
            if (locked && button.hideWhenLocked()) {
                continue;
            }
            int target = column * cellPitch
                    + (grid.buttonWidth() - ITEM_SPRITE_SIZE) / 2
                    - PLAIN_MESSAGE_PADDING;
            if (target > advance) {
                line = line.append(shift(player, target - advance));
                advance = target;
            }
            Component sprite = overlaySprite(player, button, locked);
            line = line.append(sprite);
            advance += ITEM_SPRITE_SIZE;
        }
        if (advance < innerWidth) {
            line = line.append(shift(player, innerWidth - advance));
        }
        return line;
    }

    private Component overlaySprite(Player player, Button button, boolean locked) {
        Icon icon = button.icon();
        Component sprite = Component.object(ObjectContents.sprite(Key.key(icon.atlas()), Key.key(icon.sprite())));
        ClickEvent<?> click = interaction.createOverlayClick(player, button, locked);
        if (click != null) {
            sprite = sprite.clickEvent(click);
        }
        String hoverText = button.tooltip();
        if (hoverText == null || hoverText.isBlank()) {
            hoverText = locked
                    ? firstNonBlank(button.lockedLabel(), button.label())
                    : button.label();
        }
        return sprite.hoverEvent(HoverEvent.showText(component(player, hoverText)));
    }

    private int overlayRowAtLine(Grid grid, int rows, int line) {
        for (int row = 0; row < rows; row++) {
            if (overlayLine(grid, row) == line) {
                return row;
            }
        }
        return -1;
    }

    private int overlayLine(Grid grid, int row) {
        int pixels = grid.overlayOffsetY() + row * (grid.buttonWidth() + GRID_SPACING);
        return Math.round((float) pixels / FONT_LINE_HEIGHT);
    }

    private Component shift(Player player, int pixels) {
        return pixels == 0 ? Component.empty() : component(player, "<shift:" + pixels + ">");
    }

    private List<ActionButton> buildButtons(Player player, List<Button> definitions) {
        List<ActionButton> buttons = new ArrayList<>();
        for (Button button : definitions) {
            ActionButton built = buildButton(
                    player,
                    button,
                    definition.layoutType() == DialogFZ.LayoutType.MULTI_ACTION,
                    null
            );
            if (built != null) {
                buttons.add(built);
            }
        }
        return List.copyOf(buttons);
    }

    private List<ActionButton> buildGridButtons(
            Player player,
            List<Button> definitions,
            Grid grid
    ) {
        ActionButton filler = ActionButton.create(Component.empty(), null, grid.buttonWidth(), null);
        List<ActionButton> buttons = new ArrayList<>();
        buttons.addAll(Collections.nCopies(grid.size(), filler));
        boolean[] occupied = new boolean[grid.size()];
        int nextSlot = 0;
        for (Button button : definitions) {
            int slot;
            if (button.slot() != null) {
                slot = button.slot();
                if (slot < 0 || slot >= occupied.length) {
                    plugin.getLogger().warning("Dialog menu '" + definition.name() + "' declares slot " + slot
                            + " outside the grid of " + occupied.length + " cells; the button was skipped.");
                    continue;
                }
            } else {
                while (nextSlot < occupied.length && occupied[nextSlot]) {
                    nextSlot++;
                }
                if (nextSlot >= occupied.length) {
                    break;
                }
                slot = nextSlot++;
            }
            occupied[slot] = true;
            ActionButton built = buildButton(player, button, true, grid.buttonWidth());
            if (built != null) {
                buttons.set(slot, built);
            }
        }
        return List.copyOf(buttons);
    }

    private @Nullable ActionButton buildButton(
            Player player,
            Button button,
            boolean allowHidden,
            @Nullable Integer widthOverride
    ) {
        RequirementResult renderedRequirement = button.evaluate(
                player,
                requirementService,
                definition.name(),
                definition.requirementBlocks()
        );
        boolean locked = !renderedRequirement.allowed();
        if (locked && allowHidden && button.hideWhenLocked()) {
            return null;
        }

        String label = locked
                ? firstNonBlank(button.lockedLabel(),
                        com.fluxcraft.MiaoMenu.utils.Lang.get("message.item-locked-suffix").trim() + " " + button.label())
                : button.label();
        Component tooltip = button.tooltip() == null || button.tooltip().isBlank()
                ? null
                : component(player, button.tooltip());
        DialogAction action = interaction.createAction(player, button, locked);
        int width = widthOverride != null ? widthOverride : button.width();
        return ActionButton.create(
                buttonLabel(player, label, button.icon(), button.iconOnly()),
                tooltip,
                width,
                action
        );
    }

    private Component buttonLabel(Player player, String label, @Nullable Icon icon, boolean iconOnly) {
        Component text = component(player, label);
        if (icon == null) {
            return text;
        }
        Component sprite = Component.object(ObjectContents.sprite(Key.key(icon.atlas()), Key.key(icon.sprite())));
        if (iconOnly) {
            return sprite;
        }
        return sprite.append(Component.space()).append(text);
    }

    private Component component(Player player, String value) {
        return plugin.getMenuTextParser().parse(player, value);
    }

    private static String firstNonBlank(@Nullable String first, @Nullable String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null ? second : "";
    }

}
