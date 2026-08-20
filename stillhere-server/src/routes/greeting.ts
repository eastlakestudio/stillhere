import { Env, jsonResponse, now } from '../shared';
import { sendApnsAlert } from '../lib/apns';

/** 从 deviceId 派生 6 位关心码 */
async function toCareCode(deviceId: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(deviceId));
  const hex = Array.from(new Uint8Array(hash), b => b.toString(16).padStart(2, '0')).join('');
  return hex.slice(8, 14).toUpperCase();
}

/** 通过关心码查找设备（返回 deviceId 和 device_token） */
async function findDeviceByCode(env: Env, careCode: string): Promise<{ id: string; device_token: string | null } | null> {
  // 优先用 users.care_code 冗余列精确匹配（无需 device_token，模拟器等无推送设备也能命中）
  const byCode = await env.DB.prepare(
    'SELECT id, device_token FROM users WHERE care_code = ?1 AND care_code != \'\' LIMIT 1'
  ).bind(careCode).first<{ id: string; device_token: string | null }>();
  if (byCode) return byCode;

  // 兜底：遍历并按 SHA-256 派生匹配（兼容 care_code 列未上报的老数据）
  const { results } = await env.DB.prepare(
    'SELECT id, device_token FROM users'
  ).all<{ id: string; device_token: string | null }>();
  if (!results) return null;
  for (const row of results) {
    if ((await toCareCode(row.id)) === careCode) {
      return row;
    }
  }
  return null;
}

/**
 * POST /greeting — 发送问安，推送给接收方
 * Body: { fromUserId: string, toCode: string, message?: string }
 */
async function handleSendGreeting(request: Request, env: Env): Promise<Response> {
  let body: { fromUserId?: string; toCode?: string; message?: string };
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'invalid JSON' }, 400);
  }

  const { fromUserId, toCode } = body;
  if (!fromUserId || !toCode) {
    return jsonResponse({ error: 'fromUserId and toCode are required' }, 400);
  }

  const code = toCode.trim().toUpperCase().slice(0, 6);
  if (code.length !== 6) {
    return jsonResponse({ error: 'toCode must be 6 characters' }, 400);
  }

  const message = (body.message || '问安').trim().slice(0, 200);

  // 插入问安记录
  const result = await env.DB.prepare(
    'INSERT INTO greetings (from_device_id, to_code, message) VALUES (?1, ?2, ?3)'
  ).bind(fromUserId, code, message).run();

  // 异步推送（不阻塞响应）
  const greetingId = result.meta.last_row_id;
  sendGreetingPush(env, fromUserId, code, message, greetingId).catch(e =>
    console.error(`[greeting] push failed: ${e.message}`)
  );

  return jsonResponse({ ok: true, id: greetingId });
}

/** 查询发送者的关心码用于推送文案 */
async function findCareCodeByDeviceId(env: Env, deviceId: string): Promise<string> {
  // 先检查 users 表是否存在
  const user = await env.DB.prepare('SELECT id FROM users WHERE id = ?1')
    .bind(deviceId).first<{ id: string }>();
  if (user) return toCareCode(deviceId);
  return '------';
}

/**
 * 解析推送文案中的名字：
 * 优先用 viewer 给 targetCode 起的昵称（若已互相关心，昵称存在 viewer 的 device_config.nicknames 里），
 * 否则回退为关心码。
 */
async function resolveDisplayName(env: Env, viewerDeviceId: string, targetCode: string): Promise<string> {
  try {
    const row = await env.DB.prepare(
      'SELECT config_json FROM device_config WHERE device_id = ?1'
    ).bind(viewerDeviceId).first<{ config_json: string }>();
    if (row?.config_json) {
      const cfg = JSON.parse(row.config_json);
      const nicknames = cfg?.nicknames;
      if (nicknames && typeof nicknames[targetCode] === 'string' && nicknames[targetCode] !== '') {
        return nicknames[targetCode];
      }
    }
  } catch (e: any) {
    console.error(`[greeting] resolveDisplayName failed: ${e.message}`);
  }
  return targetCode;
}

