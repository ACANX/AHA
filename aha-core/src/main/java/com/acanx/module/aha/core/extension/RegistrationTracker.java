package com.acanx.module.aha.core.extension;

import com.acanx.module.aha.extension.api.Registration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 注册追踪器：记录扩展注册的资源，关闭时统一回滚。
 *
 * @since 0.1.0
 */
public final class RegistrationTracker {

    private final List<Registration> registrations = new CopyOnWriteArrayList<>();

    /**
     * 追踪一次注册。
     *
     * @param extensionPoint 扩展点类型
     * @param implementation 实现
     * @param <T>            扩展点类型
     * @return 注册句柄
     */
    public <T> Registration track(Class<T> extensionPoint, T implementation) {
        // TODO(0.3): 将实现注册到扩展点注册表，并返回可逆 Registration
        Registration registration = () -> {
            // 回滚注册
        };
        registrations.add(registration);
        return registration;
    }

    /**
     * 回滚全部注册。
     */
    public void closeAll() {
        for (Registration registration : registrations) {
            registration.close();
        }
        registrations.clear();
    }
}
