import { Env, jsonResponse, now } from '../shared';

/**
 * POST /care  — 创建关心关系
 * DELETE /care?fromUserId=xxx&toCode=YYY — 删除关心关系
 * 
 * 不存储昵称（隐私数据保留在客户端本地）。
 */
export async function handleCare(request: Request, env: Env): Promise<Response> {
  if (request.method === 'DELETE') {
    return handleCareDelete(request, env);
  }

  if (request.method !== 'POST') {
    return jsonResponse({ error: 'method not allowed' }, 405);
  }

  let body: { fromUserId?: string; toCode?: string };
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'invalid JSON' }, 400);
  }

  const { fromUserId, toCode } = body;
  if (!fromUserId || !toCode) {
    return jsonResponse({ error: 'fromUserId and toCode are required' }, 400);
  }

  // 关心码标准化：大写取 6 位
  const code = toCode.trim().toUpperCase().slice(0, 6);
  if (code.length !== 6) {
    return jsonResponse({ error: 'toCode must be 6 characters' }, 400);
  }

  // 检查是否已存在（去重）
  const existing = await env.DB.prepare(
    'SELECT id FROM care_relations WHERE from_device_id = ?1 AND to_code = ?2'
  ).bind(fromUserId, code).first();

  if (existing) {
    return jsonResponse({ ok: true, existed: true });
  }

  await env.DB.prepare(
    'INSERT INTO care_relations (from_device_id, to_code) VALUES (?1, ?2)'
  ).bind(fromUserId, code).run();

  return jsonResponse({ ok: true, existed: false });
}

async function handleCareDelete(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const fromUserId = url.searchParams.get('fromUserId');
  const toCode = url.searchParams.get('toCode');

  if (!fromUserId || !toCode) {
    return jsonResponse({ error: 'fromUserId and toCode are required' }, 400);
  }

  const code = toCode.trim().toUpperCase().slice(0, 6);
  if (code.length !== 6) {
    return jsonResponse({ error: 'toCode must be 6 characters' }, 400);
  }

  await env.DB.prepare(
    'DELETE FROM care_relations WHERE from_device_id = ?1 AND to_code = ?2'
  ).bind(fromUserId, code).run();

  return jsonResponse({ ok: true });
}
