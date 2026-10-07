package com.acanx.module.aha.common.tool;

import java.util.List;

/**
 * 工具提供者 SPI（通过 ServiceLoader 发现）。
 *
 * @since 0.1.0
 */
public interface ToolProvider {

    /**
     * 返回提供的工具列表。
     *
     * @return 工具列表
     */
    List<Tool> tools();
}
