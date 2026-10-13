package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.core.config.ModelTier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 供应商对话框（{@code provider-dialog.fxml}）与控制器的一致性。
 *
 * <p>通用对账见 {@link FxmlViewChecks}。这里额外盯住本视图特有的一处**口径分裂风险**：
 * 档位行在 fxml 里是**写死的**（Ultra / Pro / Standard / Flash / Fallback），
 * 而档位本身来自 {@code ModelTier} 枚举——枚举改了、fxml 忘了改，界面上就会少一两档，
 * 而编译期毫无反应。控制器里有一次运行时硬校验（{@code setTierInputs}），
 * 这里再补一条**静态**对账，让问题在秒级的单测里就暴露。</p>
 *
 * @since 0.1.3
 */
class ProviderDialogFxmlTest {

    private static final String FXML = UiResources.PROVIDER_DIALOG_FXML;

    @Test
    void fxmlShipsNextToTheController() throws IOException {
        assertThat(FxmlViewChecks.read(ProviderDialog.class, FXML))
                .as("%s 必须与 ProviderDialog 同包", FXML)
                .contains("fx:id=\"root\"")
                .contains("fx:id=\"editGrid\"")
                .contains("fx:id=\"createGrid\"");
    }

    @Test
    void everyFxIdHasAnInjectedField() throws IOException {
        FxmlViewChecks.assertFxIdsInjected("provider-dialog",
                FxmlViewChecks.read(ProviderDialog.class, FXML), ProviderDialog.class);
    }

    @Test
    void everyInjectedFieldIsUsedByTheFxml() throws IOException {
        FxmlViewChecks.assertInjectedFieldsUsed("provider-dialog",
                FxmlViewChecks.read(ProviderDialog.class, FXML), ProviderDialog.class);
    }

    @Test
    void everyElementTypeIsImported() throws IOException {
        FxmlViewChecks.assertElementsImported("provider-dialog",
                FxmlViewChecks.read(ProviderDialog.class, FXML));
    }

    @Test
    void everyFxmlInstantiatedTypeRegistersItsConstructor() throws IOException {
        FxmlViewChecks.assertConstructorsRegistered("provider-dialog",
                FxmlViewChecks.read(ProviderDialog.class, FXML));
    }

    @Test
    void resourceIsWhitelistedForNativeImage() throws IOException {
        FxmlViewChecks.assertResourceWhitelisted(UiResources.relative(FXML));
    }

    /**
     * 两区的档位行都要覆盖 {@link ModelTier} 的每一档，且数量一致。
     *
     * <p>断言的是三件事：档位输入框数量、字段命名（{@code editTier<configName>}）、
     * 以及标签文案里出现该档位名。少一档、多一档、改名没同步，都会被这条挡住。</p>
     */
    @Test
    void everyTierHasItsOwnInputRowInBothSections() throws IOException {
        String fxml = FxmlViewChecks.read(ProviderDialog.class, FXML);
        List<String> ids = FxmlViewChecks.fxIds(fxml);
        ModelTier[] tiers = ModelTier.strongestFirst();

        long editTierInputs = ids.stream().filter(id -> id.startsWith("editTier")).count();
        long newTierInputs = ids.stream().filter(id -> id.startsWith("newTier")).count();
        assertThat(editTierInputs)
                .as("编辑区的档位输入框数量必须等于 ModelTier 的档位数（%d）：fxml 与枚举的口径分裂"
                        + "不会编译报错，只会让界面上少一档", tiers.length)
                .isEqualTo(tiers.length);
        assertThat(newTierInputs)
                .as("新增区同理")
                .isEqualTo(tiers.length);

        for (ModelTier tier : tiers) {
            assertThat(fxml)
                    .as("档位 %s：输入框 fx:id 必须是 editTier%s / newTier%s（控制器按此命名注入）",
                            tier.configName(), tier.configName(), tier.configName())
                    .contains("fx:id=\"editTier" + tier.configName() + "\"")
                    .contains("fx:id=\"newTier" + tier.configName() + "\"")
                    .contains("档位 " + tier.configName());
        }
    }
}
