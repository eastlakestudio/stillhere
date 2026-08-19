export default {
  async fetch(request: Request, env: any): Promise<Response> {
    const url = new URL(request.url);
    
    // Route static assets to asset binding
    if (url.pathname.startsWith('/qhao-release.apk')) {
      return env.ASSETS.fetch(request);
    }

    return new Response(landingPage(), {
      headers: { 'Content-Type': 'text/html; charset=utf-8' },
    });
  },
};

function landingPage(): string {
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>晴好 — 独居安全守护</title>
  <meta name="description" content="晴好是一款独居安全监测 App。多维度感知活动状态，定时上报心跳，长时间无活动自动告警家人。">
  <script src="https://cdn.tailwindcss.com"></script>
  <style>
    html { scroll-behavior: smooth; }
    .gradient-text {
      background: linear-gradient(135deg, #C25A00, #FF8C00);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }
    .hero-gradient {
      background: linear-gradient(135deg, #2D1600 0%, #4A2500 50%, #5C2E00 100%);
    }
    .feature-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 20px 40px rgba(194,90,0,0.15);
    }
    .privacy-section h3 { scroll-margin-top: 2rem; }
    .privacy-section h4 { scroll-margin-top: 2rem; }
  </style>
</head>
<body class="bg-slate-50 text-slate-800 antialiased">

  <section class="hero-gradient text-white">
    <div class="max-w-4xl mx-auto px-6 py-20 md:py-32 text-center">
      <div class="inline-flex items-center gap-2 bg-white/10 rounded-full px-4 py-1.5 text-sm mb-6 backdrop-blur">
        <span class="w-2 h-2 bg-orange-400 rounded-full animate-pulse"></span>
        持续守护 · 安心每一天
      </div>
      <h1 class="text-4xl md:text-6xl font-extrabold tracking-tight mb-6">
        <span class="gradient-text">晴好</span>
      </h1>
      <p class="text-lg md:text-xl text-orange-200 max-w-2xl mx-auto mb-10 leading-relaxed">
        一款为独居者设计的安全监测 App。多维度感知活动状态，定时向云端上报心跳，
        长时间无活动时自动告警关心你的家人。让关心，永不缺席。
      </p>
      <div class="flex flex-wrap justify-center gap-4">
        <a href="#download" class="inline-flex items-center gap-2 bg-white text-orange-700 font-semibold px-8 py-3.5 rounded-xl hover:bg-orange-50 transition shadow-lg shadow-orange-500/25">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"/></svg>
          下载 App
        </a>
        <a href="#features" class="inline-flex items-center gap-2 border border-white/30 text-white font-semibold px-8 py-3.5 rounded-xl hover:bg-white/10 transition">
          了解功能
        </a>
      </div>
    </div>
  </section>

  <section id="features" class="max-w-6xl mx-auto px-6 py-20 md:py-28">
    <div class="text-center mb-16">
      <h2 class="text-3xl md:text-4xl font-bold mb-4">多维度守护，滴水不漏</h2>
      <p class="text-slate-500 max-w-xl mx-auto">六种监测器协同工作，全方位感知你的活动状态。</p>
    </div>
    <div class="grid md:grid-cols-3 gap-6">
      ${featureCard('orange', '位置感知', '基于 SLC 低功耗感知位置变化，无需持续 GPS 定位。')}
      ${featureCard('emerald', '运动感知', '通过加速度传感器识别活动状态（行走、跑步、静止）。')}
      ${featureCard('amber', '空闲告警', '可配置时段和阈值（5-120分钟），超时自动触发告警。')}
      ${featureCard('rose', '关心码绑定', '每台设备生成唯一 6 位码，家人扫码即可建立守护关系。')}
      ${featureCard('sky', '云端心跳', '定期上报心跳，关心人可随时查看你的在线状态。')}
      ${featureCard('purple', '后台持久', '前台服务 + 开机自启 + 后台轮询，7×24 不中断。')}
    </div>
  </section>

  <section class="bg-white py-20 md:py-28">
    <div class="max-w-4xl mx-auto px-6 text-center">
      <h2 class="text-3xl md:text-4xl font-bold mb-4">简单三步，守护开启</h2>
      <p class="text-slate-500 mb-14">无需注册账号，下载安装即可使用。</p>
      <div class="grid md:grid-cols-3 gap-8">
        <div><div class="w-16 h-16 bg-orange-100 rounded-2xl flex items-center justify-center mx-auto mb-4 text-2xl font-bold text-orange-600">1</div><h3 class="font-semibold mb-2">下载安装</h3><p class="text-slate-500 text-sm">从 App Store 或本页下载安装。</p></div>
        <div><div class="w-16 h-16 bg-orange-100 rounded-2xl flex items-center justify-center mx-auto mb-4 text-2xl font-bold text-orange-600">2</div><h3 class="font-semibold mb-2">设置时段</h3><p class="text-slate-500 text-sm">配置监测时段和告警阈值。</p></div>
        <div><div class="w-16 h-16 bg-orange-100 rounded-2xl flex items-center justify-center mx-auto mb-4 text-2xl font-bold text-orange-600">3</div><h3 class="font-semibold mb-2">分享关心码</h3><p class="text-slate-500 text-sm">将 6 位关心码发给家人即可。</p></div>
      </div>
    </div>
  </section>

  <section id="download" class="max-w-4xl mx-auto px-6 py-20 md:py-28 text-center">
    <h2 class="text-3xl md:text-4xl font-bold mb-4">下载 晴好</h2>
    <p class="text-slate-500 mb-12">选择你的设备平台，开始守护之旅。</p>
    <div class="grid md:grid-cols-2 gap-6 max-w-lg mx-auto">
      <a href="https://apps.apple.com/us/app/%E6%99%B4%E5%A5%BD-%E4%BA%92%E7%9B%B8%E5%AE%88%E6%8A%A4/id6794597733" target="_blank" rel="noopener" class="flex flex-col items-center gap-3 bg-white rounded-2xl p-8 shadow-sm border border-slate-100 hover:border-orange-200 hover:shadow-md transition">
        <span class="text-4xl"></span>
        <span class="font-semibold text-lg">iOS App Store</span>
        <span class="text-slate-400 text-sm">前往 App Store 下载</span>
      </a>
      <a href="/qhao-release.apk" class="flex flex-col items-center gap-3 bg-white rounded-2xl p-8 shadow-sm border border-slate-100 hover:border-emerald-200 hover:shadow-md transition">
        <span class="text-4xl">🤖</span>
        <span class="font-semibold text-lg">Android APK</span>
        <span class="text-slate-400 text-sm">直接下载安装包</span>
      </a>
    </div>
  </section>

  <section id="privacy" class="bg-white border-t border-slate-100">
    <div class="max-w-3xl mx-auto px-6 py-20 md:py-28 privacy-section">
      <h2 class="text-3xl md:text-4xl font-bold mb-4 text-center">隐私政策</h2>
      <p class="text-slate-500 text-center mb-12">最后更新日期：2026 年 7 月 30 日</p>
      <div class="prose prose-slate max-w-none space-y-6 text-sm leading-relaxed">

        <h3 class="text-xl font-semibold text-slate-900 pt-4">1. 引言</h3>
        <p>EastLake Studio（以下简称「我们」）深知隐私对用户的重要性。本隐私政策旨在向您说明我们如何通过「晴好」应用程序（以下简称「本应用」）收集、使用、存储和共享您的个人信息。</p>
        <p>使用本应用即表示您同意本隐私政策中描述的数据处理方式。</p>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">2. 我们收集的信息</h3>
        <h4 class="text-lg font-semibold text-slate-800">2.1 设备活动数据（本地处理，不上传）</h4>
        <p>为判断您的活动状态，本应用在设备本地采集传感器和系统数据。精确 GPS 坐标不会被记录或上传。</p>
        <h4 class="text-lg font-semibold text-slate-800">2.2 心跳数据（上传至云端）</h4>
        <p>本应用约每小时上报一次心跳，包含：唯一 6 位关心码（非设备硬件 ID）、充电状态（是/否）、时间戳。</p>
        <p class="font-semibold">心跳数据不包含：精确位置、运动详情、个人身份信息、通讯录、照片等任何敏感数据。</p>
        <h4 class="text-lg font-semibold text-slate-800">2.3 我们不收集的信息</h4>
        <p>本应用不会收集姓名、手机号、邮箱地址、通讯录、照片、IMEI、精确 GPS 等。</p>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">3. 信息的使用方式</h3>
        <p>仅用于活动监测和安全守护。不会用于广告投放或任何商业目的。</p>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">4. 信息的存储与安全</h3>
        <p>云端数据存储在 Cloudflare D1 数据库。所有通信通过 HTTPS/TLS 加密传输。</p>

        <h3 class="text-xl font-semibold text-slate-900 pt-4">5. 联系我们</h3>
        <p>邮箱：<a href="mailto:mingh.liu@gmail.com" class="text-orange-600 underline">mingh.liu@gmail.com</a></p>
        <p>开发者：EastLake Studio</p>

        <div class="mt-12 pt-8 border-t border-slate-200 text-center text-slate-400 text-xs">
          © 2026 EastLake Studio. 晴好 StillHere. 保留所有权利。
        </div>
      </div>
    </div>
  </section>

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

function featureCard(color: string, title: string, desc: string): string {
  const colors: Record<string, string> = {
    orange: 'bg-orange-100 text-orange-600',
    emerald: 'bg-emerald-100 text-emerald-600',
    amber: 'bg-amber-100 text-amber-600',
    rose: 'bg-rose-100 text-rose-600',
    sky: 'bg-sky-100 text-sky-600',
    purple: 'bg-purple-100 text-purple-600',
  };
  return '<div class="feature-card bg-white rounded-2xl p-6 shadow-sm border border-slate-100 transition-all duration-300">' +
    '<div class="w-12 h-12 ' + colors[color] + ' rounded-xl flex items-center justify-center mb-4"></div>' +
    '<h3 class="text-lg font-semibold mb-2">' + title + '</h3>' +
    '<p class="text-slate-500 text-sm leading-relaxed">' + desc + '</p></div>';
}
