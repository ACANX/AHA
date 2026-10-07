package com.acanx.module.aha.cli;

import com.acanx.module.aha.cli.render.Renderer;
import com.acanx.module.aha.cli.render.Renderers;
import com.acanx.module.aha.cli.tty.OutputEncoding;
import com.acanx.module.aha.cli.tty.TerminalCapabilities;
import com.acanx.module.aha.common.event.ContentEvent;
import com.acanx.module.aha.common.exception.Exceptions;
import com.acanx.module.aha.common.tool.ToolApprover;
import com.acanx.module.aha.core.service.AgentResponse;
import com.acanx.module.aha.core.service.AgentService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * 单次推理命令。
 *
 * @since 0.1.0
 */
@Command(name = "run", description = "单次推理", mixinStandardHelpOptions = true)
public final class RunCommand implements Callable<Integer> {

    /** 输入。 */
    @Parameters(index = "0", description = "输入内容")
    String input;

    /** 模型名。 */
    @Option(names = "--model", description = "模型名")
    String model;

    /** 显式系统提示词。 */
    @Option(names = "--system", description = "系统提示词文本（覆盖文件加载）")
    String systemText;

    /** 显式系统提示词文件。 */
    @Option(names = "--system-file", description = "系统提示词文件（覆盖约定文件）")
    Path systemFile;

    /** 是否自动授权高风险工具。 */
    @Option(names = {"-y", "--yes"},
            description = "自动授权 WRITE/EXECUTE/ADMIN 工具")
    boolean autoYes;

    /** 关闭 ANSI 颜色。 */
    @Option(names = "--no-color", description = "关闭 ANSI 颜色（也受 NO_COLOR 环境变量影响）")
    boolean noColor;

    @Override
    public Integer call() {
        // 单次推理无交互确认余地：未加 --yes 时需授权的工具一律拒绝并给出授权指引
        AgentService service = CliContext.service(autoYes
                ? (toolName, permission, arguments) -> true
                : ToolApprover.denyAll());
        CliSession.Opened opened = CliSession.open(
                service, null, model, systemText, systemFile);
        try {
            // 非流式：内容一次性到达，仍走渲染器以复用 Markdown 渲染、折行与截断策略。
            // 输出被重定向时自动降级为纯文本（不输出 ANSI）。
            OutputEncoding encoding = OutputEncoding.detect();
            String label = model != null && !model.isBlank()
                    ? model : service.runtime().attributes().get("Model");
            Renderer renderer = Renderers.of(
                    TerminalCapabilities.detect(noColor), encoding, label, System.out);
            // 单次推理同样是长等待，与 chat 一致给出反馈
            renderer.beginTurn();
            AgentResponse response = service.chat(opened.sessionId(), input).join();
            renderer.onEvent(new ContentEvent(opened.sessionId(), response.content()));
            renderer.endTurn();
            return 0;
        } catch (RuntimeException e) {
            System.err.println("[error] " + Exceptions.message(e));
            return 1;
        } finally {
            service.closeSession(opened.sessionId());
            // 会话已持久化；给出 ID 以便后续用 `aha chat --session <id>` 继续
            System.err.println("会话已保存：" + opened.sessionId());
            System.err.println("续接本次会话：aha chat --session " + opened.sessionId());
        }
    }
}
