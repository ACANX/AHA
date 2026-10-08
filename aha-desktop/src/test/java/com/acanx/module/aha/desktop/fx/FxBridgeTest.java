package com.acanx.module.aha.desktop.fx;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FxBridge} 测试：合并、终值不丢、关窗联动、渲染失败不致命。
 *
 * <p>这些断言就是线程模型的可执行契约：不需要 JavaFX 运行时即可验证。</p>
 *
 * @since 0.2.0
 */
class FxBridgeTest {

    @Test
    void highFrequencySubmitsCoalesceIntoSingleUiTask() {
        RecordingFxDispatcher dispatcher = new RecordingFxDispatcher();
        List<String> rendered = new ArrayList<>();
        FxBridge<String> bridge = new FxBridge<>(dispatcher, rendered::add);

        for (int i = 0; i < 100; i++) {
            bridge.submit("token-" + i);
        }

        // 100 次提交 → 只投递一个 UI 任务
        assertThat(dispatcher.dispatchCount()).isEqualTo(1);
        dispatcher.drain();
        // 渲染一次，且是最后一个值（终值不丢）
        assertThat(rendered).containsExactly("token-99");
    }

    @Test
    void submitAfterFlushSchedulesAnotherUiTask() {
        RecordingFxDispatcher dispatcher = new RecordingFxDispatcher();
        List<String> rendered = new ArrayList<>();
        FxBridge<String> bridge = new FxBridge<>(dispatcher, rendered::add);

        bridge.submit("a");
        dispatcher.drain();
        bridge.submit("b");
        dispatcher.drain();

        assertThat(rendered).containsExactly("a", "b");
        assertThat(dispatcher.dispatchCount()).isEqualTo(2);
    }

    @Test
    void valueArrivingDuringRenderIsNotLost() {
        RecordingFxDispatcher dispatcher = new RecordingFxDispatcher();
        List<String> rendered = new ArrayList<>();
        // 渲染动作要在渲染过程中反向提交新值，故先占位再赋值
        AtomicReference<FxBridge<String>> holder = new AtomicReference<>();
        holder.set(new FxBridge<>(dispatcher, value -> {
            rendered.add(value);
            if ("first".equals(value)) {
                // 模拟渲染过程中后台又送来一个新值
                holder.get().submit("second");
            }
        }));
        FxBridge<String> bridge = holder.get();

        bridge.submit("first");
        // 只跑这一帧：渲染 first 的过程中排上了新任务，但还不该被渲染
        dispatcher.drainCurrent();
        assertThat(rendered).containsExactly("first");
        assertThat(dispatcher.pending()).isEqualTo(1);

        // 下一帧把新值渲染出来——说明渲染期间到达的值被重新排程，而不是被吞掉
        dispatcher.drainCurrent();
        assertThat(rendered).containsExactly("first", "second");
    }

    @Test
    void closedBridgeDropsLaterSubmissions() {
        RecordingFxDispatcher dispatcher = new RecordingFxDispatcher();
        List<String> rendered = new ArrayList<>();
        FxBridge<String> bridge = new FxBridge<>(dispatcher, rendered::add);

        bridge.submit("before");
        bridge.close();
        bridge.submit("after");
        dispatcher.drain();

        // close() 连「已排程但尚未渲染」的值也一并丢弃：正在关窗，渲染已无意义
        assertThat(bridge.isClosed()).isTrue();
        assertThat(rendered).isEmpty();
    }

    @Test
    void nullValueIsIgnored() {
        RecordingFxDispatcher dispatcher = new RecordingFxDispatcher();
        List<String> rendered = new ArrayList<>();
        FxBridge<String> bridge = new FxBridge<>(dispatcher, rendered::add);

        bridge.submit(null);
        dispatcher.drain();

        assertThat(dispatcher.dispatchCount()).isZero();
        assertThat(rendered).isEmpty();
    }

    @Test
    void renderFailureDoesNotBreakLaterUpdates() {
        RecordingFxDispatcher dispatcher = new RecordingFxDispatcher();
        List<String> rendered = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        FxBridge<String> bridge = new FxBridge<>(dispatcher, value -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("模拟渲染异常");
            }
            rendered.add(value);
        });

        bridge.submit("boom");
        dispatcher.drain();
        bridge.submit("ok");
        dispatcher.drain();

        assertThat(rendered).containsExactly("ok");
    }
}
