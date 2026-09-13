package dev.yesbsl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yesbsl.EntityIdOverride;
import dev.yesbsl.iris.SkipCategoryResolver;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在每次实体渲染的开始 / 结束维护 {@link EntityIdOverride} 的作用域。
 *
 * <h2>为什么注入这里</h2>
 * 注入 {@code EntityRenderDispatcher.render} 的 HEAD/RETURN，而不是内部的
 * {@code EntityRenderer.render} 调用点，原因有二：
 * <ol>
 *   <li>目标模组（如 YSM）自己就可能用 MixinExtras 的 {@code @WrapWithCondition}
 *       包裹内部那次 {@code EntityRenderer.render} 调用以短路原版模型绘制。
 *       若我们注入那里，在真正渲染其模型时反而不会被执行。</li>
 *   <li>HEAD/RETURN 的配对不会被任何条件取消破坏，栈不会失衡。</li>
 * </ol>
 *
 * <h2>关于注入的严格程度</h2>
 * 这里与 Iris 那两个 Mixin 不同：目标类是 <b>Minecraft 自己的类</b>，签名稳定，
 * 因此本配置使用 {@code required=true} + {@code defaultRequire=1} ——
 * 一旦靶点对不上（例如跨大版本），会<b>明确报错</b>而不是"悄悄什么都不做"。
 * 这是刻意的选择：本模组的唯一功能就挂在这一处，静默失效比报错更难排查。
 * 方法描述符写全，避免将来同名重载造成歧义。
 *
 * <h2>给适配者的提示</h2>
 * 若目标模组不由 {@code EntityRenderDispatcher} 渲染（例如第一人称手部），
 * 本类不会生效——请参考 {@code HandMaterialOverride} 与
 * {@code MixinHeldItemSupplier} 那条路。
 */
@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    private static final String RENDER =
            "render(Lnet/minecraft/world/entity/Entity;DDDFF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void yesbsl$pushOverride(Entity entity, double x, double y, double z,
                                     float rotationYaw, float partialTicks,
                                     PoseStack poseStack, MultiBufferSource bufferSource,
                                     int packedLight, CallbackInfo ci) {
        EntityIdOverride.push(SkipCategoryResolver.resolveFor(entity));
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void yesbsl$popOverride(Entity entity, double x, double y, double z,
                                    float rotationYaw, float partialTicks,
                                    PoseStack poseStack, MultiBufferSource bufferSource,
                                    int packedLight, CallbackInfo ci) {
        EntityIdOverride.pop();
    }
}
