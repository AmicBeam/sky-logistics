"use client";

import { useMemo, useState } from "react";

type ResourceKey = "item" | "fluid" | "energy" | "auto";
type RedstoneMode = "忽略" | "有信号" | "无信号" | "禁用";

const resourceOptions: Array<{ key: ResourceKey; icon: string; label: string }> = [
  { key: "item", icon: "▣", label: "物品" },
  { key: "fluid", icon: "♠", label: "流体" },
  { key: "energy", icon: "ϟ", label: "能量" },
  { key: "auto", icon: "◉", label: "自动" },
];

const redstoneModes: RedstoneMode[] = ["忽略", "有信号", "无信号", "禁用"];

const entries = [
  { dir: "↑", color: "green", flags: [1, 0, 1], priority: "0", redstone: true, pos: "12  64  -8", world: "earth" },
  { dir: "↓", color: "cyan", flags: [1, 1, 0], priority: "-1", redstone: false, pos: "-32  70  15", world: "earth" },
  { dir: "←", color: "amber", flags: [0, 0, 1], priority: "1", redstone: true, pos: "5  55  100", world: "orb" },
  { dir: "→", color: "cyan", flags: [1, 1, 1], priority: "0", redstone: false, pos: "0  128  0", world: "orb" },
  { dir: "↑", color: "green", flags: [1, 1, 0], priority: "2", redstone: false, pos: "18  72  11", world: "earth" },
  { dir: "←", color: "amber", flags: [0, 1, 1], priority: "-2", redstone: true, pos: "-8  90  42", world: "orb" },
];

