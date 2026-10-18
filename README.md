# MiaoMenu

> 轻量级 Java 版和基岩版跨平台菜单插件，支持 Paper 26.2 / Folia / Java 25

<p align="center">
  <img alt="Maven" src="https://img.shields.io/badge/Maven-C71A36?style=plastic&logo=apachemaven&logoColor=white">
  <img alt="Java CI" src="https://github.com/Yamada0001/MiaoMenu/actions/workflows/ci.yml/badge.svg?branch=26.2&logo=apachemaven&logoColor=white">
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-FF5CAD?style=plastic&logo=openjdk&logoColor=111111&labelColor=FFFFFF">
  <img alt="Bukkit 26.2" src="https://img.shields.io/badge/Bukkit-26.2-FF8A00?style=plastic&logo=minecraft&logoColor=111111&labelColor=FFD43B">
  <img alt="Paper 26.2" src="https://img.shields.io/badge/Paper-26.2-00A3FF?style=plastic&logo=minecraft&logoColor=white&labelColor=A855F7">
  <img alt="Folia 26.2" src="https://img.shields.io/badge/Folia-26.2-FF8A00?style=plastic&logo=minecraft&logoColor=white&labelColor=22C55E">
  <a href="https://github.com/Yamada0001/VillagerRevolution/releases"><img alt="GitHub Releases downloads" src="https://img.shields.io/github/downloads/Yamada0001/VillagerRevolution/total?style=plastic&logo=github&logoColor=white&label=GitHub%20Releases&labelColor=6366F1&color=38BDF8"></a>
</p>

## 目录

