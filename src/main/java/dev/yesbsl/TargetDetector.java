package dev.yesbsl;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/**
 * 判定"哪些对象需要修复"与"所需模组是否在场"。
 *
 * <h2>适配其它模组时，这里通常是你唯一需要修改的地方</h2>
 * 假设你的目标模组也是用 native / 自定义管线写入模型顶点，
 * 只需让 {@link #isTargetEntity} 认识它的目标实体即可，
 * 其余机制（上下文建立、id 覆盖、光影包约定校验）全部可以原样复用。
 *
 * <p>例如目标是某个服务端实体时：
 * <pre>{@code
 * return entity instanceof Player || entity instanceof MyCustomEntity;
 * }</pre>
 *
 * <p>若你的目标根本不由 {@code EntityRenderDispatcher} 渲染
 * （例如第一人称手部），请改看 {@link HandMaterialOverride}。
 */
public final class TargetDetector {

    /** 当前验证过的目标模组。适配其它模组时改这里。 */
    public static final String TARGET_MOD_ID = "yes_steve_model";

    public static final String IRIS_MOD_ID = "iris";

    private static volatile Boolean targetLoaded;
    private static volatile Boolean irisLoaded;

    private TargetDetector() {
    }

    /**
     * 目标模组是否在场。
     *
     * <p><b>只有拿到可信答案时才缓存。</b>模组列表在极早期可能尚未就绪，
     * 那时"查不到"并不等于"没装"；一旦把这种结果固化下来，本模组就会永久失效
     * 且不会有任何报错 —— 这是同类兼容模组最容易踩的坑，值得多这几行。
     */
    public static boolean isTargetModLoaded() {
        Boolean cached = targetLoaded;
        if (cached != null) {
            return cached;
        }
        if (ModList.get() == null) {
            // 尚未就绪：给一个"否"的即时答案，但绝不写进缓存
            return false;
        }
        boolean loaded = YesBsl.isModLoaded(TARGET_MOD_ID);
        targetLoaded = loaded;
        return loaded;
    }

    /** Iris 是否在场；缓存策略同上。 */
    public static boolean isIrisLoaded() {
        Boolean cached = irisLoaded;
        if (cached != null) {
            return cached;
        }
        if (ModList.get() == null) {
            return false;
        }
        boolean loaded = YesBsl.isModLoaded(IRIS_MOD_ID);
        irisLoaded = loaded;
        return loaded;
    }

    /**
     * 该实体是否由目标模组渲染成自定义模型。
     *
     * <p>这里刻意<b>不</b>调用目标模组的内部 API：那会随其版本更新而失效。
     * "实体类型 + 模组是否加载"已经足够精确——即便某个目标实体并没有被真正接管，
     * 让它走光影包的 Skip 路径也没有副作用（Skip 只是跳过视差与法线扰动）。
     */
    public static boolean isTargetEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity instanceof Player || CompatibilityOptions.affectsAllEntities;
    }

    /** 总前置条件：开关打开，且 Iris 与目标模组都在场。 */
    public static boolean enabled() {
        return CompatibilityOptions.enabled && isTargetModLoaded() && isIrisLoaded();
    }
}
