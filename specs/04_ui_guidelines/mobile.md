# 移动APP UI 规范（内置模板）

> 来源：openpoc review ui 内置模板
> 终端：移动APP（Mobile）
> 生成时间：2026-07-20

---

## Draft 阶段样式

> 以下第 1-6 章为 **Draft 线框阶段**的视觉规范，采用轻量卡片风（Light Card），白色为主、简约线框，聚焦信息架构和交互流程。

---

## 1. 布局规范

| 区域 | 规格 | 说明 |
|------|------|------|
| 顶部导航栏 | 高度 44-48px，全宽 | 返回按钮 + 页面标题 + 右侧操作图标 |
| 搜索栏 | 高度 36-44px，通栏或 Card 内 | 搜索框 + 筛选按钮，可折叠 |
| 内容区 | 剩余高度，纵向滚动 | Card 列表 / 详情表单 / 地图视图 |
| 底部 Tab 栏 | 高度 50-56px + 安全区 | 3-5 个 Tab 图标 + 文字，可带 Badge，注意位置略高一些，避免手机系统本身的上滑横条 |
| 悬浮按钮 | 右下角 56×56px 圆形 | 主要操作入口（新建工单/扫码等） |

```
┌──────────────────────────┐
│  状态栏 (时间/电量/信号)    │
├──────────────────────────┤
│  顶部导航栏 (48px)         │
│  ← 返回    标题    ⋮ 更多  │
├──────────────────────────┤
│  [🔍 搜索关键词]  筛选 ▼   │
├──────────────────────────┤
│                          │
│   ┌──────────────────┐   │
│   │ Card 1            │   │
│   └──────────────────┘   │
│   ┌──────────────────┐   │
│   │ Card 2            │   │
│   └──────────────────┘   │
│                          │
│              ┌────┐      │
│              │ +  │ 悬浮  │
│              └────┘      │
├──────────────────────────┤
│ 底部 Tab 栏 (50px)        │
│  [首页] [工单] [消息] [我的]│
│  安全区 (Home Indicator)  │
└──────────────────────────┘
```

## 2. 视觉风格

- **设计语言**：轻量卡片风（Light Card），白色为主
- **背景色**：页面底色 #f5f6fa，Card 底色 #ffffff，分割线 #f0f0f0
- **主色调**：品牌蓝 #1677ff（可替换为项目品牌色）
- **功能色**：成功绿 #52c41a、警告橙 #fa8c16、危险红 #ff4d4f、信息蓝 #1677ff
- **字体**：标题 16-18px（加粗），正文 14px，辅助文字 12px（#999）
- **圆角**：Card 8-12px，按钮 4-8px，输入框 4px
- **阴影**：Card 阴影 0 1px 3px rgba(0,0,0,0.08)，无大面积阴影
- **触控区域**：最小点击区域 44×44px（符合 iOS/Android 规范）
- **禁用**：小于 12px 的字体、过小点击区域、大面积渐变背景

## 3. 组件规范

| 组件 | 说明 |
|------|------|
| Card | 白色卡片 + 8-12px 圆角 + 微弱阴影，左右 16px 边距，上下 8px 间距 |
| List Item | 左侧 icon/头像 + 中间标题/副标题 + 右侧箭头/时间，高度 60-72px |
| Tab | 顶部胶囊式切换或底部图标式 Tab，active 加粗变色，带下划线或背景色 |
| Button | 主按钮通栏 40-48px 高，圆角 4-8px；次按钮线框样式；禁用态灰色 |
| Input/Textarea | 通栏或 Card 内，圆角 4px，placeholder 灰色，聚焦边框变蓝 |
| Select/Picker | 底部弹出 Picker 选择器（滚轮式），含确定/取消按钮 |
| Modal/Dialog | 居中弹窗，圆角 12px，半透明黑色遮罩，含标题/内容/操作按钮 |
| Toast | 居中黑色半透明提示条，1.5-2s 自动消失 |
| Loading | 居中旋转加载图标 + "加载中..." 文字，或骨架屏（Skeleton） |
| SwipeAction | 列表项左滑出现操作按钮（编辑/删除），红色为危险操作 |
| Badge | 右上角红点或数字角标，Tab 图标和列表项均可使用 |
| Image | 圆角 4-8px，加载中显示灰色占位图 + 渐进加载 |
| Map | 嵌入地图组件（高德/腾讯/百度），支持标记点、路径规划、定位 |

## 4. 分辨率与兼容性

