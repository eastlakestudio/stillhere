export function landingPage(): string {
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>安好 Still Here — 独居安全守护</title>
  <meta name="description" content="安好 Still Here 是一款独居安全监测 App。多维度感知活动状态，定时上报心跳，长时间无活动自动告警家人，让关心永不缺席。">
  <script src="https://cdn.tailwindcss.com"></script>
  <style>
    html { scroll-behavior: smooth; }
    .gradient-text {
      background: linear-gradient(135deg, #6366f1, #8b5cf6, #a855f7);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }
    .hero-gradient {
      background: linear-gradient(135deg, #1e1b4b 0%, #312e81 50%, #3730a3 100%);
    }
    .feature-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 20px 40px rgba(99,102,241,0.15);
    }
    .privacy-section h3 {
      scroll-margin-top: 2rem;
    }
    .privacy-section h4 {
      scroll-margin-top: 2rem;
    }
  </style>
</head>
<body class="bg-slate-50 text-slate-800 antialiased">

  <!-- ──────────────── Hero ──────────────── -->
  <section class="hero-gradient text-white">
    <div class="max-w-4xl mx-auto px-6 py-20 md:py-32 text-center">
      <div class="inline-flex items-center gap-2 bg-white/10 rounded-full px-4 py-1.5 text-sm mb-6 backdrop-blur">
        <span class="w-2 h-2 bg-green-400 rounded-full animate-pulse"></span>
        持续守护 · 安心每一天
      </div>
      <h1 class="text-4xl md:text-6xl font-extrabold tracking-tight mb-6">
        <span class="gradient-text">安好</span> Still Here
      </h1>
      <p class="text-lg md:text-xl text-indigo-200 max-w-2xl mx-auto mb-10 leading-relaxed">
        一款为独居者设计的安全监测 App。多维度感知活动状态，定时向云端上报心跳，
        长时间无活动时自动告警关心你的家人。让关心，永不缺席。
      </p>
      <div class="flex flex-wrap justify-center gap-4">
        <a href="#download" class="inline-flex items-center gap-2 bg-white text-indigo-700 font-semibold px-8 py-3.5 rounded-xl hover:bg-indigo-50 transition shadow-lg shadow-indigo-500/25">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"/></svg>
          下载 App
        </a>
        <a href="#features" class="inline-flex items-center gap-2 border border-white/30 text-white font-semibold px-8 py-3.5 rounded-xl hover:bg-white/10 transition">
          了解功能
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
        </a>
      </div>
    </div>
  </section>

  <!-- ──────────────── Features ──────────────── -->
  <section id="features" class="max-w-6xl mx-auto px-6 py-20 md:py-28">
    <div class="text-center mb-16">
      <h2 class="text-3xl md:text-4xl font-bold mb-4">多维度守护，滴水不漏</h2>
      <p class="text-slate-500 max-w-xl mx-auto">六种监测器协同工作，从位置、运动、充电到应用使用，全方位感知你的活动状态。</p>
    </div>
    <div class="grid md:grid-cols-3 gap-6">
      <!-- Card 1 -->
      <div class="feature-card bg-white rounded-2xl p-6 shadow-sm border border-slate-100 transition-all duration-300">
        <div class="w-12 h-12 bg-indigo-100 rounded-xl flex items-center justify-center mb-4">
          <svg class="w-6 h-6 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"/></svg>
        </div>
        <h3 class="text-lg font-semibold mb-2">位置感知</h3>
        <p class="text-slate-500 text-sm leading-relaxed">基于 SLC（Significant Location Change）技术，低功耗感知位置变化，无需持续 GPS 定位。</p>
      </div>
      <!-- Card 2 -->
      <div class="feature-card bg-white rounded-2xl p-6 shadow-sm border border-slate-100 transition-all duration-300">
        <div class="w-12 h-12 bg-emerald-100 rounded-xl flex items-center justify-center mb-4">
          <svg class="w-6 h-6 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
        </div>
        <h3 class="text-lg font-semibold mb-2">运动感知</h3>
        <p class="text-slate-500 text-sm leading-relaxed">通过设备加速度传感器识别身体活动（行走、跑步、静止等），确认用户正在活动。</p>
      </div>
      <!-- Card 3 -->
      <div class="feature-card bg-white rounded-2xl p-6 shadow-sm border border-slate-100 transition-all duration-300">
        <div class="w-12 h-12 bg-amber-100 rounded-xl flex items-center justify-center mb-4">
          <svg class="w-6 h-6 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
        </div>
        <h3 class="text-lg font-semibold mb-2">空闲告警</h3>
        <p class="text-slate-500 text-sm leading-relaxed">在设定的时段内（如白天 9:00-18:00），超过阈值时间无活动（5-120 分钟可调），自动触发本地告警。</p>
      </div>
      <!-- Card 4 -->
      <div class="feature-card bg-white rounded-2xl p-6 shadow-sm border border-slate-100 transition-all duration-300">
        <div class="w-12 h-12 bg-rose-100 rounded-xl flex items-center justify-center mb-4">
          <svg class="w-6 h-6 text-rose-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/></svg>
        </div>
        <h3 class="text-lg font-semibold mb-2">关心码绑定</h3>
        <p class="text-slate-500 text-sm leading-relaxed">每台设备生成唯一 6 位关心码及二维码，家人扫码或输入即可建立关心关系，双向守护。</p>
      </div>
      <!-- Card 5 -->
      <div class="feature-card bg-white rounded-2xl p-6 shadow-sm border border-slate-100 transition-all duration-300">
        <div class="w-12 h-12 bg-sky-100 rounded-xl flex items-center justify-center mb-4">
          <svg class="w-6 h-6 text-sky-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0M1.394 9.393c5.857-5.858 15.355-5.858 21.213 0"/></svg>
        </div>
        <h3 class="text-lg font-semibold mb-2">云端心跳</h3>
        <p class="text-slate-500 text-sm leading-relaxed">约每小时向云端上报一次心跳，附带设备充电状态，让关心人随时了解你的在线情况。</p>
      </div>
      <!-- Card 6 -->
      <div class="feature-card bg-white rounded-2xl p-6 shadow-sm border border-slate-100 transition-all duration-300">
        <div class="w-12 h-12 bg-purple-100 rounded-xl flex items-center justify-center mb-4">
          <svg class="w-6 h-6 text-purple-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/></svg>
        </div>
        <h3 class="text-lg font-semibold mb-2">后台持久运行</h3>
        <p class="text-slate-500 text-sm leading-relaxed">前台服务 + 开机自启 + WorkManager 三重保障，确保监测 7×24 小时不中断。</p>
      </div>
    </div>
  </section>

  <!-- ──────────────── How it works ──────────────── -->
  <section class="bg-white py-20 md:py-28">
    <div class="max-w-4xl mx-auto px-6 text-center">
      <h2 class="text-3xl md:text-4xl font-bold mb-4">简单三步，守护开启</h2>
      <p class="text-slate-500 mb-14">无需注册账号，下载安装即可使用。</p>
      <div class="grid md:grid-cols-3 gap-8">
        <div class="relative">
          <div class="w-16 h-16 bg-indigo-100 rounded-2xl flex items-center justify-center mx-auto mb-4 text-2xl font-bold text-indigo-600">1</div>
          <h3 class="font-semibold mb-2">下载安装</h3>
          <p class="text-slate-500 text-sm">从 App Store 或本页下载 APK 安装，授予必要权限。</p>
        </div>
        <div class="relative">
          <div class="w-16 h-16 bg-indigo-100 rounded-2xl flex items-center justify-center mx-auto mb-4 text-2xl font-bold text-indigo-600">2</div>
          <h3 class="font-semibold mb-2">设置时段</h3>
          <p class="text-slate-500 text-sm">配置监测时段（如白天 9:00-18:00）和空闲告警阈值，按你的作息来。</p>
        </div>
        <div>
          <div class="w-16 h-16 bg-indigo-100 rounded-2xl flex items-center justify-center mx-auto mb-4 text-2xl font-bold text-indigo-600">3</div>
          <h3 class="font-semibold mb-2">分享关心码</h3>
          <p class="text-slate-500 text-sm">将 6 位关心码或二维码发给家人，他们扫码即可远程关注你的安全。</p>
        </div>
      </div>
    </div>
  </section>

  <!-- ──────────────── Download ──────────────── -->
  <section id="download" class="max-w-4xl mx-auto px-6 py-20 md:py-28 text-center">
    <h2 class="text-3xl md:text-4xl font-bold mb-4">下载 安好 Still Here</h2>
    <p class="text-slate-500 mb-12">选择你的设备平台，开始守护之旅。</p>
    <div class="grid md:grid-cols-2 gap-6 max-w-lg mx-auto">
      <!-- iOS -->
      <a href="#" class="group flex flex-col items-center gap-3 bg-white rounded-2xl p-8 shadow-sm border border-slate-100 hover:border-indigo-200 hover:shadow-md transition">
        <svg class="w-12 h-12 text-slate-800" viewBox="0 0 24 24" fill="currentColor"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/></svg>
        <span class="font-semibold text-lg">iOS App Store</span>
        <span class="text-slate-400 text-sm">即将上架，敬请期待</span>
      </a>
      <!-- Android -->
      <a href="/app-release.apk" class="group flex flex-col items-center gap-3 bg-white rounded-2xl p-8 shadow-sm border border-slate-100 hover:border-emerald-200 hover:shadow-md transition">
        <svg class="w-12 h-12 text-emerald-600" viewBox="0 0 24 24" fill="currentColor"><path d="M17.523 12.003c0-.304-.12-.598-.317-.804L7.39 3.413C7.011 3.163 6.5 3.053 6.046 3.29c-.458.24-.746.72-.746 1.24v14.94c0 .52.288 1 .746 1.24.454.237 1.004.127 1.344-.123l9.816-7.787c.198-.206.317-.5.317-.797zM3.5 5.5h-1c-.55 0-1 .45-1 1v11c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-11c0-.55-.45-1-1-1z"/></svg>
        <span class="font-semibold text-lg">Android APK</span>
        <span class="text-slate-400 text-sm">直接下载安装包</span>
      </a>
    </div>
  </section>

  <!-- ──────────────── Privacy Policy ──────────────── -->
  <section id="privacy" class="bg-white border-t border-slate-100">
    <div class="max-w-3xl mx-auto px-6 py-20 md:py-28 privacy-section">
      <h2 class="text-3xl md:text-4xl font-bold mb-4 text-center">隐私政策</h2>
      <p class="text-slate-500 text-center mb-12">最后更新日期：2025 年 7 月 25 日</p>

      <div class="prose prose-slate max-w-none space-y-6 text-sm leading-relaxed">

        <h3 class="text-xl font-semibold text-slate-900 pt-4">1. 引言</h3>
        <p>EastLake Studio（以下简称「我们」）深知隐私对用户的重要性。本隐私政策旨在向您说明我们如何通过「安好 Still Here」应用程序（以下简称「本应用」）收集、使用、存储和共享您的个人信息，以及您享有的相关权利。</p>
        <p>使用本应用即表示您同意本隐私政策中描述的数据处理方式。如您不同意本政策的任何条款，请停止使用本应用。</p>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">2. 我们收集的信息</h3>

        <h4 class="text-lg font-semibold text-slate-800">2.1 设备活动数据（本地处理，不上传）</h4>
        <p>为判断您的活动状态，本应用会在设备本地采集以下传感器和系统数据：</p>
        <ul class="list-disc pl-6 space-y-1">
          <li><strong>位置信息</strong>：通过系统 SLC（Significant Location Change）接口感知粗略位置变化，仅在您已授权「始终允许」位置权限时生效。精确 GPS 坐标不会被记录或上传。</li>
          <li><strong>身体活动数据</strong>：通过 Google Activity Recognition API 或 Apple Core Motion 框架识别运动状态（静止、行走、跑步、乘车等），仅在您授权「身体活动」或「运动与健身」权限后启用。</li>
          <li><strong>应用前台状态</strong>：检测本应用是否处于前台使用中。</li>
          <li><strong>设备充电状态</strong>：读取设备是否正在充电。</li>
          <li><strong>系统后台刷新事件</strong>：记录系统后台任务执行情况。</li>
        </ul>
        <p class="text-slate-500 italic">以上数据全部在您的设备本地处理和判断，<strong>不会上传至云端服务器</strong>。它们仅用于本应用内部判定「用户是否处于活动状态」，以决定是否需要触发本地空闲告警。</p>

        <h4 class="text-lg font-semibold text-slate-800">2.2 心跳数据（上传至云端）</h4>
        <p>当您开启持续监测后，本应用约每小时向云端服务器上报一次「心跳」，内容包含：</p>
        <ul class="list-disc pl-6 space-y-1">
          <li><strong>设备标识符</strong>：唯一的 6 位关心码（由本应用随机生成，非设备硬件 ID）。</li>
          <li><strong>充电状态</strong>：设备当前是否在充电（是/否）。</li>
          <li><strong>时间戳</strong>：心跳上报的服务器时间。</li>
        </ul>
        <p>心跳数据的目的：让已绑定关心关系的家人可以查看您「最近活跃时间」和「设备充电状态」，从而推断您是否处于正常活动状态。</p>
        <p class="font-semibold">心跳数据<strong>不包含</strong>：精确位置、运动详情、个人身份信息、通讯录、照片、浏览记录或任何其他敏感数据。</p>

        <h4 class="text-lg font-semibold text-slate-800">2.3 关心关系数据</h4>
        <p>当您或您的家人通过关心码建立关心关系时，云端会记录绑定关系（仅「关心者关心码 ↔ 被关心者关心码」的映射），不含任何个人身份信息。</p>

        <h4 class="text-lg font-semibold text-slate-800">2.4 我们不收集的信息</h4>
        <p>本应用<strong>不会</strong>收集以下信息：</p>
        <ul class="list-disc pl-6 space-y-1">
          <li>姓名、手机号、邮箱地址等个人身份信息</li>
          <li>通讯录、通话记录、短信内容</li>
          <li>照片、视频、麦克风录音</li>
          <li>浏览器历史记录</li>
          <li>设备唯一标识符（IMEI、广告 ID 等）</li>
          <li>精确 GPS 位置坐标</li>
        </ul>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">3. 信息的使用方式</h3>
        <p>我们收集的信息仅用于以下目的：</p>
        <ul class="list-disc pl-6 space-y-1">
          <li><strong>活动监测</strong>：在设备本地判断您是否处于活动状态，以决定是否触发空闲告警。</li>
          <li><strong>安全守护</strong>：通过心跳数据和关心码绑定，让您的关心人可以查看您的在线状态。</li>
          <li><strong>服务改进</strong>：分析服务稳定性，排查技术问题。</li>
        </ul>
        <p>我们<strong>不会</strong>将您的数据用于广告投放、用户画像、自动化决策或任何商业目的。</p>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">4. 信息的存储</h3>

        <h4 class="text-lg font-semibold text-slate-800">4.1 本地存储</h4>
        <p>活动传感器数据（位置变化、运动状态、充电状态等）仅在设备内存中短暂保留用于实时判断，不会写入本地持久化存储。监测配置（告警阈值、时段设置、关心码）存储在设备的 SharedPreferences / UserDefaults 中。</p>

        <h4 class="text-lg font-semibold text-slate-800">4.2 云端存储</h4>
        <p>心跳数据和关心关系数据存储在 Cloudflare D1 数据库中，服务器位于 Cloudflare 全球边缘网络。心跳数据保留 90 天，超过保留期限后自动清理。</p>

        <h4 class="text-lg font-semibold text-slate-800">4.3 数据传输安全</h4>
        <p>所有与应用后端之间的通信均通过 HTTPS/TLS 加密传输。云端数据库采用 Cloudflare 提供的安全防护措施。</p>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">5. 信息的共享</h3>
        <p>我们<strong>不会</strong>将您的任何数据出售、出租或与第三方共享，除非满足以下情形之一：</p>
        <ul class="list-disc pl-6 space-y-1">
          <li><strong>您明确同意</strong>：当您将关心码分享给家人时，即表示您同意与该关心人共享您的在线状态（最近活跃时间、充电状态）。</li>
          <li><strong>法律要求</strong>：根据适用法律法规、法律程序或政府要求必须披露。</li>
          <li><strong>服务提供商</strong>：我们使用 Cloudflare 作为云基础设施提供商，数据存储在其服务器上。Cloudflare 的隐私政策可参见 <a href="https://www.cloudflare.com/privacypolicy/" class="text-indigo-600 underline" target="_blank">cloudflare.com/privacypolicy</a>。</li>
        </ul>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">6. 您的权利</h3>
        <p>根据适用的数据保护法律，您享有以下权利：</p>
        <ul class="list-disc pl-6 space-y-1">
          <li><strong>访问权</strong>：您可以随时在本应用内查看您的关心码和关心关系。</li>
          <li><strong>删除权</strong>：您可以通过停止监测并卸载本应用来停止所有数据采集。云端数据可通过联系我们来请求删除。</li>
          <li><strong>撤回同意</strong>：您可以在系统设置中随时撤销已授予的权限（位置、身体活动等）。</li>
          <li><strong>数据可携带权</strong>：您可以联系我们来获取您存储在云端的数据副本。</li>
        </ul>
        <p>如需行使上述权利，请通过下方联系方式与我们联系，我们将在 30 日内回复。</p>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">7. 儿童隐私</h3>
        <p>本应用不面向 13 岁以下儿童。我们不会故意收集 13 岁以下儿童的个人信息。如您发现我们无意中收集了儿童信息，请立即联系我们，我们将及时删除。</p>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">8. 第三方服务</h3>
        <p>本应用使用了以下第三方服务，它们可能收集去标识化的使用数据：</p>
        <ul class="list-disc pl-6 space-y-1">
          <li><strong>Google Activity Recognition API</strong>（仅 Android）：用于识别用户运动状态。数据在设备本地处理，不上传至 Google 服务器。Google 隐私政策：<a href="https://policies.google.com/privacy" class="text-indigo-600 underline" target="_blank">policies.google.com/privacy</a></li>
          <li><strong>Apple Core Motion</strong>（仅 iOS）：用于识别用户运动状态。数据在设备本地处理。Apple 隐私政策：<a href="https://www.apple.com/legal/privacy/" class="text-indigo-600 underline" target="_blank">apple.com/legal/privacy</a></li>
          <li><strong>Cloudflare</strong>：作为云基础设施提供商，存储心跳和关心关系数据。</li>
        </ul>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">9. 数据跨境传输</h3>
        <p>我们的云服务由 Cloudflare 提供，服务器分布于全球多个地区。您的数据可能被传输并存储到您所在国家/地区之外的服务器的信息。我们已采取适当的安全措施，确保您的数据在传输和存储过程中受到保护。</p>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">10. 隐私政策更新</h3>
        <p>我们可能会不时更新本隐私政策。更新后的政策将在本页面发布，并在页面顶部注明最后更新日期。重大变更可能会通过应用内通知或本页面公告的方式告知。建议您定期查阅本政策。</p>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">11. 联系我们</h3>
        <p>如您对本隐私政策或数据处理有任何疑问、关切或投诉，请通过以下方式联系我们：</p>
        <ul class="list-disc pl-6 space-y-1">
          <li><strong>邮箱</strong>：<a href="mailto:mingh.liu@gmail.com" class="text-indigo-600 underline">mingh.liu@gmail.com</a></li>
          <li><strong>开发者</strong>：EastLake Studio</li>
        </ul>

        <div class="mt-12 pt-8 border-t border-slate-200 text-center text-slate-400 text-xs">
          © 2025 EastLake Studio. 保留所有权利。
        </div>
      </div>
    </div>
  </section>

  <!-- ──────────────── Footer ──────────────── -->
  <footer class="border-t border-slate-200 bg-slate-50 py-8 text-center text-slate-400 text-sm">
    <div class="max-w-4xl mx-auto px-6 flex flex-wrap justify-center gap-6">
      <a href="#features" class="hover:text-slate-600 transition">功能</a>
      <a href="#download" class="hover:text-slate-600 transition">下载</a>
      <a href="#privacy" class="hover:text-slate-600 transition">隐私政策</a>
      <a href="mailto:mingh.liu@gmail.com" class="hover:text-slate-600 transition">联系我们</a>
    </div>
  </footer>

</body>
</html>`;
}
