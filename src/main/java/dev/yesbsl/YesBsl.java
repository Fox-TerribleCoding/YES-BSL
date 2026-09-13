package dev.yesbsl;

import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * YES BSL — 模组入口。
 *
 * <p>本模组解决 BSL Shaders 开启 "Advanced Materials" 后，某些由 native /
 * 自定义管线写入顶点的模组模型出现半透明闪烁的问题。完整原理见项目 README。
 *
 * <h2>给适配者的提示</h2>
 * 入口本身不含任何修复逻辑，全部工作由以下三部分完成，按需替换即可：
 * <ul>
 *   <li>{@link TargetDetector} —— 决定"哪些实体/场景需要修复"（适配新模组主要改这里）；</li>
 *   <li>{@link EntityIdOverride} —— 玩家模型等走 {@code gbuffers_entities} 的几何体；</li>
 *   <li>{@link HandMaterialOverride} —— 第一人称手模等走 {@code gbuffers_hand} 的几何体。</li>
 * </ul>
 *
 * <p>实际注入点位于 {@code dev.yesbsl.mixin} 与 {@code dev.yesbsl.mixin.iris} 两个包，
 * 分别对应 Minecraft 与 Iris 的目标类。
 */
@Mod(YesBsl.MOD_ID)
public final class YesBsl {

    public static final String MOD_ID = "yes_bsl";

    /** 日志前缀，集中在这里便于统一修改。 */
    public static final String LOG_TAG = "YES-BSL";

    private static final Logger LOG = LoggerFactory.getLogger(LOG_TAG);

    public YesBsl() {
        CompatibilityOptions.ensureLoaded();

        final boolean iris = TargetDetector.isIrisLoaded();
        final boolean target = TargetDetector.isTargetModLoaded();

        if (!iris) {
            LOG.info("未检测到 Iris，修复逻辑不会生效（本模组仅在 Iris 光影环境下有意义）。");
        } else if (!target) {
            LOG.info("未检测到目标模组，修复逻辑不会生效。");
        } else if (!CompatibilityOptions.enabled) {
            LOG.info("已在配置中禁用（config/yes_bsl.properties: enabled=false）。");
        } else {
            LOG.info("已就绪：渲染目标模型时会把 iris 报告的 id 映射到光影包预留的 Skip 类别"
                            + "（entityId={} / heldItemId={}）。",
                    CompatibilityOptions.skipEntityId, HandMaterialOverride.SKIP_ITEM_ID);
        }
    }

    /** 供其它类做"目标模组是否在场"的判断，避免各处重复拼写 modId。 */
    static boolean isModLoaded(String modId) {
        try {
            return ModList.get() != null && ModList.get().isLoaded(modId);
        } catch (Throwable t) {
            // ModList 在极早期可能尚未就绪；此时视为未加载即可。
            return false;
        }
    }
}
