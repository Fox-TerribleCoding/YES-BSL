package dev.yesbsl.iris;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;

/**
 * 唯一直接接触 Iris 类型的类。
 *
 * <h2>为什么必须单独隔离</h2>
 * JVM 在解析/验证一个类时会尝试解析它引用的类型。如果
 * {@link SkipCategoryResolver} 直接声明或使用 Iris 的类型
 * （例如把 {@code NamespacedId} 当字段），那么在<b>未安装 Iris</b>
 * 的环境里加载它就可能抛 {@code NoClassDefFoundError}——而它又是被
 * 作用于 Minecraft 类的 Mixin 调用的，于是游戏会直接崩溃。
 *
 * <p>把 Iris 引用全部收敛到这个类之后：
 * <ul>
 *   <li>调用方只持有 {@code int}，不引用任何 Iris 类型；</li>
 *   <li>本类只在确认 Iris 已加载后才会被首次主动使用，从而才被加载；</li>
 *   <li>Iris 类型只出现在<b>方法体内</b>（局部变量 / 构造调用），
 *       属于延迟解析，不在类签名里。</li>
 * </ul>
 * 因此未安装 Iris 时本模组完全惰性、不会造成任何崩溃。
 *
 * <h2>适配提示</h2>
 * 若你要适配的光影包用别的实体作为"Skip 类别"的探针，
 * 改 {@link #PROBE_ENTITY} 即可；若它根本没有这种映射表，
 * 那就应该放弃本类、改为直接返回固定 id。
 */
final class IrisIdBridge {

    /** 探针实体：光影包把它归入那个"跳过法线与视差"的实体类别。 */
    private static final NamespacedId PROBE_ENTITY = new NamespacedId("minecraft", "item_frame");

    private IrisIdBridge() {
    }

    /**
     * 查询该光影包是否遵循 BSL 的 "Skip Normal &amp; Parallax" 约定。
     *
     * <p>判据：探针实体被映射到的 id 是否恰好等于配置值。
     * 只返回 {@code int}，避免把 Iris 类型泄漏到调用方的类签名里。
     *
     * @return 应使用的 entity id，或 {@code -1} 表示该光影包不适用
     */
    static int querySkipIdIfPackMatches(int configuredId) {
        try {
            final Object2IntFunction<NamespacedId> entityIds =
                    WorldRenderingSettings.INSTANCE.getEntityIds();

            if (entityIds != null && entityIds.applyAsInt(PROBE_ENTITY) == configuredId) {
                return configuredId;
            }
        } catch (Throwable ignored) {
            // 光影包未加载 / Iris 内部结构变化：安全失效，不做任何干预。
        }
        return -1;
    }
}
