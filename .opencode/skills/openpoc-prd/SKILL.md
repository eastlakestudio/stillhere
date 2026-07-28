---
name: openpoc-prd
description: 【循环 1】PRD 阶段 — 审查 specs/ 下原始需求、规范、架构约束、UI 设计、会议纪要，输出可编辑审查报告，再由 LLM 基于审查产物生成完整 PRD。子命令：openpoc prd review [req|std|tech|ui|meet|all] 和 openpoc prd gen。适用场景：循环 1 全阶段（审查 → 生成 PRD）。
---

# openpoc prd

循环 1：PRD 阶段（人机协同）。先逐目录审查 specs/ 材料，确认后由 LLM 基于审查产物生成完整 PRD 文档。

## 子命令

### prd review — 审查材料

| 命令 | 来源目录 | 输出文件 |
|------|----------|----------|
| `openpoc prd review req` | specs/01_initial_demands | artifacts/1.1_review_requirement.md |
| `openpoc prd review std` | specs/02_standards_and_regs | artifacts/1.2_review_standard.md |
| `openpoc prd review tech <终端>` | specs/03_arch_constraints | artifacts/\<terminal\>/1.3_review_tech.md（空目录启动逐步问诊向导） |
| `openpoc prd review ui <终端>` | specs/04_ui_guidelines | artifacts/\<terminal\>/1.4_review_ui.md |
| `openpoc prd review meet` | specs/05_meeting_minutes | artifacts/1.5_review_meeting.md |
| `openpoc prd review all [终端]` | 以上全部串联 | 全部审查文件 + 提示执行 prd gen |

不带子命令显示交互菜单，列出所有审查项供逐个选择，不会一次性全执行。

### prd gen — 生成 PRD

| 命令 | 输入 | 输出 | 说明 |
|------|------|------|------|
| `openpoc prd gen` | artifacts/ 全部审查产物 + PRD 模板 | artifacts/1.0_PRD.md | CLI 扫描审查产物并输出 LLM 指令，AI 读取模板和审查产物，生成完整 PRD（全部 8 章） |

## 审查文件结构

每个 artifacts 文件包含四个章节：

- **📂 发现文件** — 文件清单（名称 + 大小）
- **📌 审查要点** — 状态表格（⬜ → ✅ → ❌），逐条确认。**每条审查要点必须附带「参考文档」清单，列出得出该结论所依据的源文件名**，用户据此判断 AI 结论是否可靠。

  格式示例：
  > 审查要点 #5：合同依据
  > 📄 参考文档：01_建设方案.docx, 03_招标文件.pdf
  > ...
- **🖥️ 终端与平台** — 识别涉及的终端（Web/移动端/大屏/桌面）
- **🗺️ 终端 × 业务场景** — 每个终端对应各自的业务场景矩阵（终端列 + 场景名列 + 说明 + 来源）。场景必须按端拆分，不要把所有场景平铺到一个列表

## prd gen 工作原理

CLI 扫描 `artifacts/` 下所有审查产物（含终端子目录），收集 🖥️终端、🗺️场景、📌要点摘要，输出 LLM 指令。AI 读取 PRD 模板和审查产物，完整填充所有章节：

| 数据来源 | 填充到 PRD 章节 |
|----------|-----------------|
| 🖥️ 终端与平台 | 5.3 兼容性要求 |
| 🗺️ 终端 × 业务场景 | 2.2.3 端与场景 |
| 📌 审查要点 | 3.1 功能清单 |
| 审查产物全文 | 1.1-1.3 产品概述、2.2.1 系统用户、2.2.2 对接系统、2.2.4 场景详述、3.2 功能详述、第五章 非功能需求、第八章 依赖与风险 |

此外自动生成：6.1 系统架构图（Mermaid）、7.1 核心业务流程图（Mermaid）。

**由 LLM 完整生成。** 审查产物表格必须已被填满，否则 PRD 质量无法保证。

## 智能特性

- **目录为空时**：可在 Qoder 聊天框直接上传文件
- **review ui 目录为空**：弹出内置模板多选（PC/移动端/大屏/自定义）
- **review tech 目录为空**：启动逐步问诊向导（架构模式→前端框架→UI组件库→地图引擎→后端），无需切换命令
- **多标签熔断**：检测到大屏/PC/移动端 2+ 标签时自动引导多端协同配置

> 💡 审查文件就是合同：用户直接编辑（改状态/填终端/加备注），确认后回复 "y" 进入下一步。

## After running

PRD 生成后无需额外操作。用户审核 `artifacts/1.0_PRD.md`，确认无误后进入循环 2（Draft）。
