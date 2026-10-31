package com.acanx.module.aha.desktop;

import com.acanx.module.aha.core.update.InstallLayout;
import com.acanx.module.aha.core.update.UpdateCommand;
import com.acanx.module.aha.core.update.UpdatePaths;

import java.util.List;

/**
 * 桌面端**单体 jar（fat jar）**的启动入口。
 *
 * <p>为什么不把 {@link AhaDesktopApp} 直接写成 {@code Main-Class}：JDK 的启动器在
 * {@code java -jar} 模式下会检查主类——只要主类是 {@code javafx.application.Application}
 * 的子类，它就假定 JavaFX 由模块路径提供，直接报
 * 「Error: JavaFX runtime components are missing, and are required to run this application」。
 * 把 {@code main} 放进一个**不继承 Application** 的普通类再转发过去，同一个 jar 即可用
 * {@code java -jar aha-desktop.jar} 启动（issue #218）。</p>
 *
 * <p>转发而不是重写：{@link AhaDesktopApp#main(String[])} 里还有「启动横幅 + 渲染诊断开关 +
 * {@code launch(Class, args)}」这几步（版本行必须走 stdout，见 issue #26 / TS-2610-003），
 * 重写一份必然漂移。</p>
 *
 * <p>模块路径模式（{@code --module com.acanx.module.aha.desktop/…AhaDesktopApp}）不走本类，
 * 行为与改造前完全一致。</p>
 *
 * @since 0.2.0
 */
public final class AhaDesktopLauncher {

    private AhaDesktopLauncher() {
    }

    /**
     * 启动入口：转发给应用类。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 无界面入口（issue #51 §7 P2）：`--update-to <版本> [--yes]` 只做升级，不启动界面。
        // 放在最前面，是因为这条路径要「先换装、再退出、由 helper 重新拉起」——
        // 一旦进了 JavaFX 启动流程再想干净退出，就得处理窗口、线程与关闭钩子，没必要。
        if (UpdateCommand.isUpdateInvocation(List.of(args))) {
            System.exit(runUpdateCommand(args));
        }
        AhaDesktopApp.main(args);
    }

    /**
     * 执行升级命令（同步；正常路径下会拉起 helper 后返回，由调用方退出进程）。
     *
     * @param args 命令行参数
     * @return 退出码：0 = 成功（或只检查）；1 = 失败；2 = 参数不合法
     */
    private static int runUpdateCommand(String[] args) {
        try {
            UpdateCommand.Options options = UpdateCommand.parse(List.of(args)).orElseThrow();
            return UpdateCommand.run(options, InstallLayout.detect(), UpdatePaths.defaultPaths(),
                    System.out);
        } catch (com.acanx.module.aha.common.exception.UpdateException e) {
            System.out.println("升级命令失败：" + e.getMessage());
            return 2;
        } catch (RuntimeException e) {
            // 推不出安装根这类环境问题：说清楚，不要留一个栈给用户
            System.out.println("升级命令失败：" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return 1;
        }
    }
}
