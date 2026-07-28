---
name: openpoc-ued
description: 【循环 3】高保真实现 — 人机循环：基于 Draft 线框升级为真实组件的高保真 HTML 应用。用法：openpoc ued <终端> [<场景名>]。proposal → GO → HTML 人机循环，逐场景迭代直到满意。适用场景：UED 阶段 — 高保真原型交付。
---

# openpoc ued

循环 3 UED — 人机循环，高保真迭代交付。

## 命令

| 命令 | 输出 | 说明 |
|------|------|------|
| `openpoc ued <终端>` | `prototype/<terminal>/index.html` | 导航壳 + 场景地图：基于 PRD 提取场景 → 生成高保真框架页（Vue 3 + 组件库 + 地图引擎 + Router + Pinia） |
| `openpoc ued <终端> <场景名>` | `ued/<terminal>/{场景}.proposal.md` → `prototype/<terminal>/{场景}.html` | 场景高保真：Step 1 生成 proposal（页面结构/组件选型/数据绑定/地图配置），Step 2 说 `GO` 后生成高保真 HTML |
| `openpoc ued build <终端> [场景]` | `prototype/<terminal>/{场景}.html` | 跳过 GO 对话：读取已有 proposal 直接生成高保真 HTML。不指定场景则批量生成所有待 build 场景 |
| `openpoc ued gen` | `artifacts/2.0_UED.md` | CLI 扫描产物并输出 LLM 指令，AI 基于 ued/ + prototype/ 产物填充 UED 设计文档（终端场景/组件选型/地图配置/Mock方案/交互规范） |
| `openpoc ued server` | `prototype/server/` | 生成 POC 后端服务（零依赖 Node HTTP + Mock API + SSE Event Bus），多端共用，AI 基于 specs/06_supplementary/ 和 Draft/UED Mock 数据填充 schema.json 和 Mock 数据 |
| `openpoc ued` | 交互菜单 | 选终端 → 导航壳 或 场景（需指定场景名） |

## 人机循环

```
① AI 生成 proposal.md（页面结构、组件选型、数据绑定、地图配置）
    ↓
② 工程师审核 proposal，直接编辑文件修改方案
    ↓
③ 工程师说 "GO"
    ↓
④ AI 重新读取 proposal，生成高保真 HTML
    ↓
⑤ 工程师查看效果，发现问题
    ↓
⑥ 两种修正路径：
    ├─ 路径 A：编辑 proposal.md → 说 "GO" → 重新生成 HTML
    └─ 路径 B：直接发提示词 → AI 修改 HTML → AI 同步更新 proposal.md
    ... 循环迭代直到评审确认
```

## 提示词修改 & proposal 同步

当用户通过提示词（而非编辑 proposal.md）要求修改已有高保真 HTML 时，AI 必须执行双向同步：

1. **读取当前状态**：先读取 `prototype/<终端>/{场景}.html` 和 `ued/<终端>/{场景}.proposal.md`
2. **修改 HTML**：按用户提示词修改 HTML 中的组件/数据/交互/地图配置
3. **同步 proposal.md**：将 HTML 中的变更反向写入 proposal.md，确保：
   - 页面结构与实际 HTML 布局一致
   - 组件选型与 HTML 实际使用的组件一致
   - 数据绑定/Mock 方案与 data() 实际结构一致
   - 地图配置与 HTML 中地图初始化参数一致
4. **保持一致性**：proposal.md 始终是 HTML 的「设计真相」，下次说 GO 时以最新 proposal.md 为准

> ⚠️ 如果用户同时编辑了 proposal.md 并发了提示词，以 proposal.md 为准（文件即合同原则）。

## 升级内容

| 组件 | Draft | UED |
|------|-------|-----|
| 地图 | 占位 Card | Cesium/Leaflet/百度/高德 真实地图 |
| 组件库 | 裸 HTML | Element Plus / Naive UI |
| 路由 | 单页面 | Vue Router |
| 状态 | data() | Pinia store |

## 🗺️ 地图库选择

自动扫描 `specs/03_arch_constraints/` 和 `artifacts/` 审查报告检测地图库关键词，已指定则自动确认，未指定则弹出交互选择菜单（1-4）。

## After running

逐页面确认，循环迭代直到满意。

## ⚠️ 深度 Mock Data 规范

生成高保真 HTML 时，Mock 数据必须遵循以下原则：

- **真实业务实体**：使用 PRD 中识别的真实业务对象名称（如化工项目用"苯乙烯"而非"介质A"）
- **行业逻辑数值**：数值范围符合行业标准（如浓度 0-100ppm，非随机 0-99999）
- **逼真时空数据**：经纬度使用项目所在地真实地理范围，时间序列带合理波动趋势
- **数据质感优先**：用真实的数据密度和变化规律掩盖前端"静态外壳"的本质

> 示例：大亚湾化工项目 → 介质名=苯乙烯/乙二醇，浓度=0-100ppm，坐标=21.4°N~22.6°N, 113.5°E~114.7°E

## 后端服务 (openpoc ued server)

在 UED 阶段后期，通过 `openpoc ued server` 生成 POC 迷你后端（多端共用），统一解决三个问题：
- **Cesium 本地托管**：Node HTTP 静态文件服务，解决 Cesium 必须 localhost 运行的限制
- **Mock API 数据服务**：基于 schema.json 自动暴露 `/api/:entity` REST 路由，数据从 `src/services/*.json` 加载
- **SSE Event Bus**：Server-Sent Events 零依赖实时消息推送，支持大屏/PC/移动端联动演示

### LLM 行为

执行 `openpoc ued server` 后，AI Agent 必须从以下两个来源提取数据模型：

**来源 1: `specs/06_supplementary/`** — 用户上传的数据字典、接口文档、字段规范
**来源 2: Draft/UED 阶段已生成的 Mock 数据** — `draft/<终端>/*.html` 的 data()、`ued/<终端>/*.proposal.md` 的 Mock 方案、`prototype/<终端>/*.html` 的 data() 或 fetch 调用

合并后执行：
1. 填充 `prototype/server/schema.json`，每个实体定义 fields（类型/必填/Mock 示例/来源）和 sharedAcross（跨端共享列表）
2. 为每个实体生成 `prototype/server/src/services/{entity}.json` Mock 数据文件
3. 更新高保真 HTML 页面：`data()` 硬编码 → `fetch('/api/{entity}')`
4. 多端联动场景：注入 `new EventSource('/api/events')` 监听 + `fetch POST /api/events` 发送
