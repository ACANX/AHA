package com.acanx.module.aha.extension.api.point;

import java.util.List;

/**
 * 命令扩展点。
 *
 * @since 0.1.0
 */
public interface CommandProvider {

    /**
     * 提供的命令。
     *
     * @return 命令描述符列表
     */
    List<CommandDescriptor> commands();
}
