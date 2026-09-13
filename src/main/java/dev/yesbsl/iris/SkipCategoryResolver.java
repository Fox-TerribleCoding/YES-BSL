package dev.yesbsl.iris;

import dev.yesbsl.CompatibilityOptions;
import dev.yesbsl.TargetDetector;
import net.minecraft.world.entity.Entity;

/**
 * 决定目标几何体应该向光影包报告哪个 entity id。
 *
 * <p>服务于走 {@code gbuffers_entities} 的几何体（玩家模型等），
 * 其 {@code skipParallax} 判据为 {@code float(entityId == 10100)}。
 *
 * <h2>为什么要按光影包实际映射校验，而不是硬编码</h2>
 * 不同光影包使用<b>完全独立</b>的 id 空间。硬编码 10100 可能在别的光影包里
 * 恰好指向另一个实体类别，造成误伤。因此这里读取光影包<b>真实的</b>映射表：
 * 只有当 {@code minecraft:item_frame} 确实被映射到配置值时
 * （即该光影包遵循 BSL 这套约定），修复才生效，否则整体自动失效。
 *
 * <h2>给适配者的提示</h2>
 * 换光影包时，请查它的 {@code entity.properties} 里"跳过法线与视差"
 * 对应的编号，填进配置 {@code skipEntityId}。若要换掉这里的"探针实体"，
 * 注意选一个<b>该光影包必定含在 Skip 列表里</b>的实体（通常是扁平的物品展示类实体）。
 *
 * <p><b>本类刻意不引用任何 Iris 类型</b>，Iris 相关的查询被隔离在
 * {@link IrisIdBridge}，原因见那里的说明。
 */
public final class SkipCategoryResolver {

    private SkipCategoryResolver() {
    }

    /**
     * @return 该实体本次渲染应报告给光影包的 entity id；{@code < 0} 表示不干预
     */
    public static int resolveFor(Entity entity) {
        CompatibilityOptions.ensureLoaded();

        if (!TargetDetector.enabled() || !TargetDetector.isTargetEntity(entity)) {
            return -1;
        }

        // 不缓存该查询：只是一次字段读取加一次 O(1) 的 map 查找，
        // 每实体一次的开销可以忽略，而这样光影包热切换后结果总能立刻跟上。
        return IrisIdBridge.querySkipIdIfPackMatches(CompatibilityOptions.skipEntityId);
    }
}