| 指标 | 要求 |
|------|------|
| 基准分辨率 | 375×812 (iPhone X / 11 Pro) |
| 适配范围 | 宽度 320-428px（覆盖 iPhone SE ~ iPhone 15 Pro Max） |
| 操作系统 | iOS 15+ / Android 12+ |
| 浏览器/WebView | Safari / Chrome WebView / 微信内置浏览器 |
| 网络 | 支持 4G/5G/WiFi，弱网环境需有离线缓存策略 |
| 横竖屏 | 默认竖屏（Portrait），地图/视频场景支持横屏 |

## 5. 交互规范

- **导航**：底部 Tab 切换一级页面，顶部返回按钮返回上级，右滑手势返回（iOS）
- **列表**：下拉刷新（带刷新动画）+ 上滑触底加载更多（"加载中..." 或 "— 没有更多了 —"）
- **长按**：列表项长按弹出操作菜单（复制/删除/分享）
- **左滑**：列表项左滑露出操作按钮（编辑/删除/标为已读）
- **点击反馈**：所有可点击元素需有点击态（透明度变化 / 背景色变化），禁用双击缩放
- **表单提交**：底部固定"提交"按钮，点击后 loading + 防重复提交，成功 Toast 后返回上级
- **空状态**：无数据时展示空状态插画 + 引导文案 + 操作按钮（如"去创建"）
- **错误状态**：网络错误展示重试按钮 + 提示文案；接口错误展示 Toast 错误信息
- **键盘**：输入框聚焦时页面自动上移避免遮挡，带"完成"按钮收起键盘
- **推送通知**：支持 Push 通知跳转到对应详情页（需原生配合）
- **扫码**：调用相机扫一扫（条形码/二维码），结果自动回填或跳转

## 6. 手机外框（Phone Frame）

> ⚠️ **强制要求**：移动端 Draft 线框和 UED 高保真 HTML 页面外层必须包裹手机外框，模拟在真机中运行的视觉效果，方便演示和评审。以安卓手机为基准，圆角全面屏风格。

### 外框规格

| 参数 | 值 |
|------|-----|
| 外框宽度 | 390px（含边框） |
| 屏幕区域 | 375×812（内嵌内容区） |
| 外框圆角 | 36px（安卓全面屏典型圆角） |
| 外框颜色 | #1a1a1a（深空灰，模拟手机中框） |
| 外框边框 | 3px solid #333 |
| 顶部状态栏 | 24px 高通栏黑底，居中显示时间（白色 12px），信号/电量图标居右 |
| 顶部摄像头孔 | 居中圆点 8×8px，#333，模拟安卓挖孔屏 |
| 底部导航条 | 白色横条 134×5px，border-radius 2.5px，模拟安卓手势导航条 |
| 外框阴影 | 0 20px 60px rgba(0,0,0,0.3)，营造悬浮立体感 |
| 背景 | 页面背景使用渐变或中性灰（#e8eaed），衬托手机外框 |

### 实现方式

**Draft 线框阶段**：
- 页面主体外层用固定宽度 390px 的 `div.phone-frame` 包裹
- 内部 375×812 区域为 `div.phone-screen`，所有线框内容在其内渲染
- 使用纯 CSS 绘制外框，不依赖图片

**UED 高保真阶段**：
- 同样使用 CSS 手机外框包裹
- 外框尺寸和样式与 Draft 阶段一致
- 如有条件可使用更精致的渐变金属质感边框

### CSS 参考

```css
/* 页面背景 */
body {
  margin: 0;
  padding: 40px 0;
  background: linear-gradient(135deg, #e8eaed 0%, #d5d8dc 100%);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  min-height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* 手机外框 */
.phone-frame {
  width: 390px;
  background: #1a1a1a;
  border: 3px solid #333;
  border-radius: 36px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3), inset 0 0 10px rgba(255,255,255,0.05);
  padding: 10px 7px;
  position: relative;
}

/* 顶部状态栏 */
.phone-status-bar {
  width: 375px;
  height: 24px;
  margin: 0 auto 0;
  background: #000;
  border-radius: 28px 28px 0 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-sizing: border-box;
  position: relative;
}
.phone-status-bar .time {
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  margin-left: 16px;
}
.phone-status-bar .icons {
  color: #fff;
  font-size: 10px;
  display: flex;
  gap: 6px;
  align-items: center;
}

/* 摄像头挖孔 */
.phone-status-bar::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 8px;
  height: 8px;
  background: #333;
  border-radius: 50%;
  border: 1px solid #555;
}

/* 屏幕区域 */
.phone-screen {
  width: 375px;
  height: 780px;
  margin: 0 auto;
  background: #fff;
  border-radius: 0 0 28px 28px;
  overflow-y: auto;
  overflow-x: hidden;
  position: relative;
}

/* 底部导航条 */
.phone-nav-bar {
  width: 375px;
  height: 8px;
  margin: 0 auto;
  background: #1a1a1a;
  display: flex;
  align-items: center;
  justify-content: center;
}
.phone-nav-bar::after {
  content: '';
  width: 134px;
  height: 4px;
  background: rgba(255,255,255,0.25);
  border-radius: 2px;
}
```

