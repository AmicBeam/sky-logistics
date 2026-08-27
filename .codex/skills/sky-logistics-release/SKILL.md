---
name: sky-logistics-release
description: 重新发布 Sky Logistics 当前版本：保持三个 mod_version 不变，重打同名 tag，并把既有 GitHub Release 重写为完整净变更日志。用户说“重打 tag”“重新发布 tag”“不推进版本重新发布”或要求更新现有版本发布链路时使用。
---

# Sky Logistics 重新发布 Tag

本 skill 处理同版本重新发布，不推进版本号。默认保持既有 Release 的正式/预发布状态和附件不变，把当前版本 tag 移到新的发布提交，并用该版本最终生效的全部变化重写完整日志。

## 授权边界

- 覆盖远端同名 tag 是破坏性操作。只有用户明确要求“重打 tag”“重新发布 tag”或同等含义时才执行；普通“发版”不能推断为允许覆盖。
- 不修改三个 `versions/*/gradle.properties` 的 `mod_version`。三个值不一致时停止并报告。
- “给我 JAR”只表示构建后放到本地 `dist/` 交付，不表示上传 GitHub Release。只有用户明确要求作为 Release 附件时才能上传。
- 不改变既有 Release 的 `isPrerelease`、`isDraft`、latest 状态或附件集合，除非用户明确要求。不能把“不推进版本”理解为 Pre-release。
- 不删除或重建 GitHub Release。若同名 Release 不存在，停止并询问是否创建。

## 发布前核对

- 确认工作区状态，保留并排除与发布无关的改动。发布所需仓库改动必须先提交并推送。
- 读取三个 `versions/*/gradle.properties`，要求 `mod_version` 完全一致；该值就是版本号与 tag，tag 不加 `v` 前缀。
- 读取并记录本地和远端同名 annotated tag 对象及 peeled commit；同时读取 GitHub Release 的标题、完整正文、草稿/预发布/latest 状态、附件名称和 URL。
- 要求旧 tag 可解析且同名 Release 已存在。保留旧 tag 对象和 peeled commit，供失败后人工恢复；不得自行删除 Release。
- 找到该版本之前的上一条语义化版本 tag，用 `上一版本..发布提交` 的最终差异和提交记录重新整理**整个当前版本**的净变更。只描述最终仍生效的变化，忽略已回滚内容。

## 可选本地 JAR

仅在用户要求 JAR 时执行：

- 使用 `sky-logistics-build` 的对应 JDK，对 1.20.1、1.21.1、26.1.2 分别运行发布级 `clean build`。
- 产物命名为 `skylogistics-<version>+<minecraft-version>.jar`。
- 覆盖 `dist/` 中同名文件前，备份旧包到 `/private/tmp/sky-logistics-<version>-before-republish/`。
- 复制新包到 `dist/` 并计算 SHA-256；本地 JAR 不加入 Git。
- 除非用户明确要求上传，Release 附件保持原样。

## Tag

- 在已提交并推送的发布提交上删除并重新创建同名 annotated tag，说明为 `Sky Logistics <版本号>`。
- 只强制推送精确 ref：`git push --force origin refs/tags/<版本号>`；禁止使用宽泛的 `--tags` 或强推分支。
- 核对远端 tag 对象和 peeled commit，要求 peeled commit 等于发布提交。
- 强推失败时停止，不得改推其它 ref。报告新旧 tag 指向和人工恢复所需的旧对象/commit。

## 重写既有 GitHub Release

- 使用同名 Release，标题为 `Sky Logistics <版本号>`，通过 `gh release edit` 原地更新，不删除重建。
- 日志必须是一份统一、完整的当前版本更新日志：以版本整体概述开头，随后使用 `### 主要更新` 和主题章节汇总从上一版本以来全部用户可感知的净变化。
- 把重新发布新增的内容合并到合适的主题章节中。禁止使用“本次重新打包”“本次增量更新”“增量前缀 + 旧日志”或分隔线拼接两份日志。
- 固定包含以下支持版本：
  - Minecraft 1.20.1 / Forge
  - Minecraft 1.21.1 / NeoForge
  - Minecraft 26.1.2 / NeoForge
- 保持原有草稿/预发布/latest 状态和附件集合，除非用户明确要求改变。
- 附件为空时，末尾明确写明：`本 Release 按发布要求不附带 JAR 或其他构建附件。`
- 更新失败时停止并报告 tag 与 Release 的当前状态，不擅自回滚或继续其它发布动作。

## 完成验证

完成后核对并报告：三个版本号未变化；远端 tag 对象与 peeled commit；Release 的 tag、标题、URL、草稿/预发布状态；正文是一份完整日志且包含新增功能；附件与授权一致；若构建 JAR则给出三个本地绝对路径、大小和 SHA-256；工作区干净且发布提交已推送。
