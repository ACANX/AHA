package com.acanx.module.aha.tool;

import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolProvider;

import java.util.List;

/**
 * 工具提供者模板。
 *
 * <p>放置位置：{@code aha-tool/src/main/java/com/acanx/module/aha/tool/}。</p>
 *
 * <p>新增 Provider 后需在两处声明，缺一不可：</p>
 * <ol>
 *   <li>{@code aha-tool/src/main/java/module-info.java}：
 *       {@code provides com.acanx.module.aha.common.tool.ToolProvider with ...;}</li>
 *   <li>{@code aha-tool/src/main/resources/META-INF/services/}
 *       {@code com.acanx.module.aha.common.tool.ToolProvider}：追加全限定类名</li>
 * </ol>
 *
 * <p>两处都需声明的原因：JPMS 的 {@code provides} 服务于 module-path 运行，
 * {@code META-INF/services} 服务于 classpath 运行。</p>
 *
 * @since 0.1.0
 */
public final class MyToolProvider implements ToolProvider {

    @Override
    public List<Tool> tools() {
        return List.of(new MyTool());
    }
}