function PixelButton({
  children,
  active = false,
  disabled = false,
  className = "",
  title,
  onClick,
}: {
  children: React.ReactNode;
  active?: boolean;
  disabled?: boolean;
  className?: string;
  title?: string;
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
}) {
  return (
    <button
      type="button"
      className={`pixel-button ${active ? "is-active" : ""} ${className}`}
      disabled={disabled}
      title={title}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

export default function Home() {
  const [lines, setLines] = useState(["AmicBeam-0", "工厂主线", "跨维度仓储"]);
  const [lineIndex, setLineIndex] = useState(0);
  const [detailPage, setDetailPage] = useState(0);
  const [resources, setResources] = useState<Record<ResourceKey, boolean>>({
    item: true,
    fluid: false,
    energy: false,
    auto: false,
  });
  const [redstone, setRedstone] = useState<RedstoneMode>("忽略");
  const [slotLimit, setSlotLimit] = useState(0);
  const [priority, setPriority] = useState(0);
  const [scale, setScale] = useState(1);
  const [showReference, setShowReference] = useState(false);
  const [lastAction, setLastAction] = useState("等待操作");

  const pageCount = Math.ceil(entries.length / 4);
  const visibleEntries = useMemo(() => entries.slice(detailPage * 4, detailPage * 4 + 4), [detailPage]);

  const selectLine = (next: number) => {
    setLineIndex(next);
    setDetailPage(0);
    setLastAction(`切换到线路 ${next + 1}：${lines[next]}`);
  };

  const createNextLine = () => {
    if (lineIndex < lines.length - 1) {
      selectLine(lineIndex + 1);
      return;
    }
    const nextLines = [...lines, `新线路-${lines.length}`];
    setLines(nextLines);
    setLineIndex(nextLines.length - 1);
    setLastAction(`已创建 ${nextLines.at(-1)}`);
  };

  const deleteLine = () => {
    if (lines.length === 1) return;
    const removed = lines[lineIndex];
    const nextLines = lines.filter((_, index) => index !== lineIndex);
    setLines(nextLines);
    setLineIndex(Math.min(lineIndex, nextLines.length - 1));
    setLastAction(`已删除线路：${removed}`);
  };

  const renameLine = (name: string) => {
    setLines((current) => current.map((line, index) => (index === lineIndex ? name : line)));
    setLastAction("线路名称已修改");
  };

  const toggleResource = (key: ResourceKey, label: string) => {
    setResources((current) => {
      const next = !current[key];
      setLastAction(`${label}${next ? "已启用" : "已停用"}`);
      return { ...current, [key]: next };
    });
  };

  const cycleRedstone = () => {
    const index = redstoneModes.indexOf(redstone);
    const next = redstoneModes[(index + 1) % redstoneModes.length];
    setRedstone(next);
    setLastAction(`红石模式：${next}`);
  };

  const adjust = (
    setter: React.Dispatch<React.SetStateAction<number>>,
    label: string,
    direction: -1 | 1,
    fast: boolean,
  ) => {
    const amount = direction * (fast ? 10 : 1);
    setter((value) => {
      const next = Math.max(label === "留槽" ? 0 : -99, Math.min(99, value + amount));
      setLastAction(`${label}${amount > 0 ? "+" : ""}${amount} → ${next}`);
      return next;
    });
  };

  const reset = () => {
    setLineIndex(0);
    setDetailPage(0);
    setResources({ item: true, fluid: false, energy: false, auto: false });
    setRedstone("忽略");
    setSlotLimit(0);
    setPriority(0);
    setLastAction("已恢复初始状态");
  };

  return (
    <main className="prototype-page">
      <section className="intro">
        <div>
          <p className="eyebrow">SKY LOGISTICS · INTERACTION PROTOTYPE</p>
          <h1>天穹配置器 GUI 原型</h1>
          <p>按 520×500 美术交付稿模拟。点击控件可验证状态与交互，尚未写入模组代码。</p>
        </div>
        <div className="toolbar" aria-label="原型预览工具">
          <span>预览倍率</span>
          <button className={scale === 1 ? "selected" : ""} onClick={() => setScale(1)}>1×</button>
          <button className={scale === 2 ? "selected" : ""} onClick={() => setScale(2)}>2×</button>
          <label><input type="checkbox" checked={showReference} onChange={(event) => setShowReference(event.target.checked)} /> 叠加参考稿</label>
          <button onClick={reset}>重置</button>
        </div>
      </section>

      <section className="workspace">
        <div className="canvas-wrap" style={{ width: 520 * scale, height: 500 * scale }}>
          <div className="gui-scale" style={{ transform: `scale(${scale})` }}>
            <div className="gui" aria-label="天穹配置器交互模拟">
              <div className="title-row">
                <span className="title-icon">▣</span>
                <strong>天穹配置器</strong>
              </div>

              <div className="route-row panel">
                <span className="label">线路</span>
                <input aria-label="线路名称" value={lines[lineIndex]} onChange={(event) => renameLine(event.target.value)} />
                <span className="counter">{lineIndex + 1}/{lines.length}</span>
                <PixelButton title="第一条线路" disabled={lineIndex === 0} onClick={() => selectLine(0)}>│‹</PixelButton>
                <PixelButton title="上一条线路" disabled={lineIndex === 0} onClick={() => selectLine(lineIndex - 1)}>‹</PixelButton>
                <PixelButton title="下一条线路；末尾时创建" onClick={createNextLine}>›＋</PixelButton>
                <PixelButton title="最后一条线路" disabled={lineIndex === lines.length - 1} onClick={() => selectLine(lines.length - 1)}>›│</PixelButton>
                <PixelButton title="删除当前线路" disabled={lines.length === 1} className="danger" onClick={deleteLine}>×</PixelButton>
              </div>

              <div className="stats-row">
                <div>节点&nbsp; 6</div><div>抽取&nbsp; 2</div><div>存入&nbsp; 4</div>
              </div>

              <div className="connections panel">
                <div className="section-heading">
                  <span>线路连接面</span>
                  <div className="page-controls">
                    <span>{detailPage + 1}/{pageCount}</span>
                    <PixelButton disabled={detailPage === 0} onClick={() => setDetailPage(detailPage - 1)}>‹</PixelButton>
                    <PixelButton disabled={detailPage === pageCount - 1} onClick={() => setDetailPage(detailPage + 1)}>›</PixelButton>
                  </div>
                </div>
                <div className="grid header">
                  <span>设备</span><span>模式</span><span>物</span><span>流</span><span>能</span><span>优先</span><span>红石</span><span>坐标</span><span>维度</span>
                </div>
                {visibleEntries.map((entry, index) => (
                  <div className="grid entry" key={`${detailPage}-${index}`}>
                    <span><i className="device-cube">◆</i></span>
                    <span className={`direction ${entry.color}`}>{entry.dir}</span>
                    {entry.flags.map((flag, flagIndex) => <span key={flagIndex} className={flag ? "check" : "empty-box"}>{flag ? "✓" : ""}</span>)}
                    <span className="priority-box">{entry.priority}</span>
                    <span className={entry.redstone ? "torch lit" : "torch"}><i /></span>
                    <span className="position">{entry.pos}</span>
                    <span className={`world ${entry.world}`}>●</span>
                  </div>
                ))}
                {visibleEntries.length < 4 && Array.from({ length: 4 - visibleEntries.length }).map((_, index) => <div className="grid entry ghost" key={`ghost-${index}`} />)}
              </div>

              <div className="resource-row">
                {resourceOptions.map((resource) => (
                  <PixelButton
                    key={resource.key}
                    className="resource-button"
                    active={resources[resource.key]}
                    onClick={() => toggleResource(resource.key, resource.label)}
                  >
                    <span className={`resource-icon ${resource.key}`}>{resource.icon}</span>
                    <span>{resource.label}</span>
                  </PixelButton>
                ))}
              </div>

              <div className="bottom-row">
                <fieldset className="control-group redstone-control">
                  <legend>红石</legend>
                  <PixelButton className="redstone-cycle" onClick={cycleRedstone} title="点击循环红石模式">
                    <span className={`tiny-torch mode-${redstone}`} />
                    <span>{redstone}</span>
                  </PixelButton>
                </fieldset>
                <fieldset className="control-group">
                  <legend>留槽</legend>
                  <PixelButton onClick={(event) => adjust(setSlotLimit, "留槽", -1, event.shiftKey)}>−</PixelButton>
                  <output>{slotLimit}</output>
                  <PixelButton onClick={(event) => adjust(setSlotLimit, "留槽", 1, event.shiftKey)}>＋</PixelButton>
                </fieldset>
                <fieldset className="control-group">
                  <legend>优先</legend>
                  <PixelButton onClick={(event) => adjust(setPriority, "优先", -1, event.shiftKey)}>−</PixelButton>
                  <output>{priority}</output>
                  <PixelButton onClick={(event) => adjust(setPriority, "优先", 1, event.shiftKey)}>＋</PixelButton>
                </fieldset>
              </div>
              {showReference && <img className="reference-overlay" src="/art/configurator-reference.png" alt="美术交付稿参考叠加层" />}
            </div>
          </div>
        </div>

        <aside className="status-card">
          <span>LAST ACTION</span>
          <strong>{lastAction}</strong>
          <p>数值按钮支持 Shift + 点击，每次增减 10。</p>
          <dl>
            <div><dt>逻辑画布</dt><dd>520 × 500</dd></div>
            <div><dt>线路</dt><dd>{lineIndex + 1} / {lines.length}</dd></div>
            <div><dt>连接页</dt><dd>{detailPage + 1} / {pageCount}</dd></div>
            <div><dt>红石</dt><dd>{redstone}</dd></div>
          </dl>
        </aside>
      </section>
    </main>
  );
}
