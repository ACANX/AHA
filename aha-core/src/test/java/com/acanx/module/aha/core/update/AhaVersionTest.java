package com.acanx.module.aha.core.update;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 版本号解析与比较（issue #51 §2.2）。
 *
 * @since 0.2.0
 */
class AhaVersionTest {

    /** 三形态的升序序列：基线 &lt; 开发版 &lt; 快照。 */
    private static final List<String> ASCENDING = List.of(
            "0.1.3",
            "0.1.3.00001",
            "0.1.3.00233",
            "0.1.3.00233.10161125",
            "0.1.3.00234.10161125",
            "0.1.4");

    @Test
    void parsesThreeForms() {
        AhaVersion baseline = AhaVersion.parse("0.1.3");
        assertThat(baseline.segmentCount()).isEqualTo(3);
        assertThat(baseline.isBaseline()).isTrue();
        assertThat(baseline.hasBuildNumber()).isFalse();
        assertThat(baseline.isSnapshot()).isFalse();
        assertThat(baseline.baseline()).isEqualTo("0.1.3");

        AhaVersion build = AhaVersion.parse("0.1.3.00233");
        assertThat(build.segmentCount()).isEqualTo(4);
        assertThat(build.isBaseline()).isFalse();
        assertThat(build.hasBuildNumber()).isTrue();
        assertThat(build.isSnapshot()).isFalse();
        assertThat(build.baseline()).isEqualTo("0.1.3");
        assertThat(build.buildNumber()).isEqualTo(233);

        AhaVersion snapshot = AhaVersion.parse("0.1.3.00233.10161125");
        assertThat(snapshot.segmentCount()).isEqualTo(5);
        assertThat(snapshot.isSnapshot()).isTrue();
        assertThat(snapshot.baseline()).isEqualTo("0.1.3");
        assertThat(snapshot.buildNumber()).isEqualTo(233);
        assertThat(snapshot.snapshotStamp()).isEqualTo(10161125);
    }

    @Test
    void acceptsLeadingVAndSurroundingSpace() {
        assertThat(AhaVersion.parse("V0.1.3").value()).isEqualTo("0.1.3");
        assertThat(AhaVersion.parse("v0.1.3.00233").value()).isEqualTo("0.1.3.00233");
        assertThat(AhaVersion.parse("  0.1.3.00233.10161125  ").value())
                .isEqualTo("0.1.3.00233.10161125");
    }

    @Test
    void missingSegmentsAreTreatedAsNotYetBuilt() {
        AhaVersion baseline = AhaVersion.parse("0.1.3");
        AhaVersion zeroBuild = AhaVersion.parse("0.1.3.00000");

        // 缺失段比任何真实段都旧 —— 这也是「基线 < 它在 dev 线上的第一次构建」这条口径的来源。
        assertThat(baseline.buildNumber()).isEqualTo(-1);
        assertThat(baseline.snapshotStamp()).isEqualTo(-1);
        assertThat(baseline).isLessThan(zeroBuild);
        assertThat(zeroBuild).isGreaterThan(baseline);
    }

    @Test
    void comparesNumericSegmentsInAscendingOrder() {
        List<AhaVersion> parsed = ASCENDING.stream().map(AhaVersion::parse).toList();

        for (int i = 0; i < parsed.size() - 1; i++) {
            assertThat(parsed.get(i)).isLessThan(parsed.get(i + 1));
            assertThat(parsed.get(i + 1)).isGreaterThan(parsed.get(i));
        }
        assertThat(parsed.get(0)).isEqualByComparingTo(parsed.get(0));

        // 逆序输入排序后应与升序序列一致。
        List<AhaVersion> shuffled = new ArrayList<>(parsed);
        java.util.Collections.reverse(shuffled);
        shuffled.sort(AhaVersion::compareTo);
        assertThat(shuffled).isEqualTo(parsed);
    }

    @Test
    void zeroPaddingDoesNotChangeOrderOrEquality() {
        AhaVersion padded = AhaVersion.parse("0.1.3.00233");
        AhaVersion plain = AhaVersion.parse("0.1.3.233");

        assertThat(padded).isEqualByComparingTo(plain);
        assertThat(padded).isEqualTo(plain);
        assertThat(padded.hashCode()).isEqualTo(plain.hashCode());
    }

    @Test
    void snapshotTimestampOnlyOrdersWithinSameBuild() {
        // 时间戳不含年份，因此跨构建时**只看构建号**：这是刻意口径，不是 bug（#51 §2.2 第 5 条）。
        AhaVersion olderBuild = AhaVersion.parse("0.1.3.00233.12312359");
        AhaVersion newerBuild = AhaVersion.parse("0.1.3.00234.00000001");
        assertThat(olderBuild).isLessThan(newerBuild);
    }

    @Test
    void rejectsMalformedInput() {
        assertThatThrownBy(() -> AhaVersion.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不得为 null");
        assertThatThrownBy(() -> AhaVersion.parse("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不得为空");
        assertThatThrownBy(() -> AhaVersion.parse("V"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不得为空");
        assertThatThrownBy(() -> AhaVersion.parse("0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本号必须是");
        assertThatThrownBy(() -> AhaVersion.parse("0.1.3.1.2.3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本号必须是");
        assertThatThrownBy(() -> AhaVersion.parse("0.1.3."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("纯数字");
        assertThatThrownBy(() -> AhaVersion.parse("0.1.x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("纯数字");
        // 用 parseInt 兼作判据会把 +3 / " 3" 当合法；这里必须拒绝。
        assertThatThrownBy(() -> AhaVersion.parse("0.1.+3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("纯数字");
        assertThatThrownBy(() -> AhaVersion.parse("0.1.3000000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("整数范围");
    }
}
