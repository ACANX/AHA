package com.acanx.module.aha.desktop.view;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 剪贴板写入（带兜底）。
 *
 * <p>无显示环境（或系统剪贴板不可用）时不该把界面搞崩：拿不到剪贴板就记一条日志返回
 * {@code false}，由调用方决定是否提示。抽成一处，是为了让「复制」在代码块、工具输出、
 * 会话导出等所有入口行为一致。</p>
 *
 * @since 0.2.0
 */
public final class Clipboards {

    private static final Logger LOG = LoggerFactory.getLogger(Clipboards.class);

    private Clipboards() {
    }

    /**
     * 把文本写入系统剪贴板。
     *
     * @param text 文本
     * @return 成功返回 {@code true}
     */
    public static boolean copy(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            return Clipboard.getSystemClipboard().setContent(content);
        } catch (RuntimeException e) {
            // 无头环境、或剪贴板被其它进程占用：不致命，记一条日志即可
            LOG.warn("写入剪贴板失败：{}", e.getMessage());
            return false;
        }
    }
}
