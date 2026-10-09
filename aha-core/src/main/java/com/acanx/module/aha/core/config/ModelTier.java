package com.acanx.module.aha.core.config;

import java.util.Optional;

/**
 * 模型档位：一个供应商内按定位划分的模型层次。
 *
 * <p>本轮固定五档，<b>顺序即强弱</b>（{@link #ULTRA} 最强、{@link #FALLBACK} 最兜底），
 * 供 UI 排序、降级链与文档引用。档位名与 {@code Model.yml} 中的配置键一致（PascalCase）。</p>
 *
 * <p><b>档位判定规则</b>：用五值白名单<b>精确匹配且大小写敏感</b>——只有精确等于
 * {@code Ultra} / {@code Pro} / {@code Standard} / {@code Flash} / {@code Fallback}
 * 的输入才被当作档位；{@code standard}、{@code pro} 等小写形式一律按模型名处理，
 * {@code MiniMax-M3} 这类大写开头的模型名也不会被误判。</p>
 *
 * @since 0.1.0
 */
public enum ModelTier {

    /** 性能顶级、价格稍贵的旗舰模型。 */
    ULTRA("Ultra"),

    /** 旗舰级别的性能、价格稍贵的模型。 */
    PRO("Pro"),

    /** 性能价格均衡的标准模型（默认档，唯一必填）。 */
    STANDARD("Standard"),

    /** 便宜、性价比高的 Flash 模型。 */
    FLASH("Flash"),

    /** 量大管饱的兜底模型。 */
    FALLBACK("Fallback");

    /** 缺省档位：一切档位解析的最终兜底。 */
    public static final ModelTier DEFAULT = STANDARD;

    private final String configName;

    ModelTier(String configName) {
        this.configName = configName;
    }

    /**
     * 配置键名（PascalCase，与 {@code Model.yml} 的 {@code Models} 键一致）。
     *
     * @return 配置键名
     */
    public String configName() {
        return configName;
    }

    /**
     * 按配置键名精确解析档位（大小写敏感）。
     *
     * @param name 输入值，可为 {@code null}
     * @return 档位；不是五值白名单之一时返回 {@link Optional#empty()}
     */
    public static Optional<ModelTier> fromConfigName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        for (ModelTier tier : values()) {
            if (tier.configName.equals(name)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    /**
     * 输入是否为档位名（五值白名单精确匹配 + 大小写敏感）。
     *
     * @param name 输入值
     * @return 是档位名返回 {@code true}
     */
    public static boolean isTierName(String name) {
        return fromConfigName(name).isPresent();
    }

    /**
     * 档位强弱顺序（从强到弱）的稳定定义。
     *
     * @return 顺序固定的档位数组
     */
    public static ModelTier[] strongestFirst() {
        return new ModelTier[]{ULTRA, PRO, STANDARD, FLASH, FALLBACK};
    }
}
