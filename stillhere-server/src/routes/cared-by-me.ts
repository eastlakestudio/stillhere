import { Env, jsonResponse } from '../shared';

/**
 * GET /cared-by-me?careCode=XXXXXX
 *
 * 查询关注自己的人（关心码列表），并标注是否互相关心。
 * 用于客户端展示"谁在关心我"，支持一键添加关心。
 */
export async function handleCaredByMe(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const careCode = url.searchParams.get('careCode');

  if (!careCode || careCode.trim().length !== 6) {
    return jsonResponse({ error: 'careCode is required (6 chars)' }, 400);
  }

  const code = careCode.trim().toUpperCase();

  // 1. 查询所有关心我的人
  const carers = await env.DB.prepare(
    'SELECT from_device_id FROM care_relations WHERE to_code = ?1 ORDER BY created_at DESC'
  ).bind(code).all<{ from_device_id: string }>();

  if (!carers.results || carers.results.length === 0) {
    return jsonResponse({ carers: [] });
  }

  // 2. 为每个 from_device_id 计算关心码（并行）
  const carerCareCodes: Array<{ careCode: string }> = [];
  await Promise.all(
    [...new Set(carers.results.map(r => r.from_device_id))].map(async (did) => {
      carerCareCodes.push({ careCode: await toCareCode(did) });
    })
  );

  // mutual 由客户端自行判断：用本地 caring 列表比对返回的 careCode
  return jsonResponse({ carers: carerCareCodes });
}

/** 从 deviceId 派生 6 位关心码（SHA-256 → hex → 取第8-13位 → 大写） */
async function toCareCode(deviceId: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(deviceId));
  const hex = Array.from(new Uint8Array(hash), b => b.toString(16).padStart(2, '0')).join('');
  return hex.slice(8, 14).toUpperCase();
}
