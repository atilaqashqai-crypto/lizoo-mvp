CREATE TABLE IF NOT EXISTS users (
  id            BIGSERIAL PRIMARY KEY,
  phone         VARCHAR(15) UNIQUE NOT NULL,
  full_name     VARCHAR(120),
  city          VARCHAR(80),
  vehicle_plate VARCHAR(30),
  role          VARCHAR(10) NOT NULL DEFAULT 'courier'
                CHECK (role IN ('courier', 'admin')),
  status        VARCHAR(10) NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending', 'approved', 'rejected')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS otp_codes (
  id         BIGSERIAL PRIMARY KEY,
  phone      VARCHAR(15) NOT NULL,
  code_hash  TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  attempts   INT NOT NULL DEFAULT 0,
  used       BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_otp_phone ON otp_codes (phone, created_at DESC);

CREATE TABLE IF NOT EXISTS zones (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  center_lat  DOUBLE PRECISION NOT NULL,
  center_lng  DOUBLE PRECISION NOT NULL,
  radius_m    INT NOT NULL DEFAULT 1000,
  multiplier  NUMERIC(4,2) NOT NULL DEFAULT 1.00,
  active      BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS activities (
  id               BIGSERIAL PRIMARY KEY,
  user_id          BIGINT NOT NULL REFERENCES users(id),
  started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at         TIMESTAMPTZ,
  end_reason       VARCHAR(20)
                   CHECK (end_reason IN ('manual', 'auto_idle', 'admin')),
  duration_seconds INT NOT NULL DEFAULT 0,
  earnings_toman   BIGINT NOT NULL DEFAULT 0,
  status           VARCHAR(10) NOT NULL DEFAULT 'active'
                   CHECK (status IN ('active', 'finished'))
);
CREATE INDEX IF NOT EXISTS idx_act_user ON activities (user_id, started_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_active_per_user
  ON activities (user_id) WHERE status = 'active';

CREATE TABLE IF NOT EXISTS location_points (
  id          BIGSERIAL PRIMARY KEY,
  activity_id BIGINT NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
  lat         DOUBLE PRECISION NOT NULL,
  lng         DOUBLE PRECISION NOT NULL,
  accuracy_m  REAL,
  zone_id     BIGINT REFERENCES zones(id),
  multiplier  NUMERIC(4,2) NOT NULL DEFAULT 1.00,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE location_points
  ADD COLUMN IF NOT EXISTS multiplier NUMERIC(4,2) NOT NULL DEFAULT 1.00;
CREATE INDEX IF NOT EXISTS idx_points_act ON location_points (activity_id, recorded_at);

CREATE TABLE IF NOT EXISTS settlements (
  id           BIGSERIAL PRIMARY KEY,
  user_id      BIGINT NOT NULL REFERENCES users(id),
  amount_toman BIGINT NOT NULL CHECK (amount_toman > 0),
  status       VARCHAR(10) NOT NULL DEFAULT 'pending'
               CHECK (status IN ('pending', 'approved', 'paid', 'rejected')),
  note         TEXT,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  reviewed_at  TIMESTAMPTZ,
  reviewed_by  BIGINT REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_settle_status ON settlements (status, requested_at);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_open_settlement
  ON settlements (user_id) WHERE status IN ('pending', 'approved');

CREATE TABLE IF NOT EXISTS settings (
  key   VARCHAR(50) PRIMARY KEY,
  value TEXT NOT NULL
);
INSERT INTO settings (key, value) VALUES
  ('base_rate_toman', '40000'),
  ('idle_minutes', '30'),
  ('idle_radius_m', '50'),
  ('min_settlement_toman', '100000')
ON CONFLICT (key) DO NOTHING;
