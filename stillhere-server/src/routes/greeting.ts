import { Env, jsonResponse, now } from '../shared';
import { sendApnsAlert } from '../lib/apns';

/** 从 deviceId 派生 6 位关心码 */
async function toCareCode(deviceId: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(deviceId));
  const hex = Array.from(new Uint8Array(hash), b => b.toString(16).padStart(2, '0')).join('');
  return hex.slice(8, 14).toUpperCase();
}

/** 通过关心码查找设备的 device_token（用于推送） */
async function findDeviceTokenByCode(env: Env, careCode: string): Promise<string | null> {
  const { results } = await env.DB.prepare(
    'SELECT id, device_token FROM users WHERE device_token IS NOT NULL AND device_token != \'\''
  ).all<{ id: string; device_token: string }>();

  if (!results) return null;
  for (const row of results) {
    if ((await toCareCode(row.id)) === careCode) {
      return row.device_token;
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

async function sendGreetingPush(env: Env, fromUserId: string, toCode: string, message: string, greetingId: number) {
  const token = await findDeviceTokenByCode(env, toCode);
  if (!token) {
    console.log(`[greeting] no device_token for code ${toCode}`);
    return;
  }

  const fromCode = await findCareCodeByDeviceId(env, fromUserId);
  try {
    await sendApnsAlert({
      env,
      deviceToken: token,
      title: '安好 · 问安',
      body: `${fromCode} 向你问安${message !== '问安' ? '：' + message : ''}`,
      badge: 1,
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
  sendReplyPush(env, greeting.from_device_id, greeting.to_code, replyText).catch(e =>
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

async function sendReplyPush(env: Env, fromDeviceId: string, toCode: string, reply: string) {
  // 查找发起方的 device_token
  const user = await env.DB.prepare(
    'SELECT device_token FROM users WHERE id = ?1 AND device_token IS NOT NULL AND device_token != \'\''
  ).bind(fromDeviceId).first<{ device_token: string }>();

  if (!user?.device_token) {
    console.log(`[greeting] no device_token for sender ${fromDeviceId}`);
    return;
  }

  // 回复方的关心码
  const replyCode = await findCareCodeByDeviceId(env, fromDeviceId);
  // 实际上这里需要的是接收方（即回复方to_code对应的人）的关心码来展示
  // 简化：直接用 toCode 作为回复者

  try {
    await sendApnsAlert({
      env,
      deviceToken: user.device_token,
      title: '安好 · 回复',
      body: reply === '安好' ? '安好 ✓' : reply,
      badge: 0,
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
