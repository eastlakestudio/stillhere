import { Env, jsonResponse, now } from '../shared';
import { getSessionEmail, redirectToGoogle } from '../lib/auth';

interface UserRow {
  id: string;
  last_active_time: number;
  is_alerted: number;
  is_charging: number;
  created_at: number;
  online_status: string | null;
}

interface RelationRow {
  id: number;
  from_device_id: string;
  to_code: string;
  created_at: number;
}

interface AlertRow {
  id: number;
  user_id: string;
  care_code: string;
  alert_type: string;
  idle_minutes: number;
  is_charging: number;
  is_resolved: number;
  created_at: number;
  resolved_at: number | null;
}

interface DashboardData {
  total: number;
  active1h: number;
  active24h: number;
  alerted: number;
  users: Array<{
    careCode: string;
    lastActive: string;
    isAlerted: boolean;
    isCharging: boolean;
    onlineStatus: string;
    createdAt: string;
  }>;
  relations: Array<{
    /** "A → B" 或 "A ↔ B"（箭头已含方向） */
    arrow: string;
    createdAt: string;
  }>;
  alerts: Array<{
    careCode: string;
    alertType: string;
    idleMinutes: number;
    isCharging: boolean;
    isResolved: boolean;
    createdAt: string;
    resolvedAt: string;
  }>;
}

/** 从 deviceId 派生 6 位关心码（SHA-256 → hex → 取第8-13位 → 大写） */
async function toCareCode(deviceId: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(deviceId));
  const hex = Array.from(new Uint8Array(hash), b => b.toString(16).padStart(2, '0')).join('');
  return hex.slice(8, 14).toUpperCase();
}

/** 需要鉴权的 dashboard 页面 */
export async function handleDashboard(request: Request, env: Env): Promise<Response> {
  const email = await getSessionEmail(request, env);
  if (!email) {
    return redirectToGoogle(request, env);
  }

  // 查询数据
  const ts = now();
  const oneHourAgo = ts - 3600;
  const oneDayAgo = ts - 86400;

  const totalRes = await env.DB.prepare(`SELECT COUNT(*) as c FROM users`).first<{ c: number }>();
  const active1hRes = await env.DB.prepare(`SELECT COUNT(*) as c FROM users WHERE last_active_time > ?1`).bind(oneHourAgo).first<{ c: number }>();
  const active24hRes = await env.DB.prepare(`SELECT COUNT(*) as c FROM users WHERE last_active_time > ?1`).bind(oneDayAgo).first<{ c: number }>();
  const alertedRes = await env.DB.prepare(`SELECT COUNT(*) as c FROM users WHERE is_alerted = 1`).first<{ c: number }>();
  const recentUsers = await env.DB.prepare(`
    SELECT id, last_active_time, is_alerted, is_charging, created_at, online_status
    FROM users ORDER BY last_active_time DESC LIMIT 50
  `).all<UserRow>();

  // 查询关心关系（全部）
  const relationsRes = await env.DB.prepare(`
    SELECT id, from_device_id, to_code, created_at
    FROM care_relations ORDER BY created_at DESC
  `).all<RelationRow>();

  // 查询告警记录（最近 100 条）
  const alertsRes = await env.DB.prepare(`
    SELECT id, user_id, care_code, alert_type, idle_minutes, is_charging, is_resolved, created_at, resolved_at
    FROM alerts ORDER BY created_at DESC LIMIT 100
  `).all<AlertRow>();

  // 为每个用户计算关心码（并行）
  const careCodeMap = new Map<string, string>();
  await Promise.all(recentUsers.results.map(async (u) => {
    careCodeMap.set(u.id, await toCareCode(u.id));
  }));

  // 为每个关系的关心人也计算关心码
  const relationCareCodeMap = new Map<string, string>();
  const relationDeviceIds = [...new Set((relationsRes.results || []).map(r => r.from_device_id))];
  await Promise.all(relationDeviceIds.map(async (did) => {
    relationCareCodeMap.set(did, await toCareCode(did));
  }));

  // 构建 (fromCareCode → toCode) 对集合，检测双向关系
  const pairSet = new Set<string>();
  const relationList: Array<{ fromCode: string; toCode: string; createdAt: number }> = [];
  for (const r of (relationsRes.results || [])) {
    const fromCode = relationCareCodeMap.get(r.from_device_id) || '------';
    pairSet.add(`${fromCode}→${r.to_code}`);
    relationList.push({ fromCode, toCode: r.to_code, createdAt: r.created_at });
  }

  // 合并为箭头行：双向 ↔ 优先，去重
  const shownPairs = new Set<string>();
  const relationRows: Array<{ arrow: string; createdAt: string }> = [];
  for (const r of relationList) {
    const sortedKey = [r.fromCode, r.toCode].sort().join('|');
    if (shownPairs.has(sortedKey)) continue;
    const isMutual = pairSet.has(`${r.toCode}→${r.fromCode}`);
    if (isMutual) {
      shownPairs.add(sortedKey);
      relationRows.push({ arrow: `${r.fromCode} ↔ ${r.toCode}`, createdAt: fmtTime(r.createdAt) });
    } else {
      shownPairs.add(`${r.fromCode}→${r.toCode}`);
      relationRows.push({ arrow: `${r.fromCode} → ${r.toCode}`, createdAt: fmtTime(r.createdAt) });
    }
  }

  const data: DashboardData = {
    total: totalRes?.c ?? 0,
    active1h: active1hRes?.c ?? 0,
    active24h: active24hRes?.c ?? 0,
    alerted: alertedRes?.c ?? 0,
    users: recentUsers.results.map(u => ({
      careCode: careCodeMap.get(u.id) || '------',
      lastActive: fmtTime(u.last_active_time),
      isAlerted: u.is_alerted === 1,
      isCharging: u.is_charging === 1,
      onlineStatus: u.online_status || 'online',
      createdAt: fmtTime(u.created_at),
    })),
    relations: relationRows,
    alerts: (alertsRes.results || []).map(a => ({
      careCode: a.care_code,
      alertType: a.alert_type,
      idleMinutes: a.idle_minutes,
      isCharging: a.is_charging === 1,
      isResolved: a.is_resolved === 1,
      createdAt: fmtTime(a.created_at),
      resolvedAt: a.resolved_at ? fmtTime(a.resolved_at) : '-',
    })),
  };

  return new Response(renderHtml(data, email), {
    headers: { 'Content-Type': 'text/html; charset=utf-8' },
  });
}

