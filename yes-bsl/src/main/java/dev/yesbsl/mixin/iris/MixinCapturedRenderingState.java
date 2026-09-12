package dev.yesbsl.mixin.iris;

import dev.yesbsl.EntityIdOverride;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Iris 在渲染目标模型期间报告光影包的 "Skip Normal &amp; Parallax" entity id。
 *
 * <p>这个值有两类消费者，两者都需要被覆盖：
 * <ol>
 *   <li>Iris 自己的 {@code MixinBufferBuilder} 在写顶点时把它填进
 *       {@code iris_Entity} 属性；</li>
 *   <li>目标模组的 Iris 兼容代码会在渲染模型前调用本方法抓取 entity id，
 *       打包后交给 native 顶点写入器（后者直接按
 *       {@code IrisVertexFormats.ENTITY} 写内存）。</li>
 * </ol>
 * 因此在这里覆盖可以同时影响两条路径，而不需要碰目标模组的混淆代码。
 *
 * <p>该 Mixin 位于 {@code required=false} 的配置中，未安装 Iris 时会被安全跳过。
 *
 * <h2>给适配者的提示</h2>
 * 若目标模组读取 entity id 的方式不同（例如自己缓存、不经由本方法），
 * 则需要改为在其读取点注入。
 */
@Mixin(CapturedRenderingState.class)
public class MixinCapturedRenderingState {

    @Inject(method = "getCurrentRenderedEntity", at = @At("HEAD"), cancellable = true)
    private void yesbsl$reportSkipEntityId(CallbackInfoReturnable<Integer> cir) {
        // 热路径：本方法会按顶点被调用，因此只做一次 volatile 读就决定是否覆盖。
        final int overrideId = EntityIdOverride.activeOverrideId;
        if (overrideId >= 0) {
            cir.setReturnValue(overrideId);
        }
    }
}
