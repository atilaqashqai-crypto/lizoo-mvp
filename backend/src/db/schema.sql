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
  ('min_settlement_toman', '1000000'),
  ('ad_price_busy', '5000000'),
  ('ad_price_normal', '3000000'),
  ('ad_price_low', '1500000'),
  ('support_whatsapp', '')
ON CONFLICT (key) DO NOTHING;

-- ===== v2 =====
ALTER TABLE users ADD COLUMN IF NOT EXISTS username    VARCHAR(40);
ALTER TABLE users ADD COLUMN IF NOT EXISTS id_doc_file VARCHAR(80);
ALTER TABLE users ADD COLUMN IF NOT EXISTS sheba       VARCHAR(26);
ALTER TABLE users ADD COLUMN IF NOT EXISTS card_number VARCHAR(16);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_username
  ON users (lower(username)) WHERE username IS NOT NULL;

ALTER TABLE settlements ADD COLUMN IF NOT EXISTS sheba       VARCHAR(26);
ALTER TABLE settlements ADD COLUMN IF NOT EXISTS card_number VARCHAR(16);

CREATE TABLE IF NOT EXISTS ad_orders (
  id                BIGSERIAL PRIMARY KEY,
  user_id           BIGINT NOT NULL REFERENCES users(id),
  advertiser_name   VARCHAR(120) NOT NULL,
  contact_phone     VARCHAR(15) NOT NULL,
  boxes_count       INT NOT NULL CHECK (boxes_count > 0),
  location_type     VARCHAR(10) NOT NULL
                    CHECK (location_type IN ('busy', 'normal', 'low')),
  unit_price_toman  BIGINT NOT NULL,
  total_price_toman BIGINT NOT NULL,
  status            VARCHAR(10) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'confirmed', 'rejected')),
  note              TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  reviewed_at       TIMESTAMPTZ,
  reviewed_by       BIGINT REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_ads_status ON ad_orders (status, created_at DESC);

-- one-time: raise the old default minimum settlement to 1,000,000 toman
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM settings WHERE key = 'migr_v2') THEN
    UPDATE settings SET value = '1000000'
      WHERE key = 'min_settlement_toman' AND value = '100000';
    INSERT INTO settings (key, value) VALUES ('migr_v2', '1');
  END IF;
END $$;
