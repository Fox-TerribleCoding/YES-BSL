package dev.yesbsl;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 运行时开关。
 *
 * <p>刻意不引入 NeoForge 的配置系统：只有三个旋钮，而且它们会在渲染线程被读取。
 * 改为直接读写 {@code <gameDir>/config/yes_bsl.properties}，零额外依赖。
 *
 * <h2>给适配者的提示</h2>
 * {@link #skipEntityId} 是适配**其它光影包**时的关键：
 * 不同光影包使用各自独立的 id 空间，请先确认它的 {@code entity.properties} 里
 * "跳过法线与视差"对应哪个编号，再填到这里。
 * {@code SkipCategoryResolver} 会用光影包的真实映射校验该值。
 */
public final class CompatibilityOptions {

    private static final String FILE_NAME = "yes_bsl.properties";

    /** 总开关；关闭后本模组退化为完全惰性。 */
    public static volatile boolean enabled = true;

    /**
     * 是否也作用于非玩家实体。
     *
     * <p>默认关闭：当前主要目标是玩家模型，判定最精确。
     * 为其它模组适配时可以打开它来快速验证"是否所有实体都受影响"。
     */
    public static volatile boolean affectsAllEntities = false;

    /**
     * 光影包为"不适合视差贴图的实体"预留的类别 id。
     *
     * <p>默认 {@code 10100} 对应 BSL v10 里那个标注为"跳过法线与视差"的实体类别
     * （当前包含物品展示框与画）。该值会在运行时按光影包的真实映射自动校验，
     * 不匹配则本模组自动失效。
     */
    public static volatile int skipEntityId = 10100;

    private static volatile boolean loaded;

    private CompatibilityOptions() {
    }

    /** 幂等；在首次需要读取配置时调用。任何失败都退回默认值。 */
    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;

        final Path path = configPath();
        if (!Files.isRegularFile(path)) {
            writeDefault(path);
            return;
        }

        final Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            return;
        }

        enabled = readBoolean(props, "enabled", enabled);
        affectsAllEntities = readBoolean(props, "affectsAllEntities", affectsAllEntities);
        skipEntityId = readInt(props, "skipEntityId", skipEntityId);
    }

    /**
     * 配置文件路径。
     *
     * <p>用 {@code FMLPaths.CONFIGDIR} 而不是相对的 {@code config/}：
     * 相对路径依赖"进程工作目录恰好是游戏目录"这一约定，启动器不同就不成立。
     * 交给加载器给出权威路径更稳妥。
     */
    private static Path configPath() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        } catch (Throwable t) {
            // 极早期拿不到时退回约定路径
            return Paths.get("config", FILE_NAME);
        }
    }

    private static void writeDefault(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, """
                    # YES BSL - BSL "Advanced Materials" 兼容修复

                    # 完全关闭干预（等价于卸载本模组）
                    enabled=true

                    # 是否也作用于非玩家实体（为其它模组适配时可打开验证）
                    affectsAllEntities=false

                    # 光影包为"不适合视差贴图的实体"预留的类别 id。
                    # 默认 10100 对应 BSL v10 中的该类别（含物品展示框与画）。
                    # 换光影包时请查其 entity.properties 里的对应编号再填写。
                    # 该值会在运行时按光影包真实映射自动校验；不匹配则本模组自动失效。
                    skipEntityId=10100
                    """);
        } catch (Throwable ignored) {
            // 只读环境等：保持默认值即可。
        }
    }

    private static boolean readBoolean(Properties props, String key, boolean fallback) {
        final String value = props.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int readInt(Properties props, String key, int fallback) {
        final String value = props.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