function fmtTime(ts: number): string {
  if (ts <= 0) return '从未';
  const d = new Date(ts * 1000);
  const now = new Date();
  const diffMin = Math.floor((now.getTime() - d.getTime()) / 60000);
  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin} 分钟前`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH} 小时前`;
  return d.toLocaleString('zh-CN', { hour12: false });
}

function renderHtml(data: DashboardData, email: string): string {
  const userRows = data.users.map(u => `
    <tr>
      <td class="code">${u.careCode}</td>
      <td>${u.lastActive}</td>
      <td>${u.isAlerted ? '⚠️ 已告警' : '正常'}</td>
      <td>${u.onlineStatus === 'offline' ? '⚫ 离线' : '🟢 在线'}</td>
      <td>${u.isCharging ? '🔌 充电' : '🔋'}</td>
      <td class="time">${u.createdAt}</td>
    </tr>
  `).join('');

  const relationRows = data.relations.map(r => `
    <tr>
      <td class="arrow">${r.arrow}</td>
      <td class="time">${r.createdAt}</td>
    </tr>
  `).join('');

  const alertRows = data.alerts.map(a => {
    const typeLabel = a.alertType === 'idle' ? '💤 空闲' : a.alertType === 'offline' ? '⚫ 离线' : a.alertType === 'online' ? '🟢 上线' : '🔄 恢复';
    const statusLabel = a.isResolved ? '✅ 已恢复' : '🔴 进行中';
    return `
    <tr>
      <td class="code">${a.careCode}</td>
      <td>${typeLabel}</td>
      <td>${a.idleMinutes > 0 ? a.idleMinutes + ' 分钟' : '-'}</td>
      <td>${a.isCharging ? '🔌' : '🔋'}</td>
      <td>${statusLabel}</td>
      <td class="time">${a.createdAt}</td>
      <td class="time">${a.resolvedAt}</td>
    </tr>
  `}).join('');

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>StillHere · 管理面板</title>
<style>
  *{margin:0;padding:0;box-sizing:border-box}
  body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f5f5f5;color:#333}
  .header{background:linear-gradient(135deg,#6366f1,#8b5cf6);color:#fff;padding:24px 20px}
  .header h1{font-size:1.5rem;font-weight:600}
  .header p{font-size:.85rem;opacity:.8;margin-top:4px}
  .stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;padding:20px;max-width:1200px;margin:0 auto}
  .stat-card{background:#fff;border-radius:12px;padding:20px;box-shadow:0 1px 3px rgba(0,0,0,.08)}
  .stat-card .label{font-size:.8rem;color:#888;margin-bottom:4px}
  .stat-card .value{font-size:2rem;font-weight:700}
  .stat-card .value.red{color:#ef4444}
  .stat-card .value.green{color:#22c55e}
  .stat-card .value.blue{color:#3b82f6}
  .section{max-width:1200px;margin:0 auto;padding:0 20px 40px}
  .section h2{font-size:1.1rem;margin-bottom:12px;color:#555}
  table{width:100%;border-collapse:collapse;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.08);margin-bottom:24px}
  th,td{text-align:left;padding:10px 14px;font-size:.85rem}
  th{background:#f8f8f8;font-weight:600;color:#666;font-size:.78rem;text-transform:uppercase;letter-spacing:.5px;white-space:nowrap}
  td{border-top:1px solid #f0f0f0}
  td.id{font-family:monospace;font-size:.78rem}
  td.code{font-family:monospace;font-weight:600;color:#6366f1;font-size:.85rem}
  td.arrow{font-family:monospace;font-weight:600;color:#6366f1;font-size:.9rem;letter-spacing:2px}
  td.time{color:#999;font-size:.78rem;white-space:nowrap}
  .footer{text-align:center;padding:20px;color:#aaa;font-size:.8rem}
  .footer a{color:#6366f1;text-decoration:none}
  .badge{display:inline-block;padding:2px 8px;border-radius:10px;font-size:.75rem;font-weight:500}
  .badge-online{background:#dcfce7;color:#16a34a}
  .badge-offline{background:#f3f4f6;color:#6b7280}
</style>
</head>
<body>
<div class="header">
  <h1>StillHere · 管理面板</h1>
  <p>已登录：${email} · <a href="/" style="color:#fff">主页</a> · <a href="/auth/logout" style="color:#fff">退出</a></p>
</div>

<div class="stats">
  <div class="stat-card">
    <div class="label">总用户数</div>
    <div class="value blue">${data.total}</div>
  </div>
  <div class="stat-card">
    <div class="label">1 小时内活跃</div>
    <div class="value green">${data.active1h}</div>
  </div>
  <div class="stat-card">
    <div class="label">24 小时内活跃</div>
    <div class="value green">${data.active24h}</div>
  </div>
  <div class="stat-card">
    <div class="label">告警中</div>
    <div class="value red">${data.alerted}</div>
  </div>
</div>

<div class="section">
  <h2>📋 最近活跃用户（${data.users.length}）</h2>
  <table>
    <thead>
      <tr><th>关心码</th><th>最后活跃</th><th>告警</th><th>在线</th><th>电源</th><th>加入时间</th></tr>
    </thead>
    <tbody>
      ${userRows || '<tr><td colspan="6" style="text-align:center;color:#999;padding:40px">暂无数据</td></tr>'}
    </tbody>
  </table>
</div>

<div class="section">
  <h2>💚 关心关系（${data.relations.length}）</h2>
  <table>
    <thead>
      <tr><th>关心方向</th><th>建立时间</th></tr>
    </thead>
    <tbody>
      ${relationRows || '<tr><td colspan="2" style="text-align:center;color:#999;padding:40px">暂无关心关系</td></tr>'}
    </tbody>
  </table>
</div>

<div class="section">
  <h2>🚨 告警记录（${data.alerts.length}）</h2>
  <table>
    <thead>
      <tr><th>关心码</th><th>类型</th><th>空闲</th><th>充电</th><th>状态</th><th>触发时间</th><th>恢复时间</th></tr>
    </thead>
    <tbody>
      ${alertRows || '<tr><td colspan="7" style="text-align:center;color:#999;padding:40px">暂无告警记录</td></tr>'}
    </tbody>
  </table>
</div>

<div class="footer">StillHere Admin Panel · Cloudflare Workers</div>
</body>
</html>`;
}
