package dev.yesbsl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * 手持物品 id 的覆盖判定，服务于走 {@code gbuffers_hand} 的几何体（第一人称手模等）。
 *
 * <h2>为什么手模要单独处理</h2>
 * {@code gbuffers_hand.glsl} 的 {@code skipParallax} <b>不看 entityId</b>，
 * 只看手持物品类别：
 * <pre>
 * int heldId = heldItemId / 100;
 * float skipParallax = float(heldId == 4 || (heldId2 == 4 &amp;&amp; isMainHand &lt; 0.5));
 * </pre>
 * 其中 {@code 4} 对应 BSL 为"地图"预留的物品类别（id 为 {@code 400}）——
 * 本来是给这种平面物品准备的逃生舱。
 *
 * <p>实测对照：手持地图时手模完全正常，空手时半透明闪烁，
 * 而两者显示的是同一份模型，唯一变量就是这个 id。
 *
 * <h2>为什么不做"手部渲染作用域"</h2>
 * 曾尝试只在 {@code ItemInHandRenderer.renderHandsWithItems} 期间伪造，
 * 但诊断表明 {@code heldItemId} 的 uniform 是<b>按帧</b>更新的，
 * 且发生在该方法返回<b>之后</b>——调用 {@code getIntID()} 时作用域早已退出。
 * 因此改为按帧判定，不做作用域。
 *
 * <h2>为什么整帧伪造 400 零副作用</h2>
 * 全 BSL 中 {@code heldItemId} 只有三处真正被使用，空手时伪造 400 与真值 0 完全等价：
 * <ul>
 *   <li>{@code gbuffers_hand} 的 {@code skipParallax}：{@code heldId == 4} —— 这是我们要的；</li>
 *   <li>{@code gbuffers_hand} 的 {@code GetHandItem(1/2/3/50/89/213)}：{@code heldId == id}，
 *       0 与 4 都不命中任何一项，结果同为 0；</li>
 *   <li>{@code coloredBlocklight.glsl} 的彩色手部光照：取 {@code heldItemId % 100} 并要求落在 1–50，
 *       而 {@code 0 % 100 == 400 % 100 == 0}，同为"无彩色光"。</li>
 * </ul>
 * 这得益于 BSL 的 {@code ABB} 编号格式——{@code 400} 的彩色光数据位恰好是 {@code 00}。
 *
 * <h2>给适配者的提示</h2>
 * 若你的目标模组渲染的是别的"非实体"内容（粒子、GUI 中的模型、骑乘姿态等），
 * 可以模仿本类：把 {@link #shouldReportHeldItemId} 换成你的触发条件，
 * 再按需替换 {@code MixinHeldItemSupplier} 的注入目标。
 */
public final class HandMaterialOverride {

    /**
     * 光影包 {@code item.properties} 中"跳过法线与视差"的类别 id。
     * {@code 400 / 100 == 4} 命中 BSL 的判定，且 {@code 400 % 100 == 0} 不触发彩色手光。
     */
    public static final int SKIP_ITEM_ID = 400;

    private HandMaterialOverride() {
    }

    /**
     * 每个 uniform 更新周期调用两次（主手 / 副手），开销可忽略。
     * 刻意不做缓存，以免玩家切换手持物品时读到过期状态。
     *
     * @param hand 当前求值的那只手
     * @return 是否应把该手的手持物品 id 谎报为 {@link #SKIP_ITEM_ID}
     */
    public static boolean shouldReportHeldItemId(InteractionHand hand) {
        if (!TargetDetector.enabled()) {
            return false;
        }

        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        // 只在空手时伪造：此时 heldItemId 本来就是 0，所有派生效果都是"无"，
        // 改成 400 不会丢失任何东西。持有物品时保持真值，
        // 以免影响物品自身的发光与彩色光照。
        return player.getItemInHand(hand).isEmpty();
    }
}