- [功能特性](#功能特性)
- [环境要求](#环境要求)
- [安装说明](#安装说明)
- [快速开始](#快速开始)
- [配置指南](#配置指南)
- [命令与权限](#命令与权限)
- [菜单系统](#菜单系统)
- [需求条件系统](#需求条件系统)
- [物品解析器](#物品解析器)
- [热重载](#热重载)
- [架构文档](#架构文档)
- [开发者指南](#开发者指南)
- [CI/CD 流水线](#cicd-流水线)
- [性能优化记录](#性能优化记录)
- [变更日志](#变更日志)

---

## 功能特性

- **双平台菜单**：Java 版 GUI 背包菜单 + 基岩版 Floodgate Cumulus 表单
- **Folia 完整兼容**：通过反射检测 + 实体调度器实现无缝 Folia 支持
- **热重载**：基于 WatchService 的文件监听，修改配置后自动生效，无需重启
- **需求条件系统**：支持权限、占位符、进度、记分板、成就等多维度条件判断
- **多物品插件兼容**：CraftEngine、ItemsAdder、MMOItems、HeadDatabase、Base64 头颅
- **PlaceholderAPI 集成**：全菜单文本支持占位符解析
- **代理跨服**：BungeeCord / Velocity 跨服切换（按 `settings.proxy-mode` 显式配置）
- **安全加固**：速率限制器 + 输入验证器 + 命令安全开关
- **菜单时钟**：专属时钟物品，右键打开默认菜单
- **多语言系统**：内置 5 种语言（简体中文 / 繁體中文 / English / Tiếng Việt / 日本語），支持自定义扩展
- **bStats 统计**：匿名使用数据上报（可关闭）

---

## 环境要求

| 组件 | 最低版本 | 推荐版本 |
|------|---------|---------|
| Minecraft 服务端 | Paper 26.2 | Paper 26.2.build.60+ |
| Java | 25 (LTS) | GraalVM JDK 25 |
| Folia | 可选 | 26.1.2+ |
| Floodgate | 可选（基岩版菜单需要） | 2.2.5+ |
| PlaceholderAPI | 可选 | 2.12.2+ |

---

## 安装说明

1. 将 `MiaoMenu.jar` 放入服务端的 `plugins/` 目录
2. 启动服务端，插件会自动生成默认配置文件
3. 编辑 `plugins/MiaoMenu/config.yml` 进行个性化配置
4. 在 `plugins/MiaoMenu/java_menus/` 和 `bedrock_menus/` 中创建菜单文件
5. 执行 `/dgm reload` 或重启服务端生效

---

## 快速开始

### 创建第一个 Java 版菜单

在 `plugins/MiaoMenu/java_menus/` 中创建 `my-menu.yml`：

```yaml
menu_title: "&6&l我的菜单"
rows: 3

items:
  spawn:
    slot: 13
    material: GRASS_BLOCK
    display_name: "&a返回主城"
    lore:
      - "&7点击传送到主城出生点"
    left_click_commands:
      - "[cmd] spawn"

  close:
    slot: 22
    material: BARRIER
    display_name: "&c关闭菜单"
    left_click_commands:
      - "[close]"
```

### 创建基岩版菜单

在 `plugins/MiaoMenu/bedrock_menus/` 中创建同名文件 `my-menu.yml`：

```yaml
menu:
  title: "我的菜单"
  items:
    - text: "返回主城"
      icon: "textures/blocks/grass.png"
      icon_type: path
      command: "spawn"
      execute_as: player
    - text: "关闭菜单"
      command: "close"
      execute_as: close
```

---

## 配置指南

### config.yml 核心配置

```yaml
config-version: 26                # 配置版本，用于自动迁移（勿手动改小）
menu-version: 7                   # 示例菜单版本
settings:
  lang: "en-us"                   # 语言选择（en-us / zh-cn / zh-tw / vi-vn / ja-jp）
  default-menu: test              # 默认菜单名称（右键时钟打开）
  open-menu-sound:
    enabled: true                 # 开启菜单打开音效
    sound: entity.experience_orb.pickup
    volume: 1.0
    pitch: 1.0
  hot-reload:
    enabled: true                 # 文件变更后自动重载（含 lang/ 目录）
  auto-generate-examples: true    # 缺失时释放示例菜单与示例配置
  item-resolver:
    fallback-material: STONE      # 物品解析失败时的回退材质
  validate-commands: false        # 命令安全验证开关（仅作用于 [cmd]/[player]）
  proxy-mode: NONE                # NONE / BUNGEE / VELOCITY
  rate-limit:
    window-millis: 2000           # 交互限流窗口长度
    max-events: 10                # 窗口内允许的点击次数
  menu-clock:
    enabled: true                 # 菜单时钟开关
    give-on-join: true            # 入服自动给时钟
```

> **配置迁移**：当 `config-version` 与插件内置版本不一致时，插件会先备份现有
> `config.yml`（生成 `config.yml.<时间戳>.bak`），然后**只补齐缺失的键**，
> 不会覆盖你已经改过的值。

### 多语言系统 (i18n)

所有玩家可见的文本消息均通过独立的语言文件管理，位于 `plugins/MiaoMenu/lang/` 目录下。首次启动时插件会自动从 JAR 中释放语言文件。

#### 内置语言

| 语言代码 | 语言    | 文件名 |
|---------|-------|--------|
| `en-us` | English  | `en-us.yml` |
| `zh-cn` | 简体中文  | `zh-cn.yml` |
| `zh-tw` | 繁體中文  | `zh-tw.yml` |
| `vi-vn` | Tiếng Việt | `vi-vn.yml` |
| `ja-jp` | 日本語   | `ja-jp.yml` |

#### 切换语言

在 `config.yml` 中修改一行即可：

```yaml
settings:
  lang: "zh-cn"   # 可选: en-us, zh-cn, zh-tw, vi-vn, ja-jp
```

#### 自定义与扩展

- **修改文本**：直接编辑 `lang/` 目录下对应的 `.yml` 文件，保存后热重载自动生效
- **新增语言**：复制任意语言文件并重命名（如 `ko-kr.yml`），再把文件名（不含 `.yml`）加入
  插件支持列表后即可使用——当前内置列表为 `en-us` / `zh-cn` / `zh-tw` / `vi-vn` / `ja-jp`，
  其它值会告警并回退到 `en-us`
- **颜色代码**：所有文本支持 `&` 颜色格式（`&c` 红色、`&a` 绿色、`&6` 金色等）
- **缺失回退**：当某条 key 在当前语言文件中不存在时，自动回退至内置英文 `en-us`；
  仍然缺失时会在控制台输出一条 `Missing language key` 告警，并把键名原样显示给玩家
- **参数占位符**：`{0}`、`{1}` 等为程序运行时替换的动态参数，翻译时必须原样保留

---

## 命令与权限

### 命令

| 命令 | 别名 | 权限 | 说明 |
|------|------|------|------|
| `/dgeysermenu open <菜单名>` | `/dgm open` | `dgeysermenu.use` | 打开指定菜单 |
| `/dgeysermenu reload` | `/dgm reload` | `dgeysermenu.reload` | 重载插件配置 |
| `/dgeysermenu help` | `/dgm help` | `dgeysermenu.use` | 显示帮助 |
| `/getmenuclock` | - | `dgeysermenu.admin` | 获取菜单时钟 |

### 权限树

```
dgeysermenu.*          (所有权限)
  ├── dgeysermenu.use  (default: true)
  ├── dgeysermenu.admin (default: op)
  └── dgeysermenu.reload (default: op)
```

---

## 菜单系统

### 动作前缀

菜单物品的点击命令使用前缀语法：

| 前缀 | 说明 | 示例 |
|------|------|------|
| `[player]` | 以玩家身份执行 | `[player] spawn` |
| `[cmd]` | 以控制台身份执行 | `[cmd] give %player% diamond 1` |
| `[message]` | 发送消息给玩家 | `[message] &a你好！` |
| `[close]` | 关闭菜单 | `[close]` |
| `[menu]` | 打开另一个菜单 | `[menu] settings` |
| `[var_set]` | 写入变量 | `[var_set] player:visits 1` |
| `[var_add]` | 变量数值累加 | `[var_add] player:visits 1` |
| `[var_remove]` | 删除变量 | `[var_remove] player:visits` |
| 无前缀 | 等同 `[player]` | `spawn` |

说明：

- **`[cmd]` 的控制台权限**：任何能点击该按钮的玩家都会获得该命令的完整控制台权限，
  不要把玩家可控占位符（如 `%some_nickname%`）拼进 `[cmd]` 命令。
- **Folia 时序**：Folia 上 `[cmd]` 会投递到全局区域线程执行，比同一动作链中的其它动作
  延后约一个 tick；如果后续动作依赖命令的结果，请拆到下一次点击。
- **前缀写错不会静默执行**：出现未注册的前缀（如 `[console]`）时，插件会输出一条
  `Unknown action prefix` 告警并跳过该动作，而不是降级成玩家命令。
- **变量作用域**：`player:<key>` 表示当前玩家变量，`global:<key>` 表示全局变量；
  变量可在文本中用 `%miao_player_<key>%` / `%miao_global_<key>%` 显示（需 PlaceholderAPI）。

---

## 需求条件系统

### 条件类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `permission` | 权限检查 | `type: permission`, `permission: vip.use` |
| `placeholder_equals` | 占位符等于（两侧去空白后比较） | `type: placeholder_equals`, `placeholder: "%server_online%"`, `value: "1"` |
| `placeholder_not_equals` | 占位符不等于（也可写 `placeholder-not-equals`） | `type: placeholder_not_equals`, `placeholder: "%server_online%"`, `value: "0"` |
| `placeholder_contains` | 占位符包含（`value` 不能为空） | `type: placeholder_contains`, `placeholder: "%player_world%"`, `value: "world"` |
| `advancement` | 成就已完成 | `type: advancement`, `advancement: minecraft:story/mine_diamond` |
| `progress` | 记分板分数达到阈值（不是百分比） | `type: progress`, `objective: playtime`, `value: 50` |
| `score_gte` | 记分板大于等于 | `type: score_gte`, `objective: kills`, `value: 100` |
| `score_lte` | 记分板小于等于 | `type: score_lte`, `objective: deaths`, `value: 10` |
| `score_equals` | 记分板等于 | `type: score_equals`, `objective: level`, `value: 50` |
| `score_range` | 记分板范围（闭区间） | `type: score_range`, `objective: level`, `min: 10`, `max: 99` |
| `block` | 引用命名条件块 | `type: block`, `block: vip_check` |

失败语义（重要）：

- **占位符未解析即视为不满足**：未安装 PlaceholderAPI、expansion 缺失或占位符拼错时，
  条件一律判定为失败，不会因为"字符串比较不相等"而误放行。
- **从未设置过分数的玩家视为不满足**：`score_lte` / `score_range` 不会把"没有分数"
  当成 0 分，避免新玩家意外通过上限类条件。
- **配置写错一律 fail-closed**：条件组里只要有一条 requirement 非法（类型未知、缺必填参数、
  引用了不存在的块），整组直接判定为不满足，并在控制台输出一次告警。
- **空条件组**：`AND` 空组视为"无条件限制"（放行）；`OR` 空组没有任何可满足分支，判定为不满足。
- **自定义提示**：每条 requirement 支持 `deny_message`（别名 `lock_message`）与 `fallback_menu`，
  命名条件块也支持这两个键；物品级 `conditions` 与菜单级 `view_requirement` 都会使用它们。

### 条件组 (AND / OR)

```yaml
conditions:
  operator: AND
  requirements:
    - type: permission
      permission: vip.use
  children:
    - operator: OR
      requirements:
        - type: placeholder_equals
          placeholder: "%server_online%"
          value: "1"
        - type: score_gte
          objective: kills
          value: 100
```

### 命名条件块 (requirement_blocks)

```yaml
requirement_blocks:
  vip_check:
    requirements:
      - type: permission
        permission: vip.use
      - type: score_gte
        objective: playtime
        value: 3600

items:
  special:
    conditions:
      requirements:
        - type: block
          block: vip_check
```

---

## 物品解析器

MiaoMenu 支持多种自定义物品格式，按优先级依次尝试：

| 格式 | 前缀 | 示例 | 需要插件 |
|------|------|------|---------|
| CraftEngine | `craftengine:` | `craftengine:namespace:custom_sword` | CraftEngine |
| ItemsAdder | `itemsadder:` | `itemsadder:namespace:ruby_sword` | ItemsAdder |
| MMOItems | `mmoitems:` | `mmoitems:SWORD:RUBY_BLADE` | MMOItems |
| HeadDatabase | `headdb:` | `headdb:12345` | HeadDatabase |
| Base64 头颅 | `base64head:` | `base64head:eyJ0ZXh0dXJlcyI6...` | 无 |
| 原版 | 无前缀 | `DIAMOND_SWORD` | 无 |

`base64head:` 使用 Mojang textures 属性的 Base64 JSON，并只接受 `textures.minecraft.net` 的皮肤 URL；64 位纹理哈希仍作为兼容输入支持。

解析失败时的行为：

- **回退材质**：任何前缀解析失败（插件未安装、ID 拼错、Base64 非法）都会回退到
  `settings.item-resolver.fallback-material`（默认 `STONE`），并在控制台**输出一次**告警，
  不再静默失败。
- **未知前缀**：写成 `item:xxx`、`custom:xxx` 这类未知前缀时同样告警并回退，
  不会被当作原版材质悄悄吞掉。
- **CraftEngine 版本差异**：`buildItemStack` / `buildBukkitItem` 两种方法名都会被尝试，
  不带命名空间的 ID 会按 CraftEngine 默认命名空间解析。

---

## 热重载

插件使用 `WatchService` 守护线程监听以下目录/文件：

- `config.yml`
- `java_menus/*.yml`
- `bedrock_menus/*.yml`
- `dialog_menus/*.yml`
- `lang/*.yml`

变更后采用**尾随防抖**：只有最后一次改动之后静默 500ms 才会真正重载，避免读到写了一半的文件。
`commands.yml` / `tasks.yml` / `storage.yml` 的变更不会触发菜单重载。也可使用 `/dgm reload`
手动重载——手动重载与文件热重载走同一条链路，结果一致。

重载是容错的：单个菜单文件解析失败时保留它此前加载的实例；目录整体为空或全部文件失败时
保留现有注册表，不会把全服菜单清空。

---

## 架构文档

### 核心模块

```
MiaoMenu (主类)
├── ConfigManager          # 配置加载与版本迁移
├── JavaMenuManager        # Java 版菜单注册表 (volatile ConcurrentHashMap)
├── BedrockMenuManager     # 基岩版菜单注册表 + Floodgate 反射桥
├── RequirementService     # 需求条件评估引擎
├── ActionRegistry         # 动作前缀分发器
├── ItemResolver           # 多源物品解析器 (反射缓存)
├── MenuClockManager       # 菜单时钟物品管理
├── HotReloadManager       # 文件监听热重载 (WatchService 守护线程)
├── ProxyManager           # BungeeCord/Velocity 代理
├── RateLimiter            # 交互速率限制器 (滑动窗口)
└── FoliaFactory           # Folia/Bukkit 调度器适配
```

### 线程模型

| 组件 | 线程 | 同步策略 |
|------|------|---------|
| 菜单注册表 | 主线程 (Bukkit) | `volatile Map` + 全量替换 |
| 热重载监听 | 守护线程 (WatchService) | `volatile` + 调度回主线程 |
| 速率限制器 | 主线程 | `ConcurrentHashMap.compute()` |
| Folia 调度 | 区域线程 | `GlobalRegionScheduler` / `EntityScheduler` |

### Folia 兼容机制

```
FoliaFactory.isFolia()
    ├── true  → FoliaSchedulerAdapter (Entity.getScheduler().runDelayed)
    └── false → BukkitSchedulerAdapter (BukkitScheduler.runTaskLater)

检测方式：Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
```

---

## 开发者指南

### 项目结构

```
MiaoMenu/
├── src/main/java/com/fluxcraft/MiaoMenu/
│   ├── MiaoMenu.java              # 主类 (生命周期管理)
│   ├── javamenu/                  # Java 版菜单系统
│   │   ├── JavaMenu.java          # 菜单模型 + InventoryHolder
│   │   ├── JavaMenuManager.java   # 菜单注册表
│   │   └── JavaMenuListener.java  # 点击/拖拽事件处理
│   ├── bedrockmenu/               # 基岩版菜单系统
│   │   ├── BedrockMenu.java       # Cumulus 表单构建
│   │   └── BedrockMenuManager.java # 表单注册 + 反射桥
│   ├── menu/
│   │   ├── action/                # 动作系统
│   │   │   ├── ActionRegistry.java
│   │   │   └── impl/              # player/cmd/message/close/menu
│   │   └── requirement/           # 需求条件系统
│   │       ├── RequirementService.java
│   │       ├── ConditionGroup.java
│   │       └── RequirementResult.java
│   ├── managers/                  # 管理器
│   │   ├── HotReloadManager.java
│   │   ├── MenuClockManager.java
│   │   └── SoundsClock.java
│   ├── integration/               # 第三方集成
│   │   └── ItemResolver.java
│   ├── foliacall/                 # Folia 适配层
│   │   ├── FoliaAdapter.java
│   │   └── FoliaFactory.java
│   ├── security/                  # 安全
│   │   ├── RateLimiter.java
│   │   └── InputValidator.java
│   ├── listeners/                 # 事件监听器
│   ├── commands/                  # 命令系统
│   ├── proxy/                     # 代理跨服
│   ├── config/                    # 配置管理
│   ├── constants/                 # 常量
│   └── utils/                     # 工具类
├── src/main/resources/
│   ├── plugin.yml
│   ├── config.yml
│   ├── lang/                      # 多语言文件（en-us, zh-cn, zh-tw, vi-vn, ja-jp）
│   ├── java_menus/                # Java 版示例菜单
│   ├── bedrock_menus/             # 基岩版示例菜单
│   ├── dialog_menus/              # Dialog 菜单示例
│   ├── commands.yml               # 自定义命令示例
│   └── tasks.yml                  # 周期任务示例
├── src/test/java/                 # 单元测试
├── .github/workflows/ci.yml       # CI 流水线
└── pom.xml                        # Maven 构建配置
```

### 构建方式

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 打包 (含 shade)
mvn package

# 完整验证
mvn verify
```

### 测试

```bash
mvn test
# 53 个单元测试，12 个测试类：
#   - RequirementServiceTest (18)   条件引擎：AND/OR、嵌套、fail-closed、数值边界
#   - RateLimiterTest (5)           滑动窗口与清理
#   - ProxyManagerTest (5)          代理模式解析与消息格式
#   - DialogMenuConfigTest (6)      Dialog 配置解析与校验
#   - MenuTextParserTest (5)        文本/字形解析
#   - ItemResolverTest (3)          纹理 URL 白名单
#   - VariableStoreTest (2)         变量读写与占位符
#   - InputValidatorTest (2)        菜单名与命令内容校验
#   - BedrockMenuConfigTest (2)     基岩菜单样例
#   - ConfigManagerExampleTest (2)  示例释放策略
#   - JavaMenuModeTest (2)          渲染模式枚举
#   - CustomCommandManagerTest (1)  自定义命令参数替换
```

---

## CI/CD 流水线

### GitHub Actions 配置

```yaml
触发条件: push/PR 到 main/master/dev 分支
权限模型: contents: read (最小权限)
并发控制: cancel-in-progress: true
构建步骤: mvn -B verify (测试+打包一步完成)
产物上传: target/*.jar → Actions Artifact
依赖缓存: maven cache enabled
```

### Dependabot

- Maven 依赖：每周检查更新
- GitHub Actions：每周检查更新
- 最大同时打开 PR：5

---

## 性能优化记录

以下优化已全部应用并通过测试验证：

### 反射缓存

| 组件 | 优化前 | 优化后 |
|------|--------|--------|
| Floodgate API | 每次调用 `getMethod()` | 缓存 `Method` 为 `volatile` 字段 |
| Cumulus Form | 每次构建表单 `Class.forName()` | 静态初始化缓存 Class + 枚举常量 |
| CraftEngine/ItemsAdder/MMOItems/HeadDB | 每次解析 `Class.forName()` | 静态懒加载缓存 Class |

### 热路径优化

| 组件 | 优化前 | 优化后 |
|------|--------|--------|
| PlaceholderUtils | 每次 `PluginManager.isPluginEnabled()` | 缓存 `volatile boolean` |
| MiaoMenu.isBedrockPlayer | 每次 `PluginManager.getPlugin()` | `onEnable` 时缓存 `volatile boolean` |
| JavaMenu.createUnlockedItemStack | 无条件 `Material.matchMaterial()` | 延迟到 else 分支 |
| Lang.get() | 完整序列化/反序列化往返 | 使用缓存的 `static final` 序列化器 |
| PlayerAction | `String.split("\\s+")` 每次编译正则 | 预编译 `static final Pattern` |

### 内存优化

| 组件 | 优化前 | 优化后 |
|------|--------|--------|
| HotReloadManager | `lastMenuReloadTimes` 无界增长 | `LinkedHashMap` 限制 32 条目 |

### 线程安全

| 组件 | 优化前 | 优化后 |
|------|--------|--------|
| ItemResolver 缓存 Class | 非 `volatile` | 全部 `volatile` |
| BedrockMenu 缓存 Class | 非 `volatile` | 全部 `volatile` |
| MiaoMenu Floodgate 字段 | 非 `volatile` | 全部 `volatile` |

---

## 变更日志

### v3.1

修复全面审查中发现的问题：

- **修复**：`dialogmenu` 子系统缺失类导致整个项目无法编译（补齐 `Button` / `Body` / `Input` /
  `DialogFZ` / `DialogYaml` / `DialogInter` / `DialogDTLB`），并真正接入 `openSmartMenu`
- **修复**：条件系统多处 fail-open——空 `OR` 组、无条件的命名条件块、未解析的占位符、
  `placeholder_contains` 空 `value`、从未设置过的记分板分数都会被误判为"满足"
- **修复**：条件失败时的 `deny_message` / `fallback_menu` 在物品级 `conditions` 路径被丢弃
- **修复**：数值阈值溢出与小数静默截断（`intValue()` → 范围校验）
- **修复**：锁定物品名称/lore 显示字面 `&7`/`&8`/`&c`
- **修复**：基岩版表单标题与按钮未去色，玩家看到字面 `§6§l`
- **修复**：基岩版 `[close]` 无效（改用 Floodgate `closeForm`）
- **修复**：基岩版表单回调未切回玩家区域线程；未知 `execute_as` 静默降级
- **修复**：`ItemResolver` / `BedrockMenu` 反射初始化"先置标志后赋值"，一次失败永久失效
- **修复**：限流只覆盖 Java 背包点击，基岩表单回调与自定义命令完全无限流
- **修复**：限流实现由固定窗口改为真正的滑动窗口，并改用 `nanoTime` 避免时钟回拨
- **修复**：`validate-commands` 会误杀中文与 `[close]`（空内容）
- **修复**：未知动作前缀静默降级为"以玩家身份执行命令"
- **修复**：`var_set` / `var_add` / `var_remove` 未注册
- **修复**：热重载防抖会丢弃尾随事件、监听范围过宽、`key.reset()` 异常后目录永久失联
- **修复**：菜单注册表在文件损坏/目录清空时把全服菜单清空
- **修复**：配置版本迁移会用默认配置整体覆盖用户设置（改为键级合并 + 备份）
- **修复**：变量持久化未接线（`load()`/`flush()` 从未调用），落盘失败静默
- **修复**：跨服失败无提示并回落成"未知命令"
- **修复**：`/dgm reload` 与文件热重载行为不一致
- **新增**：`[menu] java:<名>` / `bedrock:<名>` / `dialog:<名>` 前缀；不带前缀时自动回退
- **新增**：`settings.rate-limit` 配置项
- **新增**：`PlaceholderAPI` 扩展 `%miao_player_<key>%` / `%miao_global_<key>%`
- **新增**：`commands.yml`、`tasks.yml`、`dialog_menus/test.yml` 首次启动自动释放
- **改进**：默认中文文案改为可操作表述，五套语言文件补齐全部键
- **改进**：`/dgm help` 按权限过滤，Tab 补全不再泄露无权限子命令与菜单名

### v3.0 (Paper 26.2)

- **BREAKING**: 升级 Paper API 至 26.2.build.60-beta
- **BREAKING**: Java 版本要求提升至 25
- **BREAKING**: `api-version` 更新为 `'26.2'`
- 移除未使用的 `folia-api` 依赖
- 升级 FoliaLib 至 1.3.2
- 升级 Mockito 至 5.18.0 (Java 25 支持)
- 全面性能优化：反射缓存、热路径优化、内存修复
- 修复基岩版 requirement_blocks 条件丢失 Bug
- 修复 Folia 死亡掉落菜单时钟缺失 Bug
- i18n 合规：所有硬编码中文提取为语言条目
- **多语言系统**：内置 5 种语言文件（en-us / zh-cn / zh-tw / vi-vn / ja-jp），通过 `config.yml` 的 `settings.lang` 切换，支持热重载和缺失回退
- CI/CD 完善：最小权限、产物上传、并发控制、Dependabot
