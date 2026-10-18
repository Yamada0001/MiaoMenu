package com.fluxcraft.MiaoMenu.security;

/**
 * 菜单名与命令内容的基础校验。
 *
 * <p>这里的检查目标是"阻止明显破坏性的输入"（控制字符、换行、超长、
 * 路径分隔符），而不是靠字符白名单去猜命令语义——真正的命令注入面
 * 在于玩家可控数据被拼进 {@code [cmd]} 模板，那需要配置侧避免，
 * 无法由字符集白名单解决。</p>
 */
public final class InputValidator {
    private static final int DEFAULT_MAX_LENGTH = 64;
    private static final int COMMAND_MAX_LENGTH = 512;
    private static final String FORBIDDEN_NAME_CHARS = "/\\:*?\"<>|";

    private InputValidator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 菜单名：允许中文与空格，但拒绝路径分隔符、控制字符与过长输入。 */
    public static boolean isSafeMenuName(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > DEFAULT_MAX_LENGTH) {
            return false;
        }
        if (trimmed.startsWith(".") || trimmed.contains("..")) {
            return false;
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (current < 0x20 || current == 0x7f) {
                return false;
            }
            if (FORBIDDEN_NAME_CHARS.indexOf(current) >= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 命令内容校验。
     *
     * <p>空内容视为"无参数动作"（例如 {@code [close]}），是合法的。</p>
     */
    public static boolean isSafeCommandContent(String value) {
        if (value == null) {
            return false;
        }
        if (value.isBlank()) {
            return true;
        }
        if (value.length() > COMMAND_MAX_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\n' || current == '\r' || current == '\t') {
                return false;
            }
            if (current < 0x20 || current == 0x7f) {
                return false;
            }
        }
        return !value.contains(";;") && !value.contains("&&") && !value.contains("||");
    }

    /** 生成可安全写入日志的内容摘要（去控制字符 + 截断）。 */
    public static String describeUnsafe(String value) {
        if (value == null) {
            return "<null>";
        }
        StringBuilder sanitized = new StringBuilder(Math.min(value.length(), 64));
        for (int index = 0; index < value.length() && sanitized.length() < 64; index++) {
            char current = value.charAt(index);
            sanitized.append(current < 0x20 || current == 0x7f ? '?' : current);
        }
        if (value.length() > 64) {
            sanitized.append("...");
        }
        return sanitized.toString();
    }
}
