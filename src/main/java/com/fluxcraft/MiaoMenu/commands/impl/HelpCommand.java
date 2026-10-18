package com.fluxcraft.MiaoMenu.commands.impl;

import java.util.Map;

import org.bukkit.command.CommandSender;

import com.fluxcraft.MiaoMenu.commands.PluginCommand;
import com.fluxcraft.MiaoMenu.utils.Lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class HelpCommand implements PluginCommand {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();

    private final Map<String, String> commandDescriptions;
    private final Map<String, PluginCommand> commands;

    public HelpCommand(Map<String, String> commandDescriptions, Map<String, PluginCommand> commands) {
        this.commandDescriptions = commandDescriptions;
        this.commands = commands;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage(LEGACY_SERIALIZER.deserialize(Lang.get("message.help.header")));
        String alias = Lang.get("command.main-alias");
        for (Map.Entry<String, String> entry : commandDescriptions.entrySet()) {
            String cmdName = entry.getKey();
            PluginCommand command = commands.get(cmdName);
            if (command != null && !command.hasAccess(sender)) {
                continue;
            }
            Component descComponent = LEGACY_SERIALIZER.deserialize(entry.getValue());
            Component cmdComponent = Component.text(cmdName)
                    .color(NamedTextColor.YELLOW)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.suggestCommand("/" + alias + " " + cmdName))
                    .hoverEvent(HoverEvent.showText(descComponent));
            sender.sendMessage(
                    Component.empty()
                            .append(Component.text(" ☛ ").color(NamedTextColor.DARK_GRAY))
                            .append(cmdComponent)
                            .append(Component.text(" : ").color(NamedTextColor.GRAY))
                            .append(descComponent)
            );
        }
        sender.sendMessage(Component.empty());
        sender.sendMessage(LEGACY_SERIALIZER.deserialize(
                Lang.get("message.help.usage").replace("{0}", alias)));
    }

    @Override
    public boolean hasAccess(CommandSender sender) {
        return sender.hasPermission("dgeysermenu.use");
    }
}
