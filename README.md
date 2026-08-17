# Sparse Vertical Expansion

[English](README_en-us.md) | 简体中文

Sparse Vertical Expansion（稀疏垂直扩展，简称 SVE）是一个 Minecraft 的实验性模组，目前仅支持1.21.1 NeoForge。它让指定区块区域拥有按需分配的超原版高度，而不提高整个维度的连续建筑高度。每个区块最高可达±8M格，不过默认上限为±2M格。

> 当前版本：`0.1.0-beta.2`。**请先备份存档**，并只在测试存档中使用。  
> 当前版本兼容 `钠`（Sodium `0.8.12/0.8.13`）与 `铷`（Embeddium `1.0.x`）（均为 beta 阶段），其他版本尚未测试，**其他重写渲染的模组不兼容**。

- 设计：xiaoshuaixia
- 开发：gpt(AI)、deepseek(AI)

## 核心特点

- 普通区块继续使用原版 `Y=-64..319` 和连续 section 数组。
- 扩展 section 使用 `int sectionY` 稀疏存储；中间没有方块的高度不占用 section。
- 最后一个非空气方块被移除后，空 section 自动回收。
- 扩展数据随区块 NBT 保存，退出并重新进入世界后仍然存在。
- 每个区块或区块群可配置独立的垂直建筑区域，区域不能重叠。
- 原版高度范围不可修改，所有扩展范围按完整 16 格 section 向外对齐。
- 默认存档最高 Y 为 `2,000,015`；标准坐标范围为 `-8,388,608..8,388,607`。

## 当前可用功能

- 在已配置区域内放置、破坏和读取普通方块。
- 客户端同步、原版渲染路径、碰撞、移动、音效、粒子和生存挖掘。
- `/sve` 高度编辑界面；右键垂直条中的已有区域可删除空区域。
- 删除非空区域时拒绝操作，并返回遇到的第一个非空气方块坐标。
- `setblock`、`fill`、`clone` 的扩展坐标解析和区域边界验证。
- WorldEdit 7.3.8 批量写入边界验证，非法操作不会留下部分填充。
- 存档级最高 Y、虚空伤害和权限等级配置。

## 使用方法

1. 让拥有 `sve.region.edit` 权限的玩家站在目标区块并执行 `/sve`。
2. 输入最低与最高 Y；输入框失焦后会自动向外对齐到完整 section。
3. 确认建筑区域后，使用 `/sve platform <x> <y> <z>` 创建第一个方块，或直接从已有方块表面继续搭建。
4. 在垂直条中左键选择已有区域，右键删除；区域中存在方块时删除会被拒绝。

## 权限

- `sve.extended.build`：在扩展高度建造，默认所有玩家拥有。
- `sve.region.edit`：编辑区块垂直区域。
- `sve.config.edit`：编辑存档级配置。
- `sve.experimental.edit`：编辑实验设置。
- `sve.command.all`：拥有全部 SVE 指令权限。

没有权限模组时，权限节点映射到原版权限等级 `0..4`，可使用 `/sve permission` 修改。安装 LuckPerms 等权限模组后，可由外部模组管理玩家权限。

## 存档配置

配置只由服务器或单人存档拥有者修改，并保存在每个存档中：

```text
/sve config list
/sve config set default_extended_max_y <y>
/sve config set disable_void_damage false|player|entity
```

`disable_void_damage` 默认为 `false`。

## 兼容性与限制

- 当前只支持 Minecraft 1.21.1、NeoForge 和 Java 21。
- `钠`（Sodium `0.8.12/0.8.13`）与 `铷`（Embeddium `1.0.x`）稀疏 section 兼容层已实现（可选反射桥 + 可选 Mixin，不打包这些模组、未安装时完全不影响原版路径）；光影已验证可用（含阴影），Iris 兼容性仍在测试。
- 扩展高度暂不支持方块实体。
- 稀疏光照尚未实现，高空方块暂时使用全亮回退。
- scheduled tick、随机刻、流体刻、红石、生物生成和天气等分段模拟规则尚未实现。
- 实验性 double 坐标模式尚未实现。
- Create、Valkyrien Skies 和航空学兼容不属于当前 Beta 的保证范围。

## 安装与构建

运行环境：

- Minecraft 1.21.1
- NeoForge 21.1.248
- Java 21

构建：

```powershell
.\gradlew.bat build
```

构建产物位于 `build/libs/`。

## 发布渠道

- [GitHub](https://github.com/xiaoshuaixia2012/sparse-vertical-expansion)
- Modrinth
- MC百科

## 许可

本项目采用 [GNU GPLv3](LICENSE)。整合包可以包含和分发本模组；修改或派生自 SVE 的代码必须继续遵守 GPLv3。
