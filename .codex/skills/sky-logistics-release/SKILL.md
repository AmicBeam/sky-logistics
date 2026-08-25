---
name: sky-logistics-release
description: 发布 Sky Logistics 新版本：核对三个版本号，整理净变更日志，创建并推送版本 tag 和 GitHub Release。用户说“发布更新”“发版”或要求创建版本 tag/Release 时使用。
---

# Sky Logistics Release

发布默认只创建 tag 和 GitHub Release，不构建或上传 JAR；只有用户明确要求附件时才打包或上传。

## 发布前

- 确认工作区状态，保留并排除与发布无关的改动。
- 读取三个 `versions/*/gradle.properties`，要求 `mod_version` 完全一致；该值就是版本号与 tag，tag 不加 `v` 前缀。
- 检查同名 tag 和 Release 是否已存在。存在时停止并说明现状，不覆盖、不重建，除非用户明确授权。
- 找到上一条语义化版本 tag，用 `上一版本..HEAD` 的最终差异和提交记录整理更新日志。只描述最终生效的变化，忽略已回滚且没有净效果的提交。
- 发布所需的仓库改动必须先提交并推送；不得把无关改动带入发布提交。

## Tag

- 在当前发布提交上创建 annotated tag：tag 名为版本号，说明为 `Sky Logistics <版本号>`。
- 将该 tag 推送到 `origin`，并核对远端 tag 指向预期提交。

## GitHub Release

- 使用同名 tag，标题为 `Sky Logistics <版本号>`。
- 更新日志以简短版本概述开头，随后使用 `### 主要更新` 汇总用户可感知的净变化。
- 固定包含以下支持版本：
  - Minecraft 1.20.1 / Forge
  - Minecraft 1.21.1 / NeoForge
  - Minecraft 26.1.2 / NeoForge
- 末尾明确写明：`本 Release 按发布要求不附带 JAR 或其他构建附件。`
- 使用 notes file 创建 Release，不向 `gh release create` 传任何附件路径。
- 创建后读取 Release 的 tag、标题、正文、草稿状态、预发布状态、附件列表和 URL，确认正式发布且附件为空。

完成后报告 tag、Release 链接、发布提交和附件为空的验证结果。
