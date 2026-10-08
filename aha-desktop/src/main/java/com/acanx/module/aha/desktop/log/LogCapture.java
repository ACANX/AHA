package com.acanx.module.aha.desktop.log;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

/**
 * 把日志接进桌面端的内存缓冲（日志面板的数据源）。
 *
 * <p>做法是往 log4j2 的**根记录器**上挂一个 appender（而不是改日志配置）：</p>
 * <ul>
 *   <li>只收 {@code com.acanx.module.aha} 命名空间的事件，避免第三方库把面板刷满；</li>
 *   <li>appender 自身的级别是 {@code TRACE}，但**受记录器级别约束**——配置里是 WARN 时
 *       根本不会产生 DEBUG 事件，面板里也就看不到（这是事实，界面会写明）；</li>
 *   <li>挂不上（无 log4j2 实现、上下文未就绪）就静默降级：日志面板显示「未接入」，
 *       不影响主流程。日志面板是排错工具，不该自己变成故障源。</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class LogCapture {

    /** 只收这个前缀的日志。 */
    public static final String NAMESPACE = "com.acanx.module.aha";

    /** appender 名字（便于重复安装时判重）。 */
    private static final String APPENDER_NAME = "AhaDesktopLogPanel";

    private static final LogBuffer BUFFER = new LogBuffer();

    private static volatile boolean installed;

    private LogCapture() {
    }

    /**
     * 内存缓冲（界面读它）。
     *
     * @return 缓冲
     */
    public static LogBuffer buffer() {
        return BUFFER;
    }

    /**
     * 是否已接入。
     *
     * @return 接入返回 {@code true}
     */
    public static boolean installed() {
        return installed;
    }

    /**
     * 安装 appender（幂等）。
     *
     * @return 成功返回 {@code true}；失败时日志面板仍可用，只是没有实时数据
     */
    public static boolean install() {
        if (installed) {
            return true;
        }
        synchronized (LogCapture.class) {
            if (installed) {
                return true;
            }
            try {
                LoggerContext context = (LoggerContext) LogManager.getContext(false);
                Configuration configuration = context.getConfiguration();
                // 先确保 appender 对象在配置里（重复 install 时它已经在了）
                Appender appender = configuration.getAppender(APPENDER_NAME);
                if (!(appender instanceof BufferAppender)) {
                    appender = new BufferAppender(BUFFER);
                    appender.start();
                    configuration.addAppender(appender);
                }
                // 再确保它挂在根记录器上。**这一步不能省**：uninstall 只摘掉了引用，
                // appender 对象仍留在配置里，若只看「配置里有没有这个 appender」就跳过挂载，
                // 「卸载后再安装」就会静默失效（测试先抓到了这一点）。
                if (!configuration.getRootLogger().getAppenders().containsKey(APPENDER_NAME)) {
                    configuration.getRootLogger().addAppender(appender, Level.TRACE, null);
                }
                context.updateLoggers();
                installed = true;
                return true;
            } catch (RuntimeException e) {
                // 没有 log4j2 实现、或上下文已关闭：降级即可，不要把界面搞崩
                return false;
            }
        }
    }

    /**
     * 卸载 appender（关窗时调用；重复调用安全）。
     */
    public static void uninstall() {
        synchronized (LogCapture.class) {
            if (!installed) {
                return;
            }
            try {
                LoggerContext context = (LoggerContext) LogManager.getContext(false);
                Configuration configuration = context.getConfiguration();
                configuration.getRootLogger().removeAppender(APPENDER_NAME);
                context.updateLoggers();
            } catch (RuntimeException e) {
                // 卸载失败无关紧要：进程退出在即，缓冲会被回收
            }
            installed = false;
        }
    }

    /**
     * 把一条 log4j2 事件转成面板行。
     *
     * @param event 事件
     * @return 日志行
     */
    static LogLine toLine(LogEvent event) {
        return new LogLine(
                LogLevel.of(event.getLevel() == null ? null : event.getLevel().name()),
                event.getLoggerName(),
                event.getMessage() == null ? null : event.getMessage().getFormattedMessage(),
                LogLine.summaryOf(event.getThrown()),
                event.getTimeMillis());
    }

    /** 只把 AHA 自己的日志推进缓冲。 */
    private static final class BufferAppender extends AbstractAppender {

        private final LogBuffer target;

        private BufferAppender(LogBuffer target) {
            super(APPENDER_NAME, null, null, false, Property.EMPTY_ARRAY);
            this.target = target;
        }

        @Override
        public void append(LogEvent event) {
            String logger = event.getLoggerName();
            if (logger == null || !logger.startsWith(NAMESPACE)) {
                return;
            }
            target.add(toLine(event));
        }
    }
}
