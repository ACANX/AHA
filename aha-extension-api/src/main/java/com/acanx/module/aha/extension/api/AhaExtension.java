package com.acanx.module.aha.extension.api;

/**
 * 扩展入口契约。
 *
 * @since 0.1.0
 */
public interface AhaExtension {

    /**
     * 扩展描述符。
     *
     * @return 描述符
     */
    ExtensionDescriptor descriptor();

    /**
     * 扩展启动回调。
     *
     * @param context 扩展上下文
     */
    void onStart(ExtensionContext context);

    /**
     * 扩展停止回调。
     */
    void onStop();
}