```
┌──────────────────────────────────┐
│         (页面背景 / 渐变)          │
│                                  │
│   ╭──────────────────────╮      │
│   │  █ 状态栏 12:30  📶🔋 │      │
│   │  ╭────────────────╮  │      │
│   │  │                │  │      │
│   │  │   375 × 780    │  │ ← 屏幕│
│   │  │   内容区域      │  │      │
│   │  │                │  │      │
│   │  ╰────────────────╯  │      │
│   │       ━━━━━          │ ← 导航条│
│   ╰──────────────────────╯      │
│       安卓外框 (390px)            │
└──────────────────────────────────┘
```

> 💡 外框包裹的手机页面可直接在 PC 浏览器中演示，视觉效果接近真机截图。评审时无需部署到移动设备即可预览移动端效果。

---

## 7. UED 阶段 — Material Design 3

> 以下为 **UED 高保真阶段**的视觉规范，采用 Google Material Design 3（Material You）设计系统。Draft 线框确认后，UED 阶段必须按此规范升级为 MD3 风格的完整组件。

### 7.1 MD3 设计令牌（Design Tokens）

#### 配色系统（Dynamic Color）

MD3 基于源色（Source Color）自动生成 tonal palette，以下为默认基线：

| 角色 | 参考值 | 用途 |
|------|--------|------|
| Primary | #6750A4 | 主按钮、激活态图标、导航栏标题 |
| On Primary | #FFFFFF | 主色上的文字/图标 |
| Primary Container | #EADDFF | 次要容器背景（如选中态 Chip、FAB 容器） |
| On Primary Container | #21005D | Primary Container 上的文字 |
| Secondary | #625B71 | 次要按钮、筛选标签 |
| On Secondary | #FFFFFF | |
| Secondary Container | #E8DEF8 | 次要容器 |
| Tertiary | #7D5260 | 互补强调色 |
| Error | #B3261E | 错误/删除操作 |
| Surface | #FEF7FF | 页面背景 |
| Surface Variant | #E7E0EC | 卡片/列表项背景 |
| Surface Container | #F3EDF7 | 导航栏/底部栏背景 |
| Outline | #79747E | 边框/分割线 |
| On Surface | #1C1B1F | 正文文字 |
| On Surface Variant | #49454F | 辅助文字 |
| Inverse Surface | #313033 | 深色背景（Snackbar/Dialog 遮罩） |
| Scrim | rgba(0,0,0,0.4) | Modal 遮罩 |

> 💡 实际项目中可根据品牌色替换 Primary 源色，其余 tonal palette 自动派生。

#### 字体排版（Typography Scale）

| 样式 | 字号 | 字重 | 行高 | 用途 |
|------|------|------|------|------|
| Display Large | 57px | 400 | 64px | 欢迎页大标题 |
| Display Medium | 45px | 400 | 52px | |
| Headline Large | 32px | 400 | 40px | 页面主标题 |
| Headline Medium | 28px | 400 | 36px | 卡片大标题 |
| Title Large | 22px | 400 | 28px | 导航栏标题 |
| Title Medium | 16px | 500 | 24px | 列表项标题、按钮文字 |
| Title Small | 14px | 500 | 20px | Tab 标签 |
| Body Large | 16px | 400 | 24px | 正文 |
| Body Medium | 14px | 400 | 20px | 辅助说明 |
| Body Small | 12px | 400 | 16px | 注释 |
| Label Large | 14px | 500 | 20px | 按钮文字 |
| Label Medium | 12px | 500 | 16px | Chip 标签、Badge |
| Label Small | 11px | 500 | 16px | 小标签 |

#### 圆角（Shape Scale）

| 级别 | 值 | 适用组件 |
|------|-----|----------|
| None | 0px | 分割线 |
| Extra Small | 4px | Chip、Badge |
| Small | 8px | Card、TextField、Button |
| Medium | 12px | Dialog、Bottom Sheet |
| Large | 16px | 大卡片、Menu |
| Extra Large | 28px | FAB、屏幕圆角 |
| Full | 50% | 圆形头像、圆形 FAB |

