package dev.yesbsl.mixin.iris;

import dev.yesbsl.HandMaterialOverride;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把第一人称手部的手持物品 id 谎报成光影包的"地图"类别，
 * 使 {@code gbuffers_hand.glsl} 里的 {@code skipParallax} 变成 1，从而跳过视差贴图。
 *
 * <p>本模组处理的第二类几何体。它与 {@code MixinCapturedRenderingState} 那条路
 * <b>完全独立</b>：{@code gbuffers_hand} 的 {@code skipParallax} 只看手持物品类别，
 * 不看 entityId。详见 {@link HandMaterialOverride} 的类注释。
 *
 * <h2>为什么选 getIntID() 作为注入点</h2>
 * Iris 的 {@code HeldItemSupplier} 把结果缓存在 {@code intID} 字段里、
 * 由 {@code update()} 刷新，而 {@code getIntID()} 是 {@code heldItemId} uniform
 * 的取值点——这一点已由 {@code IdMapUniforms} 的 BootstrapMethods 确认：
 * <pre>
 * 1: LambdaMetafactory.metafactory
 *    Method arguments:
 *    #109 ()I
 *    #111 REF_invokeVirtual ...$HeldItemSupplier.getIntID:()I
 *    #109 ()I
 * </pre>
 * 在读取时覆盖，因此不受 {@code update()} 的调用时机影响。
 *
 * <h2>为什么目标类用 targets 字符串</h2>
 * {@code HeldItemSupplier} 是 {@code IdMapUniforms} 的 {@code private static} 内部类，
 * 无法用类字面量引用，只能通过 {@code targets} 指定。
 *
 * <p>该 Mixin 位于 {@code required=false} 且 {@code defaultRequire=0} 的配置中：
 * 未安装 Iris、或 Iris 内部结构变化导致注入失败时，只会静默失效，不会崩溃。
 */
@Mixin(targets = "net.irisshaders.iris.uniforms.IdMapUniforms$HeldItemSupplier")
public class MixinHeldItemSupplier {

    @Shadow
    @Final
    private InteractionHand hand;

    @Inject(method = "getIntID", at = @At("HEAD"), cancellable = true)
    private void yesbsl$reportSkipItemId(CallbackInfoReturnable<Integer> cir) {
        if (HandMaterialOverride.shouldReportHeldItemId(hand)) {
            cir.setReturnValue(HandMaterialOverride.SKIP_ITEM_ID);
        }
    }
}
