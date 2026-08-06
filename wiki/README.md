# GitHub Wiki source

此目录保存 `AmicBeam/sky-logistics` 的 GitHub Wiki Markdown 源文件。

GitHub Wiki 使用独立的 Git 仓库：

```bash
git clone https://github.com/AmicBeam/sky-logistics.wiki.git
```

启用并初始化 GitHub Wiki 后，将本目录中除 `README.md` 外的 Markdown 文件同步到 Wiki 仓库根目录并推送即可发布。`Home.md`、`_Sidebar.md` 和 `_Footer.md` 是 GitHub Wiki 的特殊页面。

内容维护要求：

1. 新增物品或方块时同步更新图鉴与侧边栏。
2. 功能变更必须同步核对所有支持版本。
3. 默认数值以当前服务端配置代码为准。
4. 默认配方以各版本数据包为准；存在差异时在 Wiki 中明确标注。