#### 海拔与阴影（Elevation）

MD3 使用 **tonal elevation**（色阶高程）替代传统阴影 —— 海拔越高，表面颜色越接近 Primary：

| 级别 | 阴影 | 用途 |
|------|------|------|
| Level 0 | 无阴影，Surface 底色 | 页面背景 |
| Level 1 | 0 1px 2px rgba(0,0,0,0.08) | Card、TextField、Bottom App Bar |
| Level 2 | 0 1px 2px rgba(0,0,0,0.12) | Top App Bar、Navigation Bar |
| Level 3 | 0 4px 8px rgba(0,0,0,0.12) | FAB（悬浮）、Modal、Menu |
| Level 4 | 0 8px 16px rgba(0,0,0,0.16) | Dialog、Navigation Drawer |
| Level 5 | 0 12px 24px rgba(0,0,0,0.2) | Bottom Sheet（展开态） |

### 7.2 MD3 组件规范

| 组件 | MD3 规范 |
|------|----------|
| **Top App Bar** | 高度 64px（大标题）/ 56px（标准），Surface 或 Primary 背景。左侧导航图标 + 标题（Title Large），右侧最多 3 个操作图标 |
| **Navigation Bar** | 底部 80px（含安全区），3-5 个目标。图标 24×24 + Label Medium 文字。激活态使用 Primary 色 + 图标填充，非激活态 Outline 色 |
| **Navigation Rail** | 侧栏 80px 宽（大屏/平板），结构同 Nav Bar 但垂直排列 |
| **FAB（悬浮按钮）** | 56×56px 圆形，Primary Container 或 Primary 背景，Level 3 阴影。可扩展为 Small FAB (40×40) 或 Large FAB (96×40 含文字) |
| **Card** | 3 种类型 — Elevated（Level 1 阴影）、Filled（Surface Variant 底色）、Outlined（Outline 边框）。圆角 Small(8px)，内边距 16px |
| **List Item** | 3 行高 — 单行 56px / 双行 72px / 三行 88px。前置 Leading 元素（icon/avatar/checkbox），尾随 Trailing 元素（箭头/开关/chip） |
| **Chip** | 4 种样式 — Assist（含 icon）、Filter（切换态）、Input（含删除按钮）、Suggestion（推荐）。圆角 Extra Small(4px)，Label Medium 文字 |
| **Button** | 5 种变体 — Filled（Primary 实心）、Filled Tonal（Primary Container）、Outlined（Outline 边框）、Text（无背景）、Elevated（Level 1 阴影）。最小高度 40px，圆角 Full(20px) |
| **TextField** | 2 种样式 — Filled（Surface Variant 底色）和 Outlined（Outline 边框）。圆角 Small(4px) 顶部，标签上移动画。Supporting Text 放错误/提示信息 |
| **Dialog** | 居中弹窗，圆角 Medium(12px)，Surface 背景。标题（Headline Small）+ 正文（Body Medium）+ 操作按钮（右对齐）。Scrim 遮罩 |
| **Bottom Sheet** | 底部滑出，圆角 Extra Large(28px) 顶部。Drag Handle（32×4px 灰色横条）。展开至半屏或全屏 |
| **Snackbar** | 底部居中，Inverse Surface 背景 + On Inverse Surface 文字。Label Large 单行文字 + 可选操作按钮（Primary 色）。4s 自动消失 |
| **Progress Indicator** | 2 种 — Linear（线形，顶部/底部）和 Circular（圆形，居中）。均可确定或不确定状态 |
| **Tabs** | 3 种 — Primary（顶部切换）、Secondary（二级筛选）、Scrollable（横向滚动）。激活态 Primary 色 + 底部指示线 3px，非激活态 On Surface Variant |
| **Badge** | 2 种 — 小圆点 6px 和数字标签 16px 高。Error 色背景，On Error 色文字 |
| **Switch / Checkbox** | Switch 圆角 Full，Primary 色激活态；Checkbox 圆角 Extra Small(4px)，Primary 色勾选。均带微动效（ripple） |
| **Time Picker** | 圆形时钟表盘或数字输入，Surface 背景，Primary 色选中指针 |
| **Date Picker** | 月份网格布局，Primary 色选中日期，圆角 Full。顶部切换年/月 |
| **Search** | 3 阶段 — 折叠态（搜索图标）、展开态（Search Bar，全宽 TextField）、结果态（搜索建议列表 + 返回按钮） |
| **Divider** | 2 种 — Full Bleed（全宽 0.5px Outline 色）和 Inset（左侧缩进 16/72px 对齐列表项文字）。垂直间距 8px |

