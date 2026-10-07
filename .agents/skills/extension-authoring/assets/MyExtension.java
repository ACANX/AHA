package com.example.myextension;

import com.acanx.module.aha.extension.api.AhaExtension;
import com.acanx.module.aha.extension.api.ExtensionContext;
import com.acanx.module.aha.extension.api.ExtensionDescriptor;
import com.acanx.module.aha.extension.api.ExtensionPermission;
import com.acanx.module.aha.extension.api.Registration;
import com.acanx.module.aha.extension.api.point.EventListenerProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * 扩展实现模板。
 *
 * <p>扩展只依赖 {@code aha-extension-api} 与 {@code aha-common}，不依赖 {@code aha-core}。</p>
 *
 * <p>同时需要提供 {@code AhaExtension.yaml} 描述符，并在
 * {@code META-INF/services/com.acanx.module.aha.extension.api.AhaExtension} 中声明本类。</p>
 *
 * <p>关键约束：{@code onStart} 中获得的每个 {@link Registration} 都必须保存，
 * 并在 {@code onStop} 中关闭，保证注册可逆。</p>
 *
 * @since 0.1.0
 */
public final class MyExtension implements AhaExtension {

    /** onStart 登记的资源，onStop 统一注销。 */
    private final List<Registration> registrations = new ArrayList<>();

    @Override
    public ExtensionDescriptor descriptor() {
        return new ExtensionDescriptor(
                "com.example.my",          // id
                "My Extension",               // name
                "1.0.0",                   // version
                "1.0",                     // apiVersion
                "com.example.myextension",    // mainModule
                "com.example.myextension.MyExtension", // mainClass
                List.of(ExtensionPermission.NETWORK),  // permissions：按需最小化
                List.of());                // dependencies
    }

    /**
     * 启动扩展，注册扩展点。
     *
     * <p>可注册的扩展点：{@code ToolProvider}、{@code CommandProvider}、
     * {@code ConfigSource}、{@code EventListenerProvider}。
     * 下面用函数式接口 {@code EventListenerProvider} 作示例，便于直接编译。</p>
     */
    @Override
    public void onStart(ExtensionContext context) {
        registrations.add(context.register(EventListenerProvider.class, event ->
                context.logger().log(System.Logger.Level.DEBUG, "收到 Agent 事件: " + event)));

        context.logger().log(System.Logger.Level.INFO, "My Extension 已启动");
    }

    /**
     * 停止扩展，逆序注销全部注册。
     */
    @Override
    public void onStop() {
        for (int i = registrations.size() - 1; i >= 0; i--) {
            registrations.get(i).close();
        }
        registrations.clear();
    }
}
