-- StillHere D1 数据库 Schema
-- 对应 openspec/changes/migrate-to-cloudflare-workers/design.md 决策 7

-- 用户表：被监控人（安好端）
CREATE TABLE IF NOT EXISTS users (
    id              TEXT PRIMARY KEY,          -- 客户端生成的 UUID（UserIdManager）
    device_token    TEXT DEFAULT NULL,         -- APNs device token（可选，push 用）
    last_active_time INTEGER NOT NULL DEFAULT 0, -- 最后活动时间戳（秒）
    threshold_minutes INTEGER NOT NULL DEFAULT 120, -- 超时阈值（分），可动态调整
    contact_id      TEXT DEFAULT NULL,         -- 绑定人 userId（还在端用户）
    is_alerted      INTEGER NOT NULL DEFAULT 0, -- 告警状态：0=未告警, 1=已告警
    is_charging     INTEGER NOT NULL DEFAULT 0, -- 是否充电：0=否, 1=是
    api_token       TEXT DEFAULT NULL,         -- 预留 API 鉴权 token
    created_at      INTEGER NOT NULL DEFAULT (unixepoch()), -- 用户创建时间
    bind_at         INTEGER DEFAULT NULL       -- 绑定时间
);

-- 绑定码表
CREATE TABLE IF NOT EXISTS bind_codes (
    code        TEXT PRIMARY KEY,              -- 6 位数字码
    user_id     TEXT NOT NULL,                 -- 生成此码的用户（被监控人）
    created_at  INTEGER NOT NULL DEFAULT (unixepoch()),
    expires_at  INTEGER NOT NULL,              -- 过期时间（5 分钟）

    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 索引：watchdog 巡检查询优化（只查"已绑定 + 未告警 + 可能超时"的用户）
CREATE INDEX IF NOT EXISTS idx_users_watchdog
    ON users(contact_id, is_alerted, last_active_time)
    WHERE contact_id IS NOT NULL AND is_alerted = 0;
