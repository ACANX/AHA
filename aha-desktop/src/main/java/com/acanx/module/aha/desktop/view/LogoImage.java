package com.acanx.module.aha.desktop.view;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.IOException;
import java.io.InputStream;

/**
 * 桌面端标志：{@code Logo.svg} 的位图版本。
 *
 * <p>{@code Logo.svg}（{@code aha-core/src/main/resources}）是**唯一矢量来源**，
 * 但 JavaFX 的 {@link Image} 只接受位图（PNG/JPEG/GIF/BMP），不认 SVG。
 * 因此把矢量渲染一次、把 PNG 入库，避免在运行时引入 SVG 渲染库或 WebView。
 * 改标志请改 SVG，然后重跑 {@code python3 bin/GenLogoPng.py}。</p>
 *
 * <p>同一份图片两处使用：窗口 / 任务栏图标，以及空会话状态下的展示。</p>
 *
 * @since 0.1.1
 */
public final class LogoImage {

    /** 资源文件名（与本类同包）。 */
    private static final String RESOURCE = "logo.png";

    /** 空态展示时的边长（px）。 */
    public static final double DISPLAY_SIZE = 96;

    private static Image cached;

    private LogoImage() {
    }

    /**
     * 载入标志图片；同一进程内只读一次。
     *
     * @return 图片；资源缺失或解码失败时返回 {@code null}（调用方据此降级，不应阻断启动）
     */
    public static synchronized Image load() {
        if (cached != null) {
            return cached;
        }
        InputStream stream = LogoImage.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            return null;
        }
        try (InputStream in = stream) {
            cached = new Image(in);
            return cached;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 取一个按比例缩放的展示控件。
     *
     * @param size 边长（px）
     * @return 图片控件；资源不可用时返回 {@code null}
     */
    public static ImageView view(double size) {
        Image image = load();
        if (image == null) {
            return null;
        }
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }
}
