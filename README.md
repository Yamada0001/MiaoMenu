# MiaoMenu

> 一个给 Paper 服务端用的菜单插件：Java 玩家开背包 GUI，基岩玩家看 Floodgate 表单，想要新一点的效果就用 Paper 原生 Dialog。

<p align="center">
  <a href="https://github.com/Yamada0001/MiaoMenu/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/Yamada0001/MiaoMenu/actions/workflows/ci.yml/badge.svg?branch=26.2&logo=github"></a>
  <a href="https://github.com/Yamada0001/MiaoMenu/releases"><img alt="Release" src="https://img.shields.io/github/v/release/Yamada0001/MiaoMenu?style=flat-square&label=release"></a>
  <a href="https://github.com/Yamada0001/MiaoMenu/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/Yamada0001/MiaoMenu/total?style=flat-square&label=downloads"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/Yamada0001/MiaoMenu?style=flat-square&label=license"></a>
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white">
  <img alt="Paper 26.2" src="https://img.shields.io/badge/Paper-26.2-22272E?style=flat-square">
  <img alt="Folia" src="https://img.shields.io/badge/Folia-supported-3DA639?style=flat-square">
</p>

[功能](#功能) · [截图](#截图) · [安装](#安装) · [快速开始](#快速开始) · [命令](#命令与权限) · [开发](#开发) · [已知限制](#已知限制)

## 为什么会有这个插件

我最初只是想解决一个很具体的问题：服务器里同时有 Java 和基岩玩家，Java 玩家点开箱子菜单，基岩玩家什么都点不到。

常见的做法是装两套插件，或者干脆放弃基岩端的菜单。MiaoMenu 的选择是：一套插件，三种渲染方式，按玩家类型自动选。

- **Java 版**：普通背包 GUI，配置在 `java_menus/`；
- **基岩版**：Floodgate 的 Cumulus 表单，配置在 `bedrock_menus/`；
- **原生 Dialog**：Paper 自带的 Dialog API，配置在 `dialog_menus/`，支持输入框、单选、动态按钮列表。

三套菜单的写法不一样，但底下的东西是共用的：条件判断、动作前缀、变量和热重载。条件组和动作串在三套菜单里完全一致，写熟一套就能迁移到另外两套。

另一个贯穿整个项目的取舍是「配置写错时宁可吵一点」。占位符没解析、记分板没分数、引用了不存在的条件块，这些情况默认按「不满足」处理，并在控制台留下告警，而不是悄悄放行或者静默失败。

## 功能

- **三种渲染 + 智能路由**：`/dgm open <名字>` 按玩家类型选渲染方式；也可以写 `java:名字`、`bedrock:名字`、`dialog:名字` 强制指定。
- **条件系统**：权限、PlaceholderAPI 占位符、成就、记分板分数，支持 AND / OR 嵌套和可复用的命名条件块。
- **动作前缀**：`[player]` `[cmd]` `[message]` `[close]` `[menu]` `[var_set]` `[var_add]` `[var_remove]`。前缀写错会在控制台告警并跳过，不会悄悄降级成玩家命令。
- **物品来源**：CraftEngine、ItemsAdder、MMOItems、HeadDatabase、Base64 头颅、主题图片、原版材质；解析失败回退到配置的材质。
- **变量**：玩家 / 全局两个作用域，落盘到 `storage.yml`，并注册了 `%miao_player_<key>%` 和 `%miao_global_<key>%` 两个 PAPI 占位符。
- **热重载**：`WatchService` 监听配置、三套菜单目录和 `lang/`，保存后 500ms 防抖自动重载。
- **菜单时钟**：入服自动发一个菜单时钟（默认叫「时光之镜」），右键打开默认菜单，可以在配置里关掉。
- **自定义命令 / 周期任务**：`commands.yml` 和 `tasks.yml` 里用同一套动作串配置。
- **主题下载**：`/dgm download` 从主题仓库拉取菜单和贴图，可选的 sha256 校验，只允许写进指定目录。
- **资源包下发**：进服自动推送资源包，可选 SHA-1 校验和强制接受。
- **更新检查**：异步查询 GitHub Release，只提示，不自动下载替换。
- **安全**：Java 点击和基岩表单回调共用滑动窗口限流；`validate-commands` 可选开启命令内容校验。
- **多语言**：内置 en-us / zh-cn / zh-tw / vi-vn / ja-jp，缺失的键回退到英文。
- **Folia**：`folia-supported: true`，调度走 FoliaLib，需要主线程的操作会切回对应线程。
- **公开 API**：通过 Bukkit `ServicesManager` 拿 `MiaoMenuApi`，可以打开菜单、读写变量、注册自定义动作前缀和动态列表数据源。

## 截图

<p align="center">
  <img src="pic/1.png" alt="Java 版背包菜单与物品提示" width="48%">
  <img src="pic/2.png" alt="基岩版 Floodgate 表单" width="48%">
</p>

<p align="center"><sub>左：Java 版背包菜单与物品提示　右：基岩版 Floodgate 表单（自定义图标）</sub></p>

## 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| Paper | 26.2（`api-version: '26.2'`） | 必须。编译时对接 `26.2.build.60-beta` |
| Java | 25 | 必须 |
| Folia | 26.2 | 可选，插件标记了 `folia-supported` |
| Floodgate | 2.2.5+ | 可选，基岩版表单需要 |
| PlaceholderAPI | 2.12.3+ | 可选，占位符和条件里的 PAPI 变量需要 |
| CraftEngine | 0.0.67+ | 可选，物品来源之一 |

ItemsAdder、MMOItems、HeadDatabase、Oraxen 没有锁定版本，都是运行时反射调用。

## 安装

1. 从 [Releases](https://github.com/Yamada0001/MiaoMenu/releases) 下载最新的 jar（CI 每次推送都会自动发布构建）；
2. 放进服务端的 `plugins/` 目录，启动服务端；
3. 首次启动会生成 `config.yml`、`lang/`、示例菜单，以及 `commands.yml`、`tasks.yml`；
4. 改配置、写菜单；
5. 保存文件即可热重载，或者执行 `/dgm reload`。

数据目录大致是这样：

```text
plugins/MiaoMenu/
├── config.yml
├── commands.yml        # 自定义命令
├── tasks.yml           # 周期任务
├── storage.yml         # 变量持久化
├── lang/               # 语言文件
├── java_menus/         # Java 版菜单
├── bedrock_menus/      # 基岩版菜单
├── dialog_menus/       # Dialog 菜单
└── themes/             # 主题资源（下载或手动放入）
```

## 快速开始

### 1. Java 版背包菜单

在 `plugins/MiaoMenu/java_menus/` 新建 `my-menu.yml`：

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
      - "[player] spawn"

  close:
    slot: 22
    material: BARRIER
    display_name: "&c关闭菜单"
    left_click_commands:
      - "[close]"
```

然后 `/dgm open my-menu`，或者把 `settings.default-menu` 改成 `my-menu`，右键菜单时钟打开。

### 2. 基岩版表单

在 `plugins/MiaoMenu/bedrock_menus/` 新建 `my-menu.yml`：

```yaml
menu:
  title: "&6&l我的菜单"
  subtitle: "&7欢迎来到服务器"
  items:
    - text: "&a返回主城\n&7点击传送"
      icon: "textures/items/compass_item"
      icon_type: path
      command: "spawn"
      execute_as: player
    - text: "&c关闭"
      command: "close"
      execute_as: close
```

`execute_as` 复用的是动作前缀，所以 `player`、`cmd`、`message`、`close`、`menu` 都可以填。基岩版表单不支持 `§` 颜色码，下发前会自动剥离。

### 3. 原生 Dialog

在 `plugins/MiaoMenu/dialog_menus/` 新建 `my-dialog.yml`：

```yaml
title: "&6&l我的菜单"

layout:
  type: multi_action
  columns: 2

body:
  - type: text
    text: "&f你好，&e%player_name%"

buttons:
  - label: "&a返回主城"
    item: COMPASS
    tooltip: "&7点击传送"
    actions:
      - "[player] spawn"
  - label: "&c关闭"
    item: BARRIER
    actions:
      - "[close]"
```

输入组件的值可以在动作里用 `$(key)` 引用，例如 `[var_set] player:last_message $(message)`。

## 命令与权限

| 命令 | 别名 | 权限 | 说明 |
|------|------|------|------|
| `/dgeysermenu open <菜单名>` | `/dgm open`、`/mmy open` | `dgeysermenu.use` | 打开菜单，支持 `java:` / `bedrock:` / `dialog:` 前缀 |
| `/dgeysermenu reload` | `/dgm reload`、`/mmy reload` | `dgeysermenu.reload` | 重载配置与全部菜单 |
| `/dgeysermenu update` | `/dgm update`、`/mmy update` | `dgeysermenu.update` | 检查是否有新版本（只检查，不下载） |
| `/dgeysermenu download [主题ID] [--force]` | `/dgm download`、`/mmy download` | `dgeysermenu.download` | 列出 / 安装主题菜单 |
| `/dgeysermenu help` | `/dgm help`、`/mmy help` | `dgeysermenu.use` | 显示帮助 |
| `/getmenuclock` | - | `dgeysermenu.admin` | 获取一个菜单时钟 |

`dgeysermenu`、`dgm`、`fluxmenu`、`mmy` 是同一个命令的别名，共用同一套参数解析、权限检查和 Tab 补全。

```text
dgeysermenu.*            所有权限
├── dgeysermenu.use      default: true
├── dgeysermenu.admin    default: op
├── dgeysermenu.reload   default: op
├── dgeysermenu.update   default: op
└── dgeysermenu.download default: op
```

不带前缀打开菜单时的路由规则：基岩玩家先找 `bedrock_menus/`，找不到再回退到 Java 菜单（Geyser 能渲染原版容器）；Java 玩家先找 `java_menus/`，找不到再试 `dialog_menus/`。

## 配置

`config.yml` 里常用的部分：

```yaml
config-version: 27          # 配置版本，插件据此判断是否需要迁移
menu-version: 8             # 示例菜单版本

settings:
  lang: "zh-cn"             # en-us / zh-cn / zh-tw / vi-vn / ja-jp
  default-menu: "test"      # 右键菜单时钟打开的菜单
  open-menu-sound:
    enabled: true
    sound: "entity.experience_orb.pickup"
    volume: 1.0
    pitch: 1.0
  hot-reload:
    enabled: true           # 保存文件后自动重载（含 lang/）
  auto-generate-examples: true
  proxy-mode: NONE          # NONE / BUNGEE / VELOCITY
  validate-commands: false  # 只作用于 [cmd] / [player] 和无前缀命令
  rate-limit:
    window-millis: 2000
    max-events: 10
  item-resolver:
    fallback-material: STONE
  menu-clock:
    enabled: true
    give-on-join: true
  update-check:
    enabled: true
    repository: "Yamada0001/MiaoMenu"
    interval-hours: 0       # 0 = 只在启动时检查一次
  theme-repository:
    repository: "Yamada0001/MiaoMenu-theme"
    branch: "main"
  resource-pack:
    enabled: false
    url: ""
    sha1: ""
    force: false
```

当 `config-version` 和插件内置的版本不一致时，会先把现有配置备份成 `config.yml.<时间戳>.bak`，然后**只补齐缺失的键**，不会覆盖你改过的值。

`menu-version` 用来判断示例菜单要不要刷新；`settings.auto-generate-examples` 控制缺失的示例文件要不要释放。

## 菜单配置

### 动作前缀

菜单里的点击动作都是一个字符串，前面的方括号是动作类型：

| 前缀 | 说明 | 示例 |
|------|------|------|
| `[player]` | 以玩家身份执行命令 | `[player] spawn` |
| `[cmd]` | 以控制台身份执行命令 | `[cmd] give %player% diamond 1` |
| `[message]` | 给玩家发消息 | `[message] &a你好` |
| `[close]` | 关闭当前菜单 | `[close]` |
| `[menu]` | 打开另一个菜单 | `[menu] settings` |
| `[var_set]` | 写入变量 | `[var_set] player:visits 1` |
| `[var_add]` | 变量数值累加 | `[var_add] player:visits 1` |
| `[var_remove]` | 删除变量 | `[var_remove] player:visits` |
| 无前缀 | 等同 `[player]` | `spawn` |

几个注意点：

- `[cmd]` 意味着任何能点到这个按钮的玩家都获得了该命令的控制台权限，不要把玩家可控的内容（比如昵称占位符）拼进 `[cmd]` 命令；
- Folia 上 `[cmd]` 会投递到全局区域线程执行，不一定和同一串里的其它动作落在同一个 tick；
- `[menu]` 支持 `java:<名字>`、`bedrock:<名字>`、`dialog:<名字>`，不带前缀时按上面的路由规则走；
- 前缀写错（比如 `[console]`）会输出 `Unknown action prefix` 告警并跳过该动作。

### Java 版物品字段

| 键 | 说明 |
|----|------|
| `slot` | 格子编号，从 0 开始，总数是 `rows × 9` |
| `material` | 原版材质或带前缀的物品来源（见下文） |
| `custom_model_data` | 资源包模型数据，`0` 表示不用 |
| `display_name` / `lore` | 支持 `&` 颜色码和占位符 |
| `left_click_commands` / `right_click_commands` | 左键 / 右键的动作串列表 |
| `conditions` | 解锁条件，支持 AND / OR 嵌套；旧的 `item_conditions` 仍然可用 |
| `lock_message` | 条件不满足时的提示 |

菜单级还有 `menu_title`、`rows`、`view_requirement` 和 `requirement_blocks`。

### 基岩版表单字段

| 键 | 说明 |
|----|------|
| `menu.title` / `menu.subtitle` / `menu.footer` | 标题与正文的首尾行 |
| `menu.items[].text` | 按钮文字，`\n` 换行 |
| `menu.items[].icon` / `icon_type` | `path`（资源包路径）或 `url`（图片直链） |
| `menu.items[].command` | 要执行的内容，`%player%` 会替换成玩家名 |
| `menu.items[].execute_as` | 复用动作前缀：`player` / `cmd` / `message` / `close` / `menu` 等 |
| `menu.items[].conditions` / `lock_message` | 条件不满足时按钮显示为 `[未解锁]` |
| `view_requirement` | 菜单级条件 |

### Dialog 字段

| 键 | 说明 |
|----|------|
| `title` / `external_title` | 窗口标题 |
| `can_close_with_escape` / `pause` / `after_action` | 关闭与暂停行为；`after_action` 可选 `close` / `none` / `wait_for_response` |
| `layout.type` | `multi_action` / `notice` / `confirmation` |
| `layout.columns` / `layout.exit_button` | 按钮列数与退出按钮 |
| `body[]` | `text` 或 `item` |
| `inputs[]` | `text` / `boolean` / `number_range` / `single_option` |
| `buttons[]` | `label` / `item` / `tooltip` / `width` / `actions` / `conditions` / `locked_label` |
| `dynamic_lists[]` | 动态按钮列表，内置 `online_players` 数据源，也可以用 API 注册 |
| `view_requirement` | 菜单级条件 |

`layout` 下还有一个可选的 `grid`（`size`、`button_width`、`style`、`overlay_offset_y`），用来把按钮排成固定网格。

## 条件系统

| 类型 | 说明 |
|------|------|
| `permission` | 是否拥有权限 |
| `placeholder_equals` / `placeholder_not_equals` | 占位符等于 / 不等于（也接受 `placeholder-not-equals`） |
| `placeholder_contains` | 占位符包含某个字符串 |
| `advancement` | 成就是否完成 |
| `progress` | 记分板分数达到阈值；不写 `value` 时分数大于 0 即通过 |
| `score_gte` / `score_lte` / `score_equals` | 分数比较 |
| `score_range` | 分数在闭区间内 |
| `block` | 引用命名条件块 |

AND / OR 可以嵌套：

```yaml
conditions:
  operator: AND
  requirements:
    - type: advancement
      advancement: "minecraft:story/root"
  children:
    - operator: OR
      requirements:
        - type: permission
          permission: "vip.shop"
        - type: score_gte
          objective: "trade_count"
          value: 5
```

命名条件块适合在多处复用：

```yaml
requirement_blocks:
  staff_only:
    deny_message: "&c仅管理员可见。"
    requirements:
      - type: permission
        permission: dgeysermenu.admin

items:
  note:
    slot: 34
    material: WRITABLE_BOOK
    display_name: "&c管理员提示"
    conditions:
      requirements:
        - type: block
          block: staff_only
```

失败语义（这部分我写得很保守，宁可拒绝也不误放行）：

- **占位符没解析成功**（没装 PAPI、expansion 不存在、拼错）一律按不满足处理；
- **玩家从没被设置过该记分板分数**时按不满足处理，不会当成 0 分，避免 `score_lte` 对新玩家恒真；
- **条件组里有一条写错**（类型未知、缺必填参数、引用不存在的块），整组直接判定不满足，并在控制台告警一次；
- **空 AND 组**视为无条件通过，**空 OR 组**视为不满足；
- `deny_message` 和 `fallback_menu` 可以写在单条条件、命名条件块和菜单的 `view_requirement` 上。

## 物品来源

`material` 按前缀解析，解析失败时回退到 `settings.item-resolver.fallback-material`（默认 `STONE`），并在控制台告警一次。

| 写法 | 来源 | 需要 |
|------|------|------|
| `craftengine:namespace:id` | CraftEngine | CraftEngine |
| `itemsadder:namespace:id` | ItemsAdder | ItemsAdder |
| `mmoitems:TYPE:ID` | MMOItems | MMOItems |
| `headdb:id` | HeadDatabase | HeadDatabase |
| `base64head:<base64>` | 原版头颅纹理 | 无 |
| `image:<主题ID>` | 资源包物品模型 `miaomenu:menu/<ID>` | 资源包（可选） |
| `DIAMOND_SWORD` | 原版材质 | 无 |

`base64head:` 只接受 `textures.minecraft.net` 的皮肤 URL；同一份 Base64 每次都会生成完全相同的头颅。`image:` 在没有资源包时会显示成纸张，菜单本身仍然能用。

## 变量与占位符

```yaml
left_click_commands:
  - "[var_add] player:visits 1"
  - "[message] &a这是你第 %miao_player_visits% 次打开菜单。"
```

- `player:<key>` 是当前玩家的变量，`global:<key>` 是全局变量；
- 变量会写进 `storage.yml`，关服时也会 flush 一次；
- `%miao_player_<key>%` / `%miao_global_<key>%` 需要服务端安装 PlaceholderAPI；
- 菜单文本里的普通占位符（如 `%player_name%`）也走 PlaceholderAPI。

## 自定义命令与周期任务

`commands.yml` 可以注册带参数的命令：

```yaml
commands:
  miaomenu-example:
    enabled: true
    description: "打开示例菜单"
    usage: "/miaomenu-example [message]"
    aliases: [mmexample]
    permission: dgeysermenu.use
    actions:
      - "[var_set] player:command_message %args%"
      - "[menu] test"
    tab_complete:
      - hello
      - welcome
```

动作串里 `%args%` 是完整参数，`%arg_1%` / `{arg:1}` 是第 N 个参数。

`tasks.yml` 是周期任务，`delay` 和 `interval` 的单位都是 tick：

```yaml
tasks:
  visit-reminder:
    enabled: true
    delay: 200
    interval: 1200
    permission: dgeysermenu.use
    conditions:
      requirements:
        - type: permission
          permission: dgeysermenu.use
    actions:
      - "[message] &e你好 %player_name%，你已打开示例菜单 %miao_player_visits% 次。"
```

## 主题与资源包

主题仓库（默认 `Yamada0001/MiaoMenu-theme`）在根目录提供 `themes.json` 清单，插件按清单下载菜单和贴图：

| 命令 | 说明 |
|------|------|
| `/dgm download` | 列出可用主题 |
| `/dgm download <主题ID>` | 下载并安装，完成后自动热重载 |
| `/dgm download <主题ID> --force` | 覆盖已存在的同名文件 |

| 主题 ID | 名称 | 季节 |
|---------|------|------|
| `spring_sakura` | 春日樱花 | 春 |
| `summer_beach` | 夏日海滩 | 夏 |
| `autumn_forest` | 秋日森林 | 秋 |
| `winter_snow` | 冬季飘雪 | 冬 |

- 下载目标只允许 `java_menus/`、`dialog_menus/`、`bedrock_menus/`、`themes/`、`lang/`，清单里出现 `..` 或绝对路径会被拒绝；
- 已存在的文件默认跳过，避免覆盖你手改过的菜单；
- 清单里给了 `sha256` 的会校验下载结果。

主题图片依赖资源包。配置后玩家进服会自动收到：

```yaml
settings:
  resource-pack:
    enabled: true
    url: "https://你的域名/miaomenu-theme.zip"
    sha1: "40 位十六进制 SHA-1"
    force: false
    prompt: "&d加载 MiaoMenu 主题资源包"
    delay-ticks: 20
```

`enabled` 为 `false` 或 `url` 为空时自动跳过；`sha1` 不合法只会告警一次并改成不校验下发，不会挡住玩家进服。

## 热重载与安全

热重载监听这些位置：

- `config.yml`
- `java_menus/*.yml`
- `bedrock_menus/*.yml`
- `dialog_menus/*.yml`
- `lang/*.yml`

采用尾随防抖：最后一次改动之后静默 500ms 才会真正重载，避免读到写了一半的文件。`commands.yml`、`tasks.yml`、`storage.yml` 的改动不会触发菜单重载，需要 `/dgm reload`。

重载是容错的：单个菜单解析失败时保留它上一次加载的实例；目录整体为空或全部失败时保留现有注册表，不会把全服菜单清空。

安全方面做了两件事：

- **滑动窗口限流**：`settings.rate-limit` 控制窗口长度和窗口内允许的点击次数，目前覆盖 Java 背包点击和基岩表单回调；
- **命令内容校验**：`settings.validate-commands` 打开后，`[cmd]`、`[player]` 和无前缀命令会先过一遍内容校验，不安全的直接拒绝并告警。

跨服传送用 `[player] server <服务器名>`，需要在 `settings.proxy-mode` 里显式选 `BUNGEE` 或 `VELOCITY`。Velocity 还要在 `velocity.toml` 里开启 `bungee-plugin-message-channel`，否则消息发不出去。

## 开发

<p>
  <img alt="Java 25" src="https://img.shields.io/badge/Java_25-ED8B00?style=flat-square&logo=openjdk&logoColor=white">
  <img alt="Maven" src="https://img.shields.io/badge/Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white">
  <img alt="GitHub Actions" src="https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white">
  <img alt="YAML" src="https://img.shields.io/badge/YAML-CB171E?style=flat-square&logo=yaml&logoColor=white">
</p>

### 构建与测试

需要 JDK 25：

```bash
mvn clean package   # 编译、跑测试、shade 打包
mvn test            # 只跑单元测试
mvn verify          # 完整验证
```

打包产物在 `target/MiaoMenu-V3.1.jar`，`original-*.jar` 是 shade 之前的原始 jar，不要拿去用。

目前有 21 个测试类、100 多个测试方法，覆盖条件引擎、限流、主题清单与安装、版本比较、配置解析、文本解析等。

### 项目结构

```text
MiaoMenu/
├── src/main/java/com/fluxcraft/MiaoMenu/
│   ├── MiaoMenu.java          # 主类：装配、菜单路由、生命周期
│   ├── api/                   # 公开 API（MiaoMenuApi、动态列表）
│   ├── javamenu/              # Java 版背包菜单
│   ├── bedrockmenu/           # 基岩版表单（Floodgate 反射桥）
│   ├── dialogmenu/            # Paper 原生 Dialog
│   ├── menu/
│   │   ├── action/            # 动作前缀与实现
│   │   └── requirement/       # 条件引擎
│   ├── managers/              # 热重载、菜单时钟、资源包、自定义命令、周期任务
│   ├── integration/           # 物品解析、PAPI、文本解析（IA / Oraxen / CraftEngine）
│   ├── commands/              # 命令
│   ├── config/                # 配置加载与迁移
│   ├── foliacall/             # Folia 适配
│   ├── security/              # 限流与输入校验
│   ├── storage/               # 变量持久化
│   ├── theme/                 # 主题清单与下载
│   ├── update/                # 版本比较与更新检查
│   ├── proxy/                 # BungeeCord / Velocity
│   └── utils/
├── src/main/resources/
│   ├── config.yml / plugin.yml
│   ├── lang/                  # 5 种语言
│   ├── java_menus/ bedrock_menus/ dialog_menus/
│   ├── commands.yml / tasks.yml
│   └── themes/                # 资源包结构（四季主题图）
├── src/test/java/             # 单元测试
└── .github/workflows/ci.yml
```

### 插件 API

其他插件可以从 `ServicesManager` 拿到 API：

```java
RegisteredServiceProvider<MiaoMenuApi> registration =
        Bukkit.getServicesManager().getRegistration(MiaoMenuApi.class);
if (registration == null) {
    return;
}
MiaoMenuApi api = registration.getProvider();

api.openMenu(player, "test");
api.setPlayerVariable(player.getUniqueId(), "visits", "1");
```

也可以注册自己的动作前缀和动态列表数据源：

```java
api.registerAction("sound", (player, content, plugin) ->
        player.playSound(player.getLocation(), content, 1.0f, 1.0f));

api.registerDynamicListProvider("warps", (player, options) ->
        List.of(new DynamicListEntry("spawn", "主城")));
```

### CI

- push / PR 到任意分支都会跑 `mvn -B verify`，产物上传为 Actions Artifact；
- push 时会自动发布 GitHub Release，标签形如 `V3.1-build.<运行号>`，jar 里会写入 `build-info.properties`，所以更新检查能精确判断是否已是最新；
- Dependabot 每周检查 Maven 依赖和 GitHub Actions 更新。

## 已知限制

- 基岩版菜单依赖 Floodgate（以及它自带的 Cumulus 表单）；没装时基岩玩家会收到一条提示，Java 菜单不受影响；
- `[cmd]` 是以控制台身份执行的，能点到按钮的玩家就拥有这条命令的完整权限，别把玩家可控内容拼进去；
- 目前只有左键 / 右键两种点击，没有单独的 Shift 动作，Shift+左键会按左键处理；
- Dialog 用的是 Paper 自带的 Dialog API，只能在支持它的服务端版本上用；
- 限流目前覆盖 Java 背包点击和基岩表单回调，Dialog 按钮和自定义命令还没走限流；
- 第三方物品插件都是反射调用，对方改了 API 就可能失效，失效时会回退材质并告警；
- 新增语言需要改代码里的内置语言列表（`Lang.BUNDLED_LANGS`），只往 `lang/` 里丢文件不会生效。

## 开发计划

下一版具体做什么我还没定，先留个位置：

- [ ] 待补充：下一版要做的功能
- [ ] 待补充：升级时的兼容性说明

有想要的功能或者遇到问题，直接开 Issue 就好。

## 关于与联系

MiaoMenu 是我在维护的个人项目，不是商业产品，更新节奏取决于服务器最近需要什么。

- 问题与建议：[Issues](https://github.com/Yamada0001/MiaoMenu/issues)
- 主题仓库：[MiaoMenu-theme](https://github.com/Yamada0001/MiaoMenu-theme)
- 其他联系方式：待补充

<p align="center">
  <img src="https://github-readme-stats.vercel.app/api/pin/?username=Yamada0001&repo=MiaoMenu&theme=transparent&hide_border=true&locale=cn" alt="MiaoMenu 仓库统计" height="150">
</p>

## 许可证

本项目以 [GPL-3.0](LICENSE) 发布，版权信息见 LICENSE 文件。二次修改和分发请遵循同一许可证。