### 7.3 MD3 交互规范

- **Ripple 水波纹**：所有可点击元素触发圆形波纹扩散动画，Primary 色 12% 透明度，从点击位置扩散至组件边缘
- **页面过渡**：Shared Axis 转场（水平滑动 200ms），Fade Through 转场（淡入淡出 300ms），Container Transform 转场（共享元素变形）
- **手势**：Swipe to Dismiss（列表项滑动删除/归档），Pull to Refresh（下拉刷新带 MD3 环形动画），Long Press（长按弹出 Context Menu）
- **滚动行为**：Top App Bar 上滑收起/下滑展开，FAB 滚动时隐藏/停止时显示
- **焦点态**：Outline 色焦点框（键盘导航），Primary 色 12% 覆盖（触控反馈）
- **加载状态**：Skeleton（骨架屏，Surface Variant 底色 + Shimmer 动画）优先于 Circular Progress
- **空状态**：居中插图 + Headline Small 标题 + Body Medium 说明 + Filled Tonal Button 操作引导
- **错误状态**：Error 色 TextField 边框 + Supporting Text 错误信息；Dialog 确认重试

### 7.4 MD3 CSS 变量参考

```css
:root {
  /* MD3 Color Tokens */
  --md-sys-color-primary: #6750A4;
  --md-sys-color-on-primary: #FFFFFF;
  --md-sys-color-primary-container: #EADDFF;
  --md-sys-color-on-primary-container: #21005D;
  --md-sys-color-secondary: #625B71;
  --md-sys-color-on-secondary: #FFFFFF;
  --md-sys-color-secondary-container: #E8DEF8;
  --md-sys-color-tertiary: #7D5260;
  --md-sys-color-error: #B3261E;
  --md-sys-color-surface: #FEF7FF;
  --md-sys-color-surface-variant: #E7E0EC;
  --md-sys-color-surface-container: #F3EDF7;
  --md-sys-color-outline: #79747E;
  --md-sys-color-on-surface: #1C1B1F;
  --md-sys-color-on-surface-variant: #49454F;
  --md-sys-color-scrim: rgba(0,0,0,0.4);

  /* MD3 Typography */
  --md-sys-typescale-headline-large: 400 32px/40px 'Roboto', sans-serif;
  --md-sys-typescale-title-large: 400 22px/28px 'Roboto', sans-serif;
  --md-sys-typescale-title-medium: 500 16px/24px 'Roboto', sans-serif;
  --md-sys-typescale-body-large: 400 16px/24px 'Roboto', sans-serif;
  --md-sys-typescale-body-medium: 400 14px/20px 'Roboto', sans-serif;
  --md-sys-typescale-label-large: 500 14px/20px 'Roboto', sans-serif;
  --md-sys-typescale-label-medium: 500 12px/16px 'Roboto', sans-serif;

  /* MD3 Shape */
  --md-sys-shape-corner-extra-small: 4px;
  --md-sys-shape-corner-small: 8px;
  --md-sys-shape-corner-medium: 12px;
  --md-sys-shape-corner-large: 16px;
  --md-sys-shape-corner-extra-large: 28px;
  --md-sys-shape-corner-full: 50%;

  /* MD3 Elevation */
  --md-sys-elevation-0: none;
  --md-sys-elevation-1: 0 1px 2px rgba(0,0,0,0.08), 0 1px 3px rgba(0,0,0,0.06);
  --md-sys-elevation-2: 0 1px 2px rgba(0,0,0,0.12), 0 2px 6px rgba(0,0,0,0.08);
  --md-sys-elevation-3: 0 4px 8px rgba(0,0,0,0.12), 0 1px 3px rgba(0,0,0,0.08);
  --md-sys-elevation-4: 0 8px 16px rgba(0,0,0,0.16), 0 2px 6px rgba(0,0,0,0.08);
  --md-sys-elevation-5: 0 12px 24px rgba(0,0,0,0.2), 0 4px 8px rgba(0,0,0,0.08);
}
```

> 💡 UED 生成高保真页面时，AI 必须基于以上 MD3 令牌和组件规范生成。Draft 的白色简约线框仅在信息架构阶段使用；进入 UED 后须全面升级为 MD3 风格组件。
