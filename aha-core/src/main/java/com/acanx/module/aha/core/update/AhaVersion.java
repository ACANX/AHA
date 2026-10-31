package com.acanx.module.aha.core.update;

import java.util.Arrays;

/**
 * AHA 版本号：把三种形态统一到一个类型里解析与比较（issue #51 §2.2）。
 *
 * <p>本项目的版本号同时存在三种形态，区别在**段数**：</p>
 *
 * <ul>
 *   <li><b>基线</b>（3 段）：{@code 0.1.3} —— 根目录 {@code version} 文件，正式发版用；</li>
 *   <li><b>开发版</b>（4 段）：{@code 0.1.3.00233} —— 基线 + PR 号补零 5 位，dev 线出包用；</li>
 *   <li><b>快照</b>（5 段）：{@code 0.1.3.00233.10161125} —— 开发版 + 北京时间 {@code MMddHHmm}。</li>
 * </ul>
 *
 * <p><b>比较规则</b>：按 {@code .} 切段后逐段<b>数值</b>比较，缺失段视作 {@code -1}（即更旧）。于是
 * {@code 0.1.3 < 0.1.3.00001 < … < 0.1.3.00233 < 0.1.3.00233.10161125}；且
 * {@code 0.1.3.00233} 与 {@code 0.1.3.233} <b>数值相等</b>（补零不参与比较）。</p>
 *
 * <p><b>已知局限（不要当成完备的时间排序）</b>：快照段的 {@code MMddHHmm} <b>不含年份</b>，
 * 它只在同一「基线 + PR 号」内部作为次序键；跨年歧义由快照包的 7 天有效期兜住
 * （见 {@code Docs/DevSpec/BuildSpec.md} §11.1）。任何依赖「它是一条真实时间线」的判断都是错的。</p>
 *
 * <p>本类无 IO、无外部依赖，因此在 CI 里可完全离线验证（issue #51 §7 的 P1-1）。</p>
 *
 * @since 0.2.0
 */
public final class AhaVersion implements Comparable<AhaVersion> {

    /** 基线版本的段数。 */
    public static final int BASELINE_SEGMENTS = 3;

    /** 开发版（带构建号）的段数。 */
    public static final int BUILD_SEGMENTS = 4;

    /** 快照（带时间戳）的段数。 */
    public static final int SNAPSHOT_SEGMENTS = 5;

    /** 缺失段在比较时的取值：比任何真实段都旧。 */
    private static final int MISSING = -1;

    /** 归一化后的版本文本（不含前导 {@code V}）。 */
    private final String value;

    /** 各段数值。 */
    private final int[] segments;

    private AhaVersion(String value, int[] segments) {
        this.value = value;
        this.segments = segments;
    }

    /**
     * 解析版本号。允许带前导 {@code V} / {@code v}（tag 里就是这个形态），段数必须是 3–5 段。
     *
     * @param text 版本文本，如 {@code 0.1.3} / {@code V0.1.3.00233}
     * @return 版本
     * @throws IllegalArgumentException 文本为空、段数不在 3–5、或某段不是纯数字
     */
    public static AhaVersion parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("版本号不得为 null");
        }
        String normalized = text.trim();
        if (!normalized.isEmpty() && (normalized.charAt(0) == 'V' || normalized.charAt(0) == 'v')) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("版本号不得为空");
        }
        String[] parts = normalized.split("\\.", -1);
        if (parts.length < BASELINE_SEGMENTS || parts.length > SNAPSHOT_SEGMENTS) {
            throw new IllegalArgumentException(
                    "版本号必须是 3–5 段（基线 / 开发版 / 快照）：" + text);
        }
        int[] segments = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || !isDigits(part)) {
                throw new IllegalArgumentException("版本号每段必须是纯数字：" + text);
            }
            try {
                segments[i] = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("版本号某段超出整数范围：" + text, e);
            }
        }
        return new AhaVersion(normalized, segments);
    }

    /**
     * 是否全为 ASCII 数字。
     *
     * <p>刻意不用 {@code Integer.parseInt} 兼作判据：它接受 {@code +1}、前导空白等，
     * 会把 {@code 0.1.+3} 当成合法版本号。</p>
     */
    private static boolean isDigits(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** 归一化后的版本文本（不含前导 {@code V}）。 */
    public String value() {
        return value;
    }

    /** 段数：3 = 基线，4 = 开发版，5 = 快照。 */
    public int segmentCount() {
        return segments.length;
    }

    /** 是否基线形态（3 段，如 {@code 0.1.3}）。 */
    public boolean isBaseline() {
        return segments.length == BASELINE_SEGMENTS;
    }

    /** 是否带构建号的开发版形态（4 段，如 {@code 0.1.3.00233}）。 */
    public boolean hasBuildNumber() {
        return segments.length >= BUILD_SEGMENTS;
    }

    /** 是否快照形态（5 段，如 {@code 0.1.3.00233.10161125}）。 */
    public boolean isSnapshot() {
        return segments.length == SNAPSHOT_SEGMENTS;
    }

    /** 基线部分（前 3 段），如 {@code 0.1.3}。 */
    public String baseline() {
        return segments[0] + "." + segments[1] + "." + segments[2];
    }

    /**
     * 构建号（第 4 段，即 PR 号补零后的数值）。
     *
     * @return 构建号；非开发版 / 快照形态返回 {@code -1}
     */
    public int buildNumber() {
        return segments.length >= BUILD_SEGMENTS ? segments[BUILD_SEGMENTS - 1] : MISSING;
    }

    /**
     * 快照时间戳（第 5 段，北京时间 {@code MMddHHmm}）。
     *
     * @return 时间戳原值；非快照形态返回 {@code -1}
     */
    public int snapshotStamp() {
        return segments.length == SNAPSHOT_SEGMENTS ? segments[SNAPSHOT_SEGMENTS - 1] : MISSING;
    }

    @Override
    public int compareTo(AhaVersion other) {
        int length = Math.max(segments.length, other.segments.length);
        for (int i = 0; i < length; i++) {
            int left = i < segments.length ? segments[i] : MISSING;
            int right = i < other.segments.length ? other.segments[i] : MISSING;
            if (left != right) {
                return left < right ? -1 : 1;
            }
        }
        return 0;
    }

    /**
     * 相等语义与 {@link #compareTo} 一致：补零不产生差异。
     *
     * <p>段数不同时 {@code compareTo} 必然非 0（缺失段取值 {@code -1}，真实段不小于 0），
     * 因此这里按键值比较即可，无需额外归一化。</p>
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof AhaVersion version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(segments);
    }

    @Override
    public String toString() {
        return value;
    }
}
