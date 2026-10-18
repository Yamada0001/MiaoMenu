package com.fluxcraft.MiaoMenu.update;

import org.jspecify.annotations.Nullable;

/**
 * 一次更新检查的结果。
 *
 * @param currentVersion 本服务器正在运行的版本
 * @param latestVersion  远端最新版本；{@code null} 表示仓库里还没有可用版本
 * @param releaseName    远端版本的展示名（可能是 {@code null}）
 * @param releaseUrl     远端版本的页面地址（可能是 {@code null}）
 * @param updateAvailable 远端版本是否比本服务器更新
 */
public record UpdateInfo(
        String currentVersion,
        @Nullable String latestVersion,
        @Nullable String releaseName,
        @Nullable String releaseUrl,
        boolean updateAvailable
) {
    public UpdateInfo {
        currentVersion = currentVersion == null ? "" : currentVersion;
    }

    /** 仓库里没有任何可用版本时的结果。 */
    public static UpdateInfo noRelease(String currentVersion) {
        return new UpdateInfo(currentVersion, null, null, null, false);
    }

    /** 用于消息展示的版本名：优先使用 Release 名称，其次使用标签。 */
    public String displayVersion() {
        if (releaseName != null && !releaseName.isBlank()) {
            return releaseName;
        }
        return latestVersion == null ? "?" : latestVersion;
    }
}
