const path = require('path');
const express = require('express');
const db = require('../config/db');
const { requireAuth, requireAdmin } = require('../middleware/auth');

const router = express.Router();
const guard = [requireAuth, requireAdmin];

// ---------- Couriers ----------
router.get('/users', guard, async (req, res) => {
  try {
    const status = ['pending', 'approved', 'rejected'].includes(req.query.status)
      ? req.query.status
      : 'pending';
    const { rows } = await db.query(
      `SELECT id, phone, full_name, username, city, vehicle_plate, status, created_at,
              (id_doc_file IS NOT NULL) AS has_id_doc,
              (sheba IS NOT NULL) AS has_bank
       FROM users
       WHERE role = 'courier' AND status = $1
         AND ($1 <> 'pending' OR full_name IS NOT NULL)
       ORDER BY created_at DESC LIMIT 200`,
      [status]
    );
    res.json({ users: rows });
  } catch (e) {
    console.error('admin users:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

function setUserStatus(to) {
  return async (req, res) => {
    try {
      const id = Number(req.params.id);
      if (!Number.isInteger(id)) return res.status(400).json({ error: 'invalid_input' });
      const { rows } = await db.query(
        `UPDATE users SET status = $1
         WHERE id = $2 AND role = 'courier' AND full_name IS NOT NULL
         RETURNING id, phone, full_name, status`,
        [to, id]
      );
      if (!rows[0]) return res.status(404).json({ error: 'not_found_or_incomplete' });
      res.json({ user: rows[0] });
    } catch (e) {
      console.error('admin user status:', e.message);
      res.status(500).json({ error: 'server_error' });
    }
  };
}
router.post('/users/:id/approve', guard, setUserStatus('approved'));
router.post('/users/:id/reject', guard, setUserStatus('rejected'));

router.get('/users/:id/id-document', guard, async (req, res) => {
  try {
    const id = Number(req.params.id);
    if (!Number.isInteger(id)) return res.status(400).json({ error: 'invalid_input' });
    const { rows } = await db.query('SELECT id_doc_file FROM users WHERE id = $1', [id]);
    const f = rows[0] && rows[0].id_doc_file;
    if (!f || !/^[\w.-]+$/.test(f)) return res.status(404).json({ error: 'not_found' });
    const { UPLOAD_DIR } = require('./users');
    res.set('Cache-Control', 'no-store');
    res.sendFile(path.join(UPLOAD_DIR, f), (err) => {
      if (err && !res.headersSent) res.status(404).json({ error: 'not_found' });
    });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

// ---------- Zones ----------
function num(v) {
  const s = String(v ?? '').trim();
  return s === '' ? NaN : Number(s);
}

function parseZone(b) {
  const z = {
    name: String(b.name || '').trim(),
    lat: num(b.center_lat),
    lng: num(b.center_lng),
    radius: num(b.radius_m),
    mult: num(b.multiplier),
    active: b.active !== false,
  };
  if (z.name.length < 2 || z.name.length > 100) return null;
  if (!(Math.abs(z.lat) <= 90) || !(Math.abs(z.lng) <= 180)) return null;
  if (!Number.isInteger(z.radius) || z.radius < 50 || z.radius > 50000) return null;
  if (!(z.mult >= 0.1 && z.mult <= 10)) return null;
  z.mult = Math.round(z.mult * 100) / 100;
  return z;
}

router.get('/zones', guard, async (req, res) => {
  try {
    const { rows } = await db.query('SELECT * FROM zones ORDER BY id');
    res.json({ zones: rows.map((z) => ({ ...z, multiplier: Number(z.multiplier) })) });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

router.post('/zones', guard, async (req, res) => {
  try {
    const z = parseZone(req.body);
    if (!z) return res.status(400).json({ error: 'invalid_input' });
    const { rows } = await db.query(
      `INSERT INTO zones (name, center_lat, center_lng, radius_m, multiplier, active)
       VALUES ($1,$2,$3,$4,$5,$6) RETURNING *`,
      [z.name, z.lat, z.lng, z.radius, z.mult, z.active]
    );
    res.status(201).json({ zone: rows[0] });
  } catch (e) {
    console.error('zone create:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

router.put('/zones/:id', guard, async (req, res) => {
  try {
    const id = Number(req.params.id);
    const z = parseZone(req.body);
    if (!Number.isInteger(id) || !z) return res.status(400).json({ error: 'invalid_input' });
    const { rows } = await db.query(
      `UPDATE zones SET name=$1, center_lat=$2, center_lng=$3,
         radius_m=$4, multiplier=$5, active=$6
       WHERE id=$7 RETURNING *`,
      [z.name, z.lat, z.lng, z.radius, z.mult, z.active, id]
    );
    if (!rows[0]) return res.status(404).json({ error: 'not_found' });
    res.json({ zone: rows[0] });
  } catch (e) {
    console.error('zone update:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

// ---------- Settings ----------
const LIMITS = {
  base_rate_toman: [1000, 100000000],
  idle_minutes: [5, 240],
  idle_radius_m: [10, 500],
  min_settlement_toman: [0, 100000000],
  ad_price_busy: [0, 1000000000],
  ad_price_normal: [0, 1000000000],
  ad_price_low: [0, 1000000000],
};
const TEXT_KEYS = { support_whatsapp: /^\+?\d{0,15}$/ };

router.get('/settings', guard, async (req, res) => {
  try {
    const { rows } = await db.query('SELECT key, value FROM settings');
    const out = {};
    for (const r of rows) {
      if (LIMITS[r.key]) out[r.key] = Number(r.value);
      else if (TEXT_KEYS[r.key]) out[r.key] = r.value;
    }
    res.json({ settings: out });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

router.put('/settings', guard, async (req, res) => {
  try {
    const entries = Object.entries(req.body || {}).filter(
      ([k]) => LIMITS[k] || TEXT_KEYS[k]
    );
    if (entries.length === 0) return res.status(400).json({ error: 'invalid_input' });
    for (const [k, v] of entries) {
      if (TEXT_KEYS[k]) {
        if (!TEXT_KEYS[k].test(String(v ?? '').trim())) {
          return res.status(400).json({ error: 'invalid_input', field: k });
        }
        continue;
      }
      const n = num(v);
      const [min, max] = LIMITS[k];
      if (!Number.isInteger(n) || n < min || n > max) {
        return res.status(400).json({ error: 'invalid_input', field: k });
      }
    }
    await db.withTx(async (c) => {
      for (const [k, v] of entries) {
        await c.query(
          `INSERT INTO settings (key, value) VALUES ($1, $2)
           ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value`,
          [k, TEXT_KEYS[k] ? String(v ?? '').trim() : String(num(v))]
        );
      }
    });
    res.json({ ok: true });
  } catch (e) {
    console.error('settings:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

// ---------- Stats ----------
router.get('/stats', guard, async (req, res) => {
  try {
    const { rows } = await db.query(
      `SELECT
         (SELECT count(*) FROM users
            WHERE role='courier' AND status='pending' AND full_name IS NOT NULL) AS pc,
         (SELECT count(*) FROM settlements WHERE status='pending') AS ps,
         (SELECT count(*) FROM activities WHERE status='active') AS aa,
         (SELECT COALESCE(sum(earnings_toman),0) FROM activities
            WHERE started_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Tehran')
                                 AT TIME ZONE 'Asia/Tehran')) AS te`
    );
    const r = rows[0];
    res.json({
      pending_couriers: Number(r.pc),
      pending_settlements: Number(r.ps),
      active_activities: Number(r.aa),
      today_earnings_toman: Number(r.te),
    });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

module.exports = router;