async function sendGreetingPush(env: Env, fromUserId: string, toCode: string, message: string, greetingId: number) {
  const target = await findDeviceByCode(env, toCode);
  if (!target?.device_token) {
    console.log(`[greeting] no device_token for code ${toCode}`);
    return;
  }

  const fromCode = await findCareCodeByDeviceId(env, fromUserId);
  // 若接收方已互相关心（给发送者起了昵称），用昵称替代关心码
  const displayName = fromUserId ? await resolveDisplayName(env, target.id, fromCode) : fromCode;
  const nowTs = now();
  try {
    await sendApnsAlert({
      env,
      deviceToken: target.device_token,
      title: '晴好 · 问安',
      body: `${displayName} 向你问安${message !== '问安' ? '：' + message : ''}`,
      badge: 1,
      data: {
        greetingId,
        fromCareCode: fromCode,
        displayName,
        message,
        createdAt: nowTs,
        isReply: false,
      },
    });
  } catch (e: any) {
    console.error(`[greeting] APNs push failed: ${e.message}`);
  }
}

/**
 * POST /greeting/reply — 回复问安，推送给发起方
 * Body: { greetingId: number, reply: string, fromUserId?: string }
 * - 更新原问候的 reply 字段
 * - 创建反向问候记录，让发送方在 pending-greetings 中看到回复
 */
async function handleReplyGreeting(request: Request, env: Env): Promise<Response> {
  let body: { greetingId?: number; reply?: string; fromUserId?: string };
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'invalid JSON' }, 400);
  }

  const { greetingId, reply, fromUserId } = body;
  if (!greetingId || !reply) {
    return jsonResponse({ error: 'greetingId and reply are required' }, 400);
  }

  const replyText = reply.trim().slice(0, 500);
  if (!replyText) {
    return jsonResponse({ error: 'reply cannot be empty' }, 400);
  }

  // 查找问安记录
  const greeting = await env.DB.prepare(
    'SELECT id, from_device_id, to_code, reply_to_id FROM greetings WHERE id = ?1 AND reply IS NULL'
  ).bind(greetingId).first<{ id: number; from_device_id: string; to_code: string; reply_to_id: number | null }>();

  if (!greeting) {
    return jsonResponse({ error: 'greeting not found or already replied' }, 404);
  }

  // 更新回复
  const ts = now();
  await env.DB.prepare(
    'UPDATE greetings SET reply = ?1, replied_at = ?2 WHERE id = ?3'
  ).bind(replyText, ts, greetingId).run();

  // 异步推送回复通知给发起方
  sendReplyPush(env, greeting.from_device_id, greeting.to_code, replyText, greetingId).catch(e =>
    console.error(`[greeting] reply push failed: ${e.message}`)
  );

  // 创建反向问候记录：让发起方在 pending-greetings 中看到回复
  // 仅当原问候不是回复（reply_to_id IS NULL）且回复方提供了 fromUserId 时才创建
  if (!greeting.reply_to_id && fromUserId) {
    const senderCode = await toCareCode(greeting.from_device_id);
    // 检查是否已存在反向记录（防重复）
    const existing = await env.DB.prepare(
      'SELECT id FROM greetings WHERE reply_to_id = ?1 LIMIT 1'
    ).bind(greetingId).first<{ id: number }>();
    if (!existing) {
      await env.DB.prepare(
        'INSERT INTO greetings (from_device_id, to_code, message, reply_to_id) VALUES (?1, ?2, ?3, ?4)'
      ).bind(fromUserId, senderCode, replyText, greetingId).run();
    }
  }

  return jsonResponse({ ok: true });
}

async function sendReplyPush(env: Env, fromDeviceId: string, toCode: string, reply: string, greetingId?: number) {
  // 查找发起方的 device_token
  const user = await env.DB.prepare(
    'SELECT device_token FROM users WHERE id = ?1 AND device_token IS NOT NULL AND device_token != \'\''
  ).bind(fromDeviceId).first<{ device_token: string }>();

  if (!user?.device_token) {
    console.log(`[greeting] no device_token for sender ${fromDeviceId}`);
    return;
  }

  // 发起方视角里回复者的名字：优先昵称（已互相关心时）否则关心码
  const displayName = await resolveDisplayName(env, fromDeviceId, toCode);

  try {
    await sendApnsAlert({
      env,
      deviceToken: user.device_token,
      title: '晴好 · 回复',
      body: reply === '晴好' ? `${displayName} 回复：晴好 ✓` : `${displayName} 回复：${reply}`,
      badge: 0,
      data: {
        greetingId: greetingId ?? 0,
        fromCareCode: toCode,
        displayName,
        message: reply,
        createdAt: now(),
        isReply: true,
      },
    });
  } catch (e: any) {
    console.error(`[greeting] reply APNs push failed: ${e.message}`);
  }
}

/**
 * GET /pending-greetings?careCode=XXXXXX
 * 查询未回复且未通知的问安消息，标记为已通知
 */
