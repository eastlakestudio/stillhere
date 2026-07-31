import { Env, jsonResponse } from '../shared';

/**
 * GET /caring?deviceId=XXX
 *
 * 查询"我关心的人"列表，用于新装 App 后恢复关心关系。
 * 返回 care_relations 中 from_device_id 匹配的所有记录。
 */
export async function handleCaring(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const deviceId = url.searchParams.get('deviceId');

  if (!deviceId || deviceId.trim().length === 0) {
    return jsonResponse({ error: 'deviceId is required' }, 400);
  }

  const relations = await env.DB.prepare(
    'SELECT to_code, created_at FROM care_relations WHERE from_device_id = ?1 ORDER BY created_at DESC'
  ).bind(deviceId.trim()).all<{ to_code: string; created_at: number }>();

  if (!relations.results || relations.results.length === 0) {
    return jsonResponse({ caring: [] });
  }

  // 从本设备配置中读取昵称映射（nicknames: {bindCode: name}）
  let nicknames: Record<string, string> = {};
  const cfg = await env.DB.prepare(
    'SELECT config_json FROM device_config WHERE device_id = ?1'
  ).bind(deviceId.trim()).first<{ config_json: string }>();
  if (cfg?.config_json) {
    try {
      const parsed = JSON.parse(cfg.config_json);
      nicknames = parsed.nicknames || {};
    } catch {}
  }

  return jsonResponse({
    caring: relations.results.map(r => ({
      bindCode: r.to_code,
      createdAt: r.created_at,
      name: nicknames[r.to_code] || '',
    })),
  });
}
