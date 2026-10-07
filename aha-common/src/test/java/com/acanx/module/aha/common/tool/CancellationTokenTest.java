package com.acanx.module.aha.common.tool;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CancellationToken} 测试。
 *
 * @since 0.1.0
 */
class CancellationTokenTest {

    @Test
    void initiallyNotCancelled() {
        CancellationToken token = new CancellationToken();
        assertThat(token.isCancelled()).isFalse();
    }

    @Test
    void notifiesRegisteredListeners() {
        CancellationToken token = new CancellationToken();
        AtomicInteger count = new AtomicInteger();
        token.onCancel(count::incrementAndGet);
        token.onCancel(count::incrementAndGet);

        token.cancel();

        assertThat(token.isCancelled()).isTrue();
        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    void runsListenerImmediatelyWhenAlreadyCancelled() {
        CancellationToken token = new CancellationToken();
        token.cancel();

        AtomicInteger count = new AtomicInteger();
        token.onCancel(count::incrementAndGet);

        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    void cancelIsIdempotent() {
        CancellationToken token = new CancellationToken();
        AtomicInteger count = new AtomicInteger();
        token.onCancel(count::incrementAndGet);

        token.cancel();
        token.cancel();

        assertThat(count.get()).isEqualTo(1);
    }
}