async function handlePendingGreetings(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const careCode = url.searchParams.get('careCode');

  if (!careCode || careCode.trim().length !== 6) {
    return jsonResponse({ error: 'careCode is required (6 chars)' }, 400);
  }

  const code = careCode.trim().toUpperCase();

  // 查询未通知的问安和回复（用 notified 去重，包含反向回复记录）
  const greetings = await env.DB.prepare(
    'SELECT id, from_device_id, message, created_at, reply_to_id FROM greetings WHERE to_code = ?1 AND reply IS NULL AND notified = 0 ORDER BY created_at DESC LIMIT 20'
  ).bind(code).all<{ id: number; from_device_id: string; message: string; created_at: number; reply_to_id: number | null }>();

  if (!greetings.results || greetings.results.length === 0) {
    return jsonResponse({ greetings: [] });
  }

  // 标记为已通知，防止重复返回
  const ids = greetings.results.map(g => g.id);
  const placeholders = ids.map((_, i) => `?${i + 1}`).join(',');
  await env.DB.prepare(
    `UPDATE greetings SET notified = 1 WHERE id IN (${placeholders})`
  ).bind(...ids).run();

  // 为每个发送者计算关心码
  const result = await Promise.all(
    greetings.results.map(async (g) => ({
      id: g.id,
      fromCareCode: await toCareCode(g.from_device_id),
      message: g.message,
      createdAt: g.created_at,
      isReply: g.reply_to_id != null,
    }))
  );

  return jsonResponse({ greetings: result });
}

/**
 * GET /greeting-history?careCode=XXXXXX&days=7
 * 查询该关心码收到的问安历史（含已回复/未回复、反向回复记录），不标记 notified，
 * 默认返回最近 7 天，days 可指定 1-90。
 */
async function handleGreetingHistory(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const careCode = url.searchParams.get('careCode');

  if (!careCode || careCode.trim().length !== 6) {
    return jsonResponse({ error: 'careCode is required (6 chars)' }, 400);
  }

  const code = careCode.trim().toUpperCase();

  // 时间窗（秒）：默认 7 天
  const daysParam = parseInt(url.searchParams.get('days') || '7', 10);
  const days = Number.isFinite(daysParam) ? Math.min(Math.max(daysParam, 1), 90) : 7;
  const since = now() - days * 86400;

  // 接收方收到的问安：原始问安（reply_to_id IS NULL）+ 反向回复记录（reply_to_id NOT NULL）
  const greetings = await env.DB.prepare(
    'SELECT id, from_device_id, message, reply, replied_at, created_at, reply_to_id FROM greetings WHERE to_code = ?1 AND created_at >= ?2 ORDER BY id DESC LIMIT 100'
  ).bind(code, since).all<{
    id: number; from_device_id: string; message: string; reply: string | null;
    replied_at: number | null; created_at: number; reply_to_id: number | null;
  }>();

  if (!greetings.results || greetings.results.length === 0) {
    return jsonResponse({ history: [] });
  }

  // 接收方自身 deviceId（用于解析接收方视角的发送者昵称）
  const viewerDevice = await findDeviceByCode(env, code);

  const result = await Promise.all(
    (greetings.results ?? []).map(async (g) => {
      const fromCareCode = await toCareCode(g.from_device_id);
      const displayName = viewerDevice
        ? await resolveDisplayName(env, viewerDevice.id, fromCareCode)
        : fromCareCode;
      return {
        id: g.id,
        fromCareCode,
        displayName,
        message: g.message,
        reply: g.reply || null,
        repliedAt: g.replied_at || null,
        createdAt: g.created_at,
        isReply: g.reply_to_id != null,
      };
    })
  );

  return jsonResponse({ history: result });
}

/**
 * 主路由分发
 */
export async function handleGreeting(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);

  // POST /greeting/reply
  if (url.pathname === '/greeting/reply') {
    if (request.method !== 'POST') {
      return jsonResponse({ error: 'method not allowed' }, 405);
    }
    return handleReplyGreeting(request, env);
  }

  // GET /greeting-history?careCode=...
  if (url.pathname === '/greeting-history') {
    if (request.method !== 'GET') {
      return jsonResponse({ error: 'method not allowed' }, 405);
    }
    return handleGreetingHistory(request, env);
  }

  // GET /greeting?careCode=... or /pending-greetings?careCode=...
  if (request.method === 'GET') {
    return handlePendingGreetings(request, env);
  }

  // POST /greeting
  if (request.method === 'POST') {
    return handleSendGreeting(request, env);
  }

  return jsonResponse({ error: 'method not allowed' }, 405);
}
