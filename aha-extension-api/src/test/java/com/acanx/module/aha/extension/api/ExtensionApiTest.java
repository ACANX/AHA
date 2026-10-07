package com.acanx.module.aha.extension.api;

import com.acanx.module.aha.extension.api.event.DispatchMode;
import com.acanx.module.aha.extension.api.event.EventKey;
import com.acanx.module.aha.extension.api.event.ExtensionEvent;
import com.acanx.module.aha.extension.api.event.ExtensionEventListener;
import com.acanx.module.aha.extension.api.point.CommandDescriptor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 扩展 API 模型测试。
 *
 * @since 0.1.0
 */
class ExtensionApiTest {

    /** 测试用事件。 */
    private record SampleEvent(String value) implements ExtensionEvent {
    }

    @Test
    void descriptorIsValueObject() {
        ExtensionDescriptor descriptor = new ExtensionDescriptor(
                "demo", "Demo", "1.0.0", "0.1", "com.demo.extension", "com.demo.Extension",
                List.of(ExtensionPermission.READ, ExtensionPermission.NETWORK),
                List.of(new ExtensionDependency("base", "[1.0,2.0)", false)));

        assertThat(descriptor.id()).isEqualTo("demo");
        assertThat(descriptor.permissions()).containsExactly(
                ExtensionPermission.READ, ExtensionPermission.NETWORK);
        assertThat(descriptor.dependencies().getFirst().optional()).isFalse();
        assertThat(descriptor).isEqualTo(new ExtensionDescriptor(
                "demo", "Demo", "1.0.0", "0.1", "com.demo.extension", "com.demo.Extension",
                List.of(ExtensionPermission.READ, ExtensionPermission.NETWORK),
                List.of(new ExtensionDependency("base", "[1.0,2.0)", false))));
    }

    @Test
    void eventKeyCarriesMetadata() {
        EventKey<SampleEvent> key =
                EventKey.of("sample", SampleEvent.class, DispatchMode.WATERFALL);

        assertThat(key.key()).isEqualTo("sample");
        assertThat(key.type()).isEqualTo(SampleEvent.class);
        assertThat(key.mode()).isEqualTo(DispatchMode.WATERFALL);
        assertThat(DispatchMode.values()).contains(
                DispatchMode.EMIT, DispatchMode.WATERFALL,
                DispatchMode.SERIAL, DispatchMode.PARALLEL);
    }

    @Test
    void listenerIsFunctionalInterface() {
        List<String> seen = new ArrayList<>();
        ExtensionEventListener<SampleEvent> listener = event -> seen.add(event.value());

        listener.onEvent(new SampleEvent("a"));

        assertThat(seen).containsExactly("a");
    }

    @Test
    void registrationIsAutoCloseable() {
        AtomicBoolean closed = new AtomicBoolean(false);
        Registration registration = () -> closed.set(true);

        registration.close();

        assertThat(closed).isTrue();
        assertThat(registration).isInstanceOf(AutoCloseable.class);
    }

    @Test
    void commandDescriptorIsValueObject() {
        assertThat(new CommandDescriptor("hello", "打招呼"))
                .isEqualTo(new CommandDescriptor("hello", "打招呼"));
    }

    @Test
    void extensionConfigContractIsImplementable() {
        ExtensionConfig config = new ExtensionConfig() {
            private final java.util.Map<String, Object> values =
                    java.util.Map.of("name", "aha", "n", 3, "flag", true);

            @Override
            public java.util.Optional<String> getString(String key) {
                Object value = values.get(key);
                return value instanceof String s ? java.util.Optional.of(s) : java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<Integer> getInt(String key) {
                Object value = values.get(key);
                return value instanceof Integer i ? java.util.Optional.of(i) : java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<Boolean> getBoolean(String key) {
                Object value = values.get(key);
                return value instanceof Boolean b ? java.util.Optional.of(b) : java.util.Optional.empty();
            }

            @Override
            public java.util.Map<String, Object> asMap() {
                return values;
            }
        };

        assertThat(config.getString("name")).contains("aha");
        assertThat(config.getInt("n")).contains(3);
        assertThat(config.getBoolean("flag")).contains(true);
        assertThat(config.getString("missing")).isEmpty();
        assertThat(config.asMap()).containsKey("name");
    }
}
