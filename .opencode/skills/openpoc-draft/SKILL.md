---
name: openpoc-draft
description: 【循环 2】线框图 — 人机循环：基于 PRD 场景矩阵逐场景生成线框 HTML。用法：openpoc draft <终端> <场景名>。白色面板简约风，proposal → GO → HTML 循环迭代，聚焦信息架构不纠缠视觉细节。适用场景：Draft 阶段 — 线框图快速交付。
---

# openpoc draft

循环 2 Draft — 人机循环，逐场景生成线框 HTML。

## 命令

| 命令 | 作用 | 流程 |
|------|------|------|
| `openpoc draft` | 交互菜单 | 选终端 → 生成框架 或 选场景 |
| `openpoc draft <终端>` | 框架壳 + 场景地图 | 基于 PRD 提取场景 → 生成 index.html |
| `openpoc draft <终端> <场景名>` | 场景线框页面 | proposal → GO → HTML → 迭代 |
| `openpoc draft build <终端> [场景]` | 跳过 GO 对话直接生成 HTML | 读取已有 proposal.md → 生成 {场景}.html。不指定场景则批量生成所有待 build 场景 |

## 人机循环

```
① proposal.md  ← AI 生成方案（布局/字段/交互）
    ↓ 用户审核，直接编辑文件修改方案
② 用户说 "GO"
    ↓
③ {场景}.html  ← AI 按 proposal 生成线框
    ↓ 用户/客户操作体验，沟通反馈
④ 两种修正路径：
    ├─ 路径 A：编辑 proposal.md → 说 "GO" → 重新生成 HTML
    └─ 路径 B：直接发提示词 → AI 修改 HTML → AI 同步更新 proposal.md
    ... 循环迭代直到确认
⑤ 输出最终线框，移交 UED
```

## 提示词修改 & proposal 同步

当用户通过提示词（而非编辑 proposal.md）要求修改已有 HTML 时，AI 必须执行双向同步：

1. **读取当前状态**：先读取 `draft/<终端>/{场景}.html` 和 `draft/<终端>/{场景}.proposal.md`
2. **修改 HTML**：按用户提示词修改 HTML 中的布局/字段/交互
3. **同步 proposal.md**：将 HTML 中的变更反向写入 proposal.md，确保：
   - 卡片布局草图（ASCII）与实际 HTML 结构一致
   - 字段清单表格与实际 data() 结构一致
   - 交互清单与实际按钮/事件一致
4. **保持一致性**：proposal.md 始终是 HTML 的「设计真相」，下次说 GO 时以最新 proposal.md 为准

> ⚠️ 如果用户同时编辑了 proposal.md 并发了提示词，以 proposal.md 为准（文件即合同原则）。

## ⚠️ Draft 视觉规范

```
✅ 白色背景 + 1px 实线边框 + #333 加粗标题
✅ 按钮用线框样式（border + 白底）
✅ 表格用简单边框，表头浅灰底
✅ 地图/图片用虚线边框占位 Card（.map-placeholder / .img-placeholder）
❌ 禁用 box-shadow、border-radius、渐变色、彩色按钮/标签
```

## After running

逐项确认，全部确认后进入 Phase 3。
