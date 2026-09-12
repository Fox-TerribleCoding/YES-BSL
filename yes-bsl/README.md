# YES BSL

**独立的兼容修复模组**，解决 **BSL Shaders** 开启 **Advanced Materials** 后，
某些由 native / 自定义渲染管线写入顶点的模组模型出现 **半透明闪烁** 的问题。

当前针对 [Yes Steve Model (YSM)](https://modrinth.com/mod/yes-steve-model) 验证，
覆盖**玩家模型**与**第一人称手模**。但**代码结构是通用的**——
任何遇到同类问题的模组都可以通过少量改动复用本方案，详见
[「为其它模组适配」](#为其它模组适配)。

- 不修改 BSL 光影包的任何文件
- 不修改目标模组的任何文件
- 不依赖反射，全部通过编译期 Mixin 注入完成
- 实测性能开销在 1 FPS 的浮动误差范围内

**以 [MIT](LICENSE) 协议开源** —— 可自由使用、修改、分发，仅需保留原作者署名。

---

## 问题原理

理解这一节是后续适配的前提。

### BSL 的视差贴图会改写采样坐标

BSL 在 `ADVANCED_MATERIALS` 下启用视差贴图（POM），并且——**这一点才是问题的关键——
它用 POM 求得的坐标去采样模型贴图本身**。其效果可以概括为：

```
若 未命中"跳过视差"标记：
    偏移坐标 = 视差计算(原始纹理坐标, ...)
    模型颜色 = 读取模型贴图(偏移坐标)     ← 不再是原始纹理坐标
```

`newCoord` 由 `vTexCoord` / `vTexCoordAM` 推导，而这两个 varying 又来自
OptiFine/Iris 对 `mc_midTexCoord` 与 `at_tangent` 的语义约定（quad 内 UV 的仿射还原）。

### 两类几何体的差别

| 几何体来源 | 顶点由谁写入 | 扩展属性是否正确 | 结果 |
|---|---|---|---|
| MC 标准模型（方块、原版生物、**手持物品**） | `BufferBuilder` | ✅ Iris 的 `MixinBufferBuilder` 每 4 个顶点计算一次，正确填充 | POM 映射成立，正常 |
| YSM 等模组模型 | **native 直接按 `IrisVertexFormats.ENTITY` 写内存** | ❌ 取值不满足该映射前提 | `newCoord` 偏离真实 `texCoord` |

于是 `albedo` 采样到贴图**错误的区域**——这类模型贴图布局紧凑，大量空白像素：
**空白像素的 alpha 被 alpha test 丢弃 ⇒ 看起来半透明；偏移量随视角/距离每帧变化 ⇒ 闪烁。**

### 一个现成的对照实验

BSL 为**地图**这个物品单独预留了一个类别（id 为 `400`）——因为地图是平面贴图，
同样经不起 POM 的坐标改写。

**于是就有了一个现成的对照：手持地图时手臂完全正常，空手时手模闪烁**，
而两者显示的是同一份 YSM 手模。唯一差异就是这个 id——
这直接证实了 POM 是元凶。

### 两条完全独立的路径

这是本项目最容易踩坑的地方：**同一个几何体在不同 program 里，`skipParallax` 的判据完全不同**。

| 渲染对象 | program | 判据 | 对应的光影包逃生舱 |
|---|---|---|---|
| 玩家模型（第三人称等） | `gbuffers_entities` | `entityId == 10100` | 实体侧的"跳过法线与视差"类别 |
| 第一人称手模 | `gbuffers_hand` | `heldItemId / 100 == 4` | 物品侧的"地图"类别（id `400`） |

> 曾经以为手模也看 `entityId`，结果覆盖了却毫无效果——它读的根本不是同一个变量。

---

## 修复方式

一句话：**在渲染目标几何体期间，让 Iris 对外报告光影包的 Skip 类别值。**

```
玩家模型：  EntityRenderDispatcher.render ──HEAD──▶ 建立上下文（entityId = 10100）
            CapturedRenderingState.getCurrentRenderedEntity ──HEAD──▶ 命中时返回 10100

第一人称手模：IdMapUniforms$HeldItemSupplier.getIntID ──HEAD──▶ 空手时返回 400
```

### 为什么是"读取时覆盖"而不是"写入时改写"

Iris 也在同一位置写 `currentEntity`。若我们也去改写，谁先谁后取决于 Mixin 的应用顺序，
不可靠。改为在**读取**时覆盖则与顺序无关——读取发生在渲染过程中，
此时上下文恰好指向被渲染的对象。

### 为什么不会误伤

**玩家模型侧**：不硬编码盲信 `10100`，而是运行时读取该光影包**真实的**映射表：

```java
WorldRenderingSettings.INSTANCE.getEntityIds()
        .applyAsInt(new NamespacedId("minecraft", "item_frame"))
```

只有当它恰好等于配置值（即这个光影包确实遵循 BSL 这套约定）时修复才生效，
否则整体失效。**换成别的光影包会自动不生效。**

**手模侧**：只在**空手**时伪造，持有物品时保持真值。且全 BSL 中 `heldItemId`
只有三处真正被使用，空手时伪造 `400` 与真值 `0` 完全等价：

| 使用点 | 空手 (0) | 伪造 (400) |
|---|---|---|
| `gbuffers_hand` `skipParallax`: `heldId == 4` | 0 | **1** ← 目标 |
| `gbuffers_hand` `GetHandItem(1/2/3/50/89/213)` | 全 0 | 全 0（4 不命中任何一项） |
| `coloredBlocklight` 彩色手光: `heldItemId % 100` ∈ 1–50 | `0%100=0` | `400%100=0`（同为无色） |

最后一行得益于 BSL 的 `ABB` 编号格式——`400` 的彩色光数据位恰好是 `00`。
**这也是 BSL 选 `400` 当地图类目的原因。**

---

## 性能

`CapturedRenderingState.getCurrentRenderedEntity()` 会**按顶点**被调用
（Iris 写 `iris_Entity` 时，以及目标模组的 native 写入器），因此热路径被压到最省：

```java
final int overrideId = EntityIdOverride.activeOverrideId;   // 一次 volatile int 读
if (overrideId >= 0) { cir.setReturnValue(overrideId); }
```

- 不参与渲染时，代价仅为一次 volatile 读（绝大多数绘制：方块、生物、粒子、GUI）
- 上下文栈用 `ThreadLocal`，但只在 push/pop（每个实体一次）访问，不进入热路径
- 无反射

实测与未安装时相差约 1 FPS，属浮动误差范围。

---

## 安装

把 `YES_BSL-1.0.0.jar` 放进 `.minecraft/mods/`
（版本隔离时是 `versions/<版本名>/mods/`）。

**依赖**（均为可选，缺失时对应部分自动失效，不会崩溃）：

| 模组 | 版本 | 说明 |
|---|---|---|
| [Iris](https://modrinth.com/mod/iris) | 1.8+ | 必需，否则本模组无意义 |
| [Yes Steve Model](https://modrinth.com/mod/yes-steve-model) | 2.x | 当前针对它验证 |

启动后日志中应出现：

```
[YES-BSL] 已就绪：渲染目标模型时会把 iris 报告的 id 映射到光影包预留的 Skip 类别（entityId=10100 / heldItemId=400）。
```

首次启动会在游戏目录生成 `config/yes_bsl.properties`：

```properties
# 完全关闭干预（等价于卸载本模组）
enabled=true

# 是否也作用于非玩家实体（为其它模组适配时可打开验证）
affectsAllEntities=false

# 光影包为"不适合视差贴图的实体"预留的类别 id
skipEntityId=10100
```

---

## 为其它模组适配

骨架与具体模组解耦，适配新目标通常只需要改**一处判定**。

### 情况一：目标模组的问题出在 `entityId` 路径

即它的模型走 `gbuffers_entities`。只需让
`TargetDetector#isTargetEntity` 认识你的目标实体：

```java
// src/main/java/dev/yesbsl/TargetDetector.java
public static boolean isTargetEntity(Entity entity) {
    return entity instanceof Player;        // ← 改成你的目标类型
}
```

其余（上下文建立、id 覆盖、光影包约定校验）全部现成可用。

### 情况二：目标模组的问题出在 `heldItemId` 路径

即它渲染的是第一人称手部 / 手持物。参考 `HandMaterialOverride`，
它的职责是回答"此刻是否该谎报手持物品 id"：

```java
// src/main/java/dev/yesbsl/HandMaterialOverride.java
public static boolean shouldReportHeldItemId(InteractionHand hand) {
    // ← 把判定换成你的触发条件
}
```

### 情况三：换了别的光影包

不同光影包使用**各自独立**的 id 空间。请先确认它的
`entity.properties` / `item.properties` 里"跳过法线与视差"对应哪个编号，
再填进配置的 `skipEntityId`。

**务必保留 `SkipCategoryResolver` 的"实际映射校验"逻辑**：
它保证只有当光影包真的把探针实体映射到该编号时修复才生效。
去掉这一步就可能误伤使用其它 id 约定的光影包。

### 关键约定

1. **先做对照实验再动手。** 找一个"同类几何体但表现正常"的场景
   （本项目里是手持地图 vs 空手），它能直接锁定根因，比读代码快得多。
2. **确认目标走的是哪个 program。** `gbuffers_entities` 与 `gbuffers_hand`
   的判据完全不同，选错变量会"改了但没效果"。
3. **优先复用光影包自己的逃生舱。** BSL 已为不适合 POM 的几何体预留了类别，
   引导进去比修改 shader 更干净，也不涉及光影包的许可问题。

---

## 从源码构建

无需 Gradle、无需联网——只要本机存在游戏目录（自带官方映射的 Minecraft、
NeoForge、Sponge Mixin 与 `mods/` 下的 Iris）即可：

```powershell
.\build.ps1 -Clean                 # 默认目标版本目录为 T
.\build.ps1 -Clean -GameVersion X  # 指定其它版本目录
```

构建产物位于 `build\`，同时会发布一份到工程上一级目录方便取用。

### 工程结构

```
src/main/java/dev/yesbsl/
├── YesBsl.java                     # mod 入口（无修复逻辑）
├── CompatibilityOptions.java       # 配置读写
├── TargetDetector.java             # ★ 判定"哪些对象需要修复"
├── EntityIdOverride.java           # 实体 id 覆盖上下文（含按顶点热路径）
├── HandMaterialOverride.java       # ★ 手模：是否该谎报 heldItemId
├── iris/
│   ├── SkipCategoryResolver.java   # Skip id 解析 + 光影包约定校验
│   └── IrisIdBridge.java           # 唯一接触 Iris 类型的隔离层
└── mixin/
    ├── MixinEntityRenderDispatcher.java      # 建立实体上下文
    └── iris/
        ├── MixinCapturedRenderingState.java  # 覆盖 entityId（玩家模型）
        └── MixinHeldItemSupplier.java        # 谎报 heldItemId（手模）
```

标 ★ 的两个文件是适配新模组时的主要改动点。

---

## 许可

**本项目以 [MIT](LICENSE) 协议开源** —— 可自由使用、修改、分发，
**仅需保留原作者署名**。

### 与第三方的关系

本项目**不包含**任何第三方的代码或资源，与下列项目**无隶属关系**，
也不代表它们的立场：

| 项目 | 协议 | 本项目的关系 |
|---|---|---|
| [BSL Shaders](https://modrinth.com/shader/bsl-shaders) | LicenseRef-All-Rights-Reserved（**非开源**） | 不含、不修改、不再分发其任何代码或资源；仅在运行时调整 Iris 对外报告的 id |
| [Iris](https://github.com/IrisShaders/Iris) | LGPL-3.0 | 仅通过 `@Mixin(targets=...)` 引用其公开类名，**未复制其代码** |
| [Yes Steve Model](https://modrinth.com/mod/yes-steve-model) | All-Rights-Reserved | 不反编译、不修改、不内嵌；不调用其混淆内部 API，仅判断 mod 是否加载 |

> MIT 只覆盖本项目自己的代码。若你要基于本项目二次开发并分发，
> 上述三方的权利与义务仍需你自行确认。

### 致谢

- **Capt Tatsu** —— BSL Shaders 作者。本项目完全依赖他在
  `entity.properties` / `item.properties` 中预留的"跳过法线与视差"类别，
  以及 `400` 这个精心设计的编号（彩色光数据位为 `00`，使伪造它不产生副作用）。
- **Iris 团队** —— 提供了规范的 Mixin 注入点与清晰的 id 映射 API。
