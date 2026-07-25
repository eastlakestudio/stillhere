// ---------- 共享类型 & 工具函数 ----------
// 所有模块导入此文件，避免循环依赖

export interface Env {
  DB: D1Database;
  APNS_P8_KEY: string;    // .p8 私钥内容
  APNS_KEY_ID: string;    // Apple Key ID
  APNS_TEAM_ID: string;   // Apple Team ID
  APP_BUNDLE_ID: string;  // App Bundle ID（推送 topic）
}

export function jsonResponse(data: unknown, status = 200): Response {
  const resp = new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
  resp.headers.set('Access-Control-Allow-Origin', '*');
  resp.headers.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  resp.headers.set('Access-Control-Allow-Headers', 'Content-Type');
  return resp;
}

/** 当前 Unix 秒 */
export function now(): number {
  return Math.floor(Date.now() / 1000);
}
