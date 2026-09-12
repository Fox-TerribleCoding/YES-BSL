package dev.yesbsl;

/**
 * 实体 entity id 的覆盖上下文。
 *
 * <p>服务于走 {@code gbuffers_entities} 的几何体（玩家模型等），
 * 其 {@code skipParallax} 判据是 {@code entityId == 10100}。
 *
 * <h2>为什么用"读取时覆盖"而不是"写入时改写"</h2>
 * Iris 在 {@code EntityRenderDispatcher.render} 里 pushPose 之后调用
 * {@code CapturedRenderingState.setCurrentEntity(id)}。若我们也在同一位置注入去改写，
 * 谁先谁后取决于 Mixin 的应用顺序，不可靠。改为在<b>读取</b>
 * {@code getCurrentRenderedEntity()} 时返回覆盖值则与顺序无关：
 * 读取发生在渲染过程中，此时本上下文恰好指向被渲染的对象。
 *
 * <h2>性能</h2>
 * {@code getCurrentRenderedEntity()} 会被顶点写入路径（Iris 的
 * {@code MixinBufferBuilder}）以及目标模组的 native 顶点写入器<b>按顶点</b>调用，
 * 所以热路径被压缩成<b>一次 volatile int 读</b>：{@link #activeOverrideId}。
 *
 * <p>栈本身用 {@code ThreadLocal}，但只在 push/pop（每个实体一次）时访问，
 * 不进入按顶点执行的热路径。渲染发生在 Render thread，
 * 因此把当前覆盖值放在静态字段里是安全的。
 */
public final class EntityIdOverride {

    private static final int NO_OVERRIDE = -1;

    /** 正常渲染嵌套远达不到这个深度；超过说明发生了异常导致栈失衡。 */
    private static final int MAX_DEPTH = 64;

    /**
     * 热路径唯一读取的字段：当前生效的覆盖 id，{@code < 0} 表示不覆盖。
     * 用单个 volatile int 而非"布尔 + 方法调用 + ThreadLocal"，
     * 让每个顶点一次的读取保持最廉价。
     */
    public static volatile int activeOverrideId = NO_OVERRIDE;

    private static final ThreadLocal<Frame> STACK = ThreadLocal.withInitial(Frame::new);

    private EntityIdOverride() {
    }

    /**
     * 建立一次覆盖作用域，必须与 {@link #pop()} 配对。
     *
     * @param overrideId 本次渲染要报告给光影包的 entity id；{@code < 0} 表示不覆盖
     */
    public static void push(int overrideId) {
        final Frame frame = STACK.get();

        if (frame.depth >= MAX_DEPTH) {
            // 渲染抛异常时 RETURN 注入不会执行，栈可能残留。
            // 就地重建，避免永久污染后续所有实体的 entity id。
            frame.depth = 0;
        }

        frame.ids[frame.depth] = overrideId;
        frame.depth++;
        activeOverrideId = overrideId;
    }

    public static void pop() {
        final Frame frame = STACK.get();
        if (frame.depth > 0) {
            frame.depth--;
        }
        activeOverrideId = frame.depth > 0 ? frame.ids[frame.depth - 1] : NO_OVERRIDE;
    }

    private static final class Frame {
        private final int[] ids = new int[MAX_DEPTH];
        private int depth;
    }
}
