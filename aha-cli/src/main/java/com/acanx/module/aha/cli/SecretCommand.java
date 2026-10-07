package com.acanx.module.aha.cli;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.exception.Exceptions;
import com.acanx.module.aha.core.security.EncryptedFileSecretStore;
import com.acanx.module.aha.core.security.SecretStore;
import com.acanx.module.aha.core.security.SecretStores;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.Console;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 密钥库管理命令。
 *
 * <p>配合 {@code Security.KeyStore} 使用：占位符（如 {@code ${AHA_API_KEY_DEEPSEEK}}）
 * 未命中环境变量时，会回退到此处写入的加密密钥库。</p>
 *
 * @since 0.1.0
 */
@Command(
        name = "secret",
        description = "密钥库管理（加密存储 API Key 等凭据）",
        mixinStandardHelpOptions = true,
        subcommands = {
                SecretCommand.SetSub.class,
                SecretCommand.GetSub.class,
                SecretCommand.DeleteSub.class,
                SecretCommand.ListSub.class})
public final class SecretCommand implements Runnable {

    /** 主密码环境变量名。 */
    static final String MASTER_PASSWORD_ENV = EncryptedFileSecretStore.MASTER_PASSWORD_ENV;

    @Override
    public void run() {
        System.out.println("用法: aha secret [set|get|delete|list]");
    }

    /**
     * 解析密钥库；不可用时给出可操作的提示。
     *
     * @return 密钥库
     */
    static SecretStore requireStore() {
        SecretStore store = SecretStores.create(CliContext.config());
        if (store == null) {
            throw new AhaException("KEYSTORE_UNAVAILABLE",
                    "密钥库不可用。请设置主密码环境变量后重试：" + MASTER_PASSWORD_ENV);
        }
        return store;
    }

    /**
     * 写入密钥。
     *
     * @since 0.1.0
     */
    @Command(name = "set", description = "写入密钥", mixinStandardHelpOptions = true)
    public static final class SetSub implements Callable<Integer> {

        @Parameters(index = "0", description = "键名，通常与占位符同名，如 AHA_API_KEY_DEEPSEEK")
        String name;

        @Option(names = {"-v", "--value"},
                description = "密钥值；不提供则从终端读取（不回显）")
        String value;

        @Override
        public Integer call() {
            char[] secret = resolveSecret();
            if (secret == null) {
                return 1;
            }
            try {
                requireStore().store(name, secret);
                System.out.println("已写入密钥: " + name);
                System.out.println("模型配置中可使用占位符引用：" + "${" + name + "}");
                return 0;
            } catch (AhaException e) {
                System.err.println("[error] " + Exceptions.message(e));
                return 1;
            } finally {
                java.util.Arrays.fill(secret, '\0');
            }
        }

        /**
         * 取得密钥值。
         *
         * @return 密钥；无法获取时返回 {@code null}
         */
        private char[] resolveSecret() {
            if (value != null && !value.isEmpty()) {
                return value.toCharArray();
            }
            Console console = System.console();
            if (console == null) {
                System.err.println("[error] 非交互环境请使用 --value 提供密钥");
                return null;
            }
            char[] first = console.readPassword("密钥值（不回显）: ");
            if (first == null || first.length == 0) {
                System.err.println("[error] 密钥值不能为空");
                return null;
            }
            char[] again = console.readPassword("再次输入确认: ");
            if (again == null || !java.util.Arrays.equals(first, again)) {
                System.err.println("[error] 两次输入不一致");
                java.util.Arrays.fill(first, '\0');
                return null;
            }
            java.util.Arrays.fill(again, '\0');
            return first;
        }
    }

    /**
     * 读取密钥。
     *
     * @since 0.1.0
     */
    @Command(name = "get", description = "读取密钥", mixinStandardHelpOptions = true)
    public static final class GetSub implements Callable<Integer> {

        @Parameters(index = "0", description = "键名")
        String name;

        @Option(names = "--mask", description = "仅显示首尾各 4 位，避免密钥出现在终端历史中")
        boolean mask;

        @Override
        public Integer call() {
            try {
                char[] secret = requireStore().retrieve(name);
                if (secret == null) {
                    System.err.println("[error] 密钥不存在: " + name);
                    return 1;
                }
                System.out.println(mask ? mask(secret) : new String(secret));
                java.util.Arrays.fill(secret, '\0');
                return 0;
            } catch (AhaException e) {
                System.err.println("[error] " + Exceptions.message(e));
                return 1;
            }
        }

        /**
         * 遮蔽密钥中间部分。
         *
         * @param secret 密钥
         * @return 遮蔽后的文本
         */
        private static String mask(char[] secret) {
            String text = new String(secret);
            if (text.length() <= 8) {
                return "****";
            }
            return text.substring(0, 4) + "****" + text.substring(text.length() - 4);
        }
    }

    /**
     * 删除密钥。
     *
     * @since 0.1.0
     */
    @Command(name = "delete", description = "删除密钥", mixinStandardHelpOptions = true)
    public static final class DeleteSub implements Callable<Integer> {

        @Parameters(index = "0", description = "键名")
        String name;

        @Override
        public Integer call() {
            try {
                requireStore().delete(name);
                System.out.println("已删除密钥: " + name);
                return 0;
            } catch (AhaException e) {
                System.err.println("[error] " + Exceptions.message(e));
                return 1;
            }
        }
    }

    /**
     * 列出密钥名。
     *
     * @since 0.1.0
     */
    @Command(name = "list", description = "列出密钥名（不含值）", mixinStandardHelpOptions = true)
    public static final class ListSub implements Callable<Integer> {

        @Override
        public Integer call() {
            try {
                SecretStore store = requireStore();
                if (!(store instanceof EncryptedFileSecretStore encrypted)) {
                    System.err.println("[error] 当前密钥库实现不支持列举");
                    return 1;
                }
                List<String> names = encrypted.listKeys();
                if (names.isEmpty()) {
                    System.out.println("密钥库为空: " + encrypted.keyStorePath());
                    return 0;
                }
                System.out.println("密钥库: " + encrypted.keyStorePath());
                names.forEach(name -> System.out.println("  " + name));
                return 0;
            } catch (AhaException e) {
                System.err.println("[error] " + Exceptions.message(e));
                return 1;
            }
        }
    }
}
