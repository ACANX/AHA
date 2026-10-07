package com.acanx.module.aha.cli;

import com.acanx.module.aha.core.security.EncryptedFileSecretStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SecretCommand} 测试。
 *
 * <p>主密码通过系统属性注入（{@link EncryptedFileSecretStore#MASTER_PASSWORD_ENV}），
 * 与生产的环境变量优先级一致。</p>
 *
 * @since 0.1.0
 */
class SecretCommandTest {

    @TempDir
    Path home;

    private CommandLine cmd;
    private ByteArrayOutputStream stdout;
    private ByteArrayOutputStream stderr;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private String originalHome;

    @BeforeEach
    void setUp() throws Exception {
        originalOut = System.out;
        originalErr = System.err;
        originalHome = System.getProperty("AHA_HOME");
        stdout = new ByteArrayOutputStream();
        stderr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));

        // 密钥库落在临时目录，避免污染真实用户目录
        System.setProperty("AHA_HOME", home.toString());
        Files.createDirectories(home.resolve("Key"));
        writeAhaYaml();

        cmd = new CommandLine(new AhaCli());
        cmd.setOut(new PrintWriter(new OutputStreamWriter(stdout, StandardCharsets.UTF_8), true));
        cmd.setErr(new PrintWriter(new OutputStreamWriter(stderr, StandardCharsets.UTF_8), true));
        CliContext.invalidate();
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        if (originalHome == null) {
            System.clearProperty("AHA_HOME");
        } else {
            System.setProperty("AHA_HOME", originalHome);
        }
        System.clearProperty(EncryptedFileSecretStore.MASTER_PASSWORD_ENV);
        CliContext.invalidate();
    }

    private void writeAhaYaml() throws Exception {
        Files.writeString(home.resolve("Aha.yaml"), """
                Aha:
                  Security:
                    KeyStore: encrypted-file
                    KeyStorePath: '${AHA_HOME}/Key/Aha.keystore'
                """);
    }

    private void withMasterPassword(String password) {
        System.setProperty(EncryptedFileSecretStore.MASTER_PASSWORD_ENV, password);
    }

    private String out() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return stderr.toString(StandardCharsets.UTF_8);
    }

    @Test
    void helpIsAvailable() {
        for (String sub : new String[] {"set", "get", "delete", "list"}) {
            stdout.reset();
            int code = cmd.execute("secret", sub, "--help");
            assertThat(code).as(sub).isZero();
            assertThat(out()).as(sub).contains("Usage: aha secret " + sub);
        }
    }

    @Test
    void withoutMasterPasswordGivesActionableError() {
        // 未设主密码时应明确告知所需环境变量，而不是笼统失败
        int code = cmd.execute("secret", "list");

        assertThat(code).isEqualTo(1);
        assertThat(err()).contains("密钥库不可用").contains(EncryptedFileSecretStore.MASTER_PASSWORD_ENV);
    }

    @Test
    void setThenListThenGetThenDelete() {
        withMasterPassword("pw");

        assertThat(cmd.execute("secret", "set", "AHA_DEMO_KEY", "--value", "sk-demo-123456")).isZero();
        assertThat(out()).contains("已写入密钥: AHA_DEMO_KEY").contains("${AHA_DEMO_KEY}");

        stdout.reset();
        assertThat(cmd.execute("secret", "list")).isZero();
        assertThat(out()).contains("AHA_DEMO_KEY");

        stdout.reset();
        assertThat(cmd.execute("secret", "get", "AHA_DEMO_KEY")).isZero();
        assertThat(out().trim()).isEqualTo("sk-demo-123456");

        stdout.reset();
        assertThat(cmd.execute("secret", "get", "AHA_DEMO_KEY", "--mask")).isZero();
        assertThat(out().trim()).isEqualTo("sk-d****3456");

        stdout.reset();
        assertThat(cmd.execute("secret", "delete", "AHA_DEMO_KEY")).isZero();
        assertThat(out()).contains("已删除密钥");
    }

    @Test
    void keystoreFileIsEncryptedOnDisk() throws Exception {
        withMasterPassword("pw");
        cmd.execute("secret", "set", "K", "--value", "plaintext-secret");

        String content = Files.readString(home.resolve("Key/Aha.keystore"));

        // 明文不得出现在密钥库文件中
        assertThat(content).doesNotContain("plaintext-secret");
        assertThat(content).contains("\"entries\"").contains("K");
    }

    @Test
    void getMissingKeyFails() {
        withMasterPassword("pw");

        assertThat(cmd.execute("secret", "get", "nope")).isEqualTo(1);
        assertThat(err()).contains("密钥不存在");
    }

    @Test
    void listOnEmptyKeystoreReportsEmpty() {
        withMasterPassword("pw");

        assertThat(cmd.execute("secret", "list")).isZero();
        assertThat(out()).contains("密钥库为空");
    }

    @Test
    void noArgsPrintsUsage() {
        assertThat(cmd.execute("secret")).isZero();
        assertThat(out()).contains("用法: aha secret");
    }

    @Test
    void wrongMasterPasswordCannotReadExistingSecret() {
        withMasterPassword("right");
        cmd.execute("secret", "set", "K", "--value", "value1");

        System.setProperty(EncryptedFileSecretStore.MASTER_PASSWORD_ENV, "wrong");
        stdout.reset();
        int code = cmd.execute("secret", "get", "K");

        // 解密失败应报错，而不是返回垃圾或明文
        assertThat(code).isEqualTo(1);
    }

    @Test
    void blankValueIsRejectedInNonInteractiveMode() {
        withMasterPassword("pw");

        // 非交互环境下未提供 --value 时应提示改用 --value，而不是挂起等待输入
        int code = cmd.execute("secret", "set", "K");

        assertThat(code).isEqualTo(1);
        assertThat(err()).contains("--value");
    }
}
