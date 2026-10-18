package com.fluxcraft.MiaoMenu.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jspecify.annotations.NonNull;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.utils.Lang;

public class ProxyManager implements PluginMessageListener {
    private static final String BUNGEECORD_CHANNEL = "BungeeCord";
    private static final String VELOCITY_CHANNEL = "bungeecord:main";
    private static final String PROXY_MODE_KEY = "settings.proxy-mode";
    private static final String LEGACY_VELOCITY_KEY = "settings.velocity-network";

    private final MiaoMenu plugin;
    private volatile ProxyType proxyType;

    public ProxyManager(MiaoMenu plugin) {
        this.plugin = plugin;
        this.proxyType = ProxyType.NONE;
    }

    public void initialize() {
        proxyType = configuredProxyType();
        if (proxyType == ProxyType.NONE) {
            plugin.getLogger().info(Lang.get("log.proxy.disabled"));
            return;
        }
        registerChannel(BUNGEECORD_CHANNEL);
        if (proxyType == ProxyType.VELOCITY) {
            // Velocity 只有在开启 bungee-plugin-message-channel 时才转发 legacy 通道，
            // 现代通道名是 bungeecord:main；两个都注册以提高兼容性。
            registerChannel(VELOCITY_CHANNEL);
            plugin.getLogger().info(Lang.get("log.proxy.velocity-forwarding-required"));
        }
        plugin.getLogger().info(Lang.get("log.proxy.detected").replace("{0}", proxyType.name()));
    }

    private void registerChannel(String channel) {
        try {
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, this);
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not register proxy channel " + channel, e);
        }
    }

    public void reload() {
        shutdown();
        initialize();
    }

    public void shutdown() {
        unregisterChannel(BUNGEECORD_CHANNEL);
        unregisterChannel(VELOCITY_CHANNEL);
        proxyType = ProxyType.NONE;
    }

    private void unregisterChannel(String channel) {
        if (plugin.getServer().getMessenger().isIncomingChannelRegistered(plugin, channel)) {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, channel);
        }
        if (plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, channel)) {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
        }
    }

    private ProxyType configuredProxyType() {
        String configuredMode = plugin.getConfig().getString(PROXY_MODE_KEY);
        if (plugin.getConfig().contains(LEGACY_VELOCITY_KEY)) {
            plugin.getLogger().warning(Lang.get("log.proxy.velocity-network-deprecated"));
        }
        if (configuredMode == null || configuredMode.isBlank()) {
            if (plugin.getConfig().contains(LEGACY_VELOCITY_KEY)) {
                return plugin.getConfig().getBoolean(LEGACY_VELOCITY_KEY, false)
                        ? ProxyType.VELOCITY
                        : ProxyType.NONE;
            }
            return ProxyType.NONE;
        }
        ProxyType configuredType = ProxyType.fromConfigValue(configuredMode);
        if (configuredType == ProxyType.NONE && !"NONE".equalsIgnoreCase(configuredMode.trim())) {
            plugin.getLogger().warning(Lang.get("log.proxy.unknown-mode").replace("{0}", configuredMode));
        }
        return configuredType;
    }

    /** 配置层面是否启用了代理集成（不代表代理真的在线）。 */
    public boolean isProxyConnected() {
        return proxyType != ProxyType.NONE;
    }

    /**
     * 把玩家送到目标服务器。
     *
     * @return 消息是否已成功投递给代理（代理端的接受与否无法同步确认）
     */
    public boolean sendServerCommand(Player player, String serverName) {
        if (proxyType == ProxyType.NONE) {
            return false;
        }
        String channel = proxyType == ProxyType.VELOCITY ? VELOCITY_CHANNEL : BUNGEECORD_CHANNEL;
        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, channel)) {
            channel = BUNGEECORD_CHANNEL;
        }
        try {
            sendServerCommand(player, channel, serverName);
            return true;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    Lang.get("log.proxy.send-failed").replace("{0}", serverName), e);
            return false;
        }
    }

    private void sendServerCommand(Player player, String channel, String serverName) {
        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, channel)) {
            throw new IllegalStateException(
                    Lang.get("log.proxy.channel-not-registered").replace("{0}", channel));
        }
        player.sendPluginMessage(plugin, channel, createBungeeCordServerMessage(serverName));
    }

    private byte[] createBungeeCordServerMessage(String serverName) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             DataOutputStream dataOut = new DataOutputStream(byteStream)) {
            dataOut.writeUTF("Connect");
            dataOut.writeUTF(serverName);
            return byteStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(
                    Lang.get("log.proxy.message-create-failed").replace("{0}", serverName), e);
        }
    }

    @Override
    public void onPluginMessageReceived(@NonNull String channel, @NonNull Player player, byte @NonNull [] message) {
        if (BUNGEECORD_CHANNEL.equals(channel) || VELOCITY_CHANNEL.equals(channel)) {
            handleProxyResponse(message);
        }
    }

    private void handleProxyResponse(byte[] message) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(message);
             DataInputStream dataIn = new DataInputStream(byteStream)) {
            String subchannel = dataIn.readUTF();
            plugin.getLogger().fine(Lang.get("log.proxy.message-received").replace("{0}", subchannel));
        } catch (IOException e) {
            plugin.getLogger().log(Level.FINE, Lang.get("log.proxy.response-handle-failed"), e);
        }
    }

    public enum ProxyType {
        NONE,
        BUNGEECORD,
        VELOCITY;

        static ProxyType fromConfigValue(String value) {
            if (value == null) {
                return NONE;
            }
            return switch (value.trim().toUpperCase(Locale.ROOT)) {
                case "BUNGEE", "BUNGEECORD" -> BUNGEECORD;
                case "VELOCITY" -> VELOCITY;
                default -> NONE;
            };
        }
    }
}
