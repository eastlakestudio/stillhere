// ---------- 共享类型 & 工具函数 ----------
// 所有模块导入此文件，避免循环依赖

export interface Env {
  DB: D1Database;
  APNS_P8_KEY: string;       // .p8 私钥内容
  APNS_KEY_ID: string;       // Apple Key ID
  APNS_TEAM_ID: string;      // Apple Team ID
  APP_BUNDLE_ID: string;     // App Bundle ID（推送 topic）
  GOOGLE_CLIENT_ID: string;  // Google OAuth Client ID
  GOOGLE_CLIENT_SECRET: string; // Google OAuth Client Secret
  SESSION_SECRET: string;    // 会话签名密钥（随机字符串）
}

/** 唯一允许访问 dashboard 的邮箱 */
export const ALLOWED_EMAIL = 'mingh.liu@gmail.com';

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

/** 从 deviceId 派生 6 位关心码（SHA-256 → hex → 第8-13位 → 大写） */
export async function toCareCode(deviceId: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(deviceId));
  const hex = Array.from(new Uint8Array(hash), b => b.toString(16).padStart(2, '0')).join('');
  return hex.slice(8, 14).toUpperCase();
}
