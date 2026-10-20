package com.acanx.module.aha.desktop.chat;

/**
 * 助手正文的**分段累积器**（issue #403）。
 *
 * <p>一轮对话里模型会多次产出正文：工具调用前的开场白、工具结果回灌后的分析、收尾的总结。
 * 它们分属不同的显示段，而事件流里原有的「整轮结束」只在轮末出现一次——于是旧实现把整轮
 * 正文塞进同一个气泡，总结凭空出现在开场白之后、工具卡片之上。</p>
 *
 * <p>这个类只负责「段号 + 段内累积」这一件纯逻辑：{@link #append} 给出某个<b>确定段号</b>
 * 的全文快照，{@link #end} 收尾当前段并推进段号。渲染侧拿快照里的段号决定「续写当前气泡」
 * 还是「另起一条气泡」，因此排队中的渲染任务不会写进下一段的气泡（跨段串字）。</p>
 *
 * <p>快照还带**代际号**（{@link #reset()} 时加一）：切换会话会清空消息流并复位段号，
 * 而上一轮推理可能还在后台产出，它手里的快照与新会话的第一段撞号。渲染侧只要比对代际号，
 * 就能把「上一会话残留在途的正文」整条丢掉，而不是把它画进新会话（PR #404 审查 P2-2）。</p>
 *
 * <p>刻意不引用任何 JavaFX 类型：这样分段行为就能在**没有图形环境**的机器上被直接断言
 * （与 {@link FoldState} 同一个思路）。线程安全：写入方是推理线程、推进段号的是 UI 线程，
 * 所以对外方法都带锁。</p>
 *
 * @since 0.2.0
 */
public final class AssistantSegments {

    /**
     * 一段正文的渲染快照。
     *
     * <p>文本是提交时刻的**全文**而不是增量：即使渲染被合并（帧内多次提交只渲染最后一次），
     * 也不会丢内容。</p>
     *
     * @param generation 代际号（每次 {@link #reset()} 加一）；与当前代际不符的快照一律丢弃
     * @param seq        段号（自 0 起，每 {@link #end()} 一次加一）
     * @param text       该段到目前为止的全文
     */
    public record Segment(int generation, int seq, String text) {
    }

    private final Object lock = new Object();

    /** 代际号：{@link #reset()} 时加一，用于识别「上一会话残留在途的快照」。 */
    private int generation;

    /** 当前段号。 */
    private int seq;

    /** {@link #buffer} 里累积的是哪一段；与 {@link #seq} 不同表示上一段已收尾。 */
    private int bufferSeq;

    /** 当前段的正文缓冲。 */
    private final StringBuilder buffer = new StringBuilder();

    /**
     * 追加增量，返回当前段的全文快照。
     *
     * @param delta 增量文本（{@code null} 视为空串）
     * @return 该段截至此刻的快照
     */
    public Segment append(String delta) {
        synchronized (lock) {
            // 上一段已被 end() 收尾：本段从空缓冲开始。判据是段号而不是「缓冲是否为空」，
            // 否则上一段恰好结束在空串上时会把两段并成一段
            if (bufferSeq != seq) {
                buffer.setLength(0);
                bufferSeq = seq;
            }
            buffer.append(delta == null ? "" : delta);
            return new Segment(generation, seq, buffer.toString());
        }
    }

    /**
     * 收尾当前段并推进段号。
     *
     * <p>之所以要<b>显式交出</b>最后一份快照，而不是「清空字段等下一段自己重建」：
     * 旧实现只把字段置空、丢下在途的合并值不管，排队的渲染任务取不到值，
     * 收尾正文的末尾就会整段落空（表现为回答结尾被截断）。</p>
     *
     * <p>幂等：连续调用两次时第二次返回 {@code null}。这条性质是必需的——段边界之后
     * 可能还有一次「整轮结束」的收尾（见 {@code ChatController}），若不幂等，
     * 同一段正文会被当成新的一段再渲染一遍，界面上凭空多出一条重复气泡。</p>
     *
     * @return 当前段的最终快照；该段没有正文时返回 {@code null}（调用方不必建空气泡）
     */
    public Segment end() {
        synchronized (lock) {
            if (buffer.length() == 0) {
                // 无正文的边界（模型直接调工具、DoneEvent 的兜底收尾）不占用段号：
                // 段号因此恒等于「有正文的段数」，日志与测试里读到的数字才对得上
                // （PR #404 审查 P3）
                return null;
            }
            Segment last = new Segment(generation, seq, buffer.toString());
            // 缓冲必须清空并跟着段号走：否则重复 end() 会拿旧正文再交一份快照
            buffer.setLength(0);
            // 段号在锁内推进：之后的 append 会立刻按新段累积
            seq++;
            bufferSeq = seq;
            return last;
        }
    }

    /**
     * 复位（切换会话 / 清空对话流时调用）。
     *
     * <p>段号归零的同时推进代际号：复位前已排队的旧快照会带着旧代际号回来，
     * 渲染侧据此丢弃它们——否则旧段的段号会与新会话的第一段撞号，
     * 「静默写进已下线的气泡」就变成「可见的串字」。</p>
     */
    public void reset() {
        synchronized (lock) {
            buffer.setLength(0);
            seq = 0;
            bufferSeq = 0;
            generation++;
        }
    }

    /**
     * 当前代际号（供渲染侧比对快照是否仍属当前会话）。
     *
     * @return 代际号；只在 {@link #reset()} 时加一
     */
    public int generation() {
        synchronized (lock) {
            return generation;
        }
    }

    /**
     * 当前段号（供测试与日志；段号只在 {@link #end()} 时推进）。
     *
     * @return 段号
     */
    public int seq() {
        synchronized (lock) {
            return seq;
        }
    }
}
