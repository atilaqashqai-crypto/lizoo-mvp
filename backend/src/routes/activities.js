const express = require('express');
const db = require('../config/db');
const { requireAuth, requireApproved } = require('../middleware/auth');
const { getSettings } = require('../services/settings');
const { findZone } = require('../services/zones');
const {
  loadPoints,
  calculate,
  detectIdle,
  finishWith,
  publicActivity: pub,
} = require('../services/activity');

const router = express.Router();
router.use(requireAuth, requireApproved);

router.post('/start', async (req, res) => {
  try {
    const { rows } = await db.query(
      'INSERT INTO activities (user_id) VALUES ($1) RETURNING *',
      [req.user.id]
    );
    res.status(201).json({ activity: pub(rows[0]) });
  } catch (e) {
    if (e.code === '23505') {
      const cur = await db.query(
        "SELECT * FROM activities WHERE user_id = $1 AND status = 'active'",
        [req.user.id]
      );
      return res.status(409).json({ error: 'already_active', activity: pub(cur.rows[0]) });
    }
    console.error('start:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

router.get('/active', async (req, res) => {
  try {
    const { rows } = await db.query(
      "SELECT * FROM activities WHERE user_id = $1 AND status = 'active'",
      [req.user.id]
    );
    res.json({ activity: pub(rows[0]) });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

router.get('/', async (req, res) => {
  try {
    const { rows } = await db.query(
      `SELECT * FROM activities WHERE user_id = $1
       ORDER BY started_at DESC LIMIT 30`,
      [req.user.id]
    );
    res.json({ activities: rows.map(pub) });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

router.post('/:id/points', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const input = Array.isArray(req.body.points) ? req.body.points.slice(0, 50) : [];
    if (!Number.isInteger(id) || input.length === 0) {
      return res.status(400).json({ error: 'invalid_input' });
    }

    const result = await db.withTx(async (c) => {
      const a = (
        await c.query(
          'SELECT * FROM activities WHERE id = $1 AND user_id = $2 FOR UPDATE',
          [id, req.user.id]
        )
      ).rows[0];
      if (!a) return { code: 404, body: { error: 'not_found' } };
      if (a.status !== 'active') {
        return { code: 409, body: { error: 'not_active', activity: pub(a) } };
      }

      const s = await getSettings(c);
      const zones = (
        await c.query(
          'SELECT id, center_lat, center_lng, radius_m, multiplier FROM zones WHERE active'
        )
      ).rows;
      const lastRow = (
        await c.query(
          'SELECT max(recorded_at) AS t FROM location_points WHERE activity_id = $1',
          [id]
        )
      ).rows[0];

      const minT = lastRow.t ? lastRow.t.getTime() : a.started_at.getTime();
      const now = Date.now();
      const clean = input
        .map((p) => ({
          lat: Number(p.lat),
          lng: Number(p.lng),
          acc: p.accuracy == null ? null : Number(p.accuracy),
          t: new Date(p.recorded_at).getTime(),
        }))
        .filter(
          (p) =>
            Number.isFinite(p.lat) && Math.abs(p.lat) <= 90 &&
            Number.isFinite(p.lng) && Math.abs(p.lng) <= 180 &&
            Number.isFinite(p.t) && p.t > minT && p.t <= now + 60000 &&
            (p.acc == null || p.acc <= 100)
        )
        .sort((x, y) => x.t - y.t);

      if (clean.length === 0) {
        return { code: 200, body: { activity: pub(a), auto_stopped: false } };
      }

      for (const p of clean) {
        const z = findZone(p, zones);
        await c.query(
          `INSERT INTO location_points
             (activity_id, lat, lng, accuracy_m, zone_id, multiplier, recorded_at)
           VALUES ($1, $2, $3, $4, $5, $6, $7)`,
          [id, p.lat, p.lng, p.acc, z ? z.id : null, z ? z.multiplier : 1, new Date(p.t)]
        );
      }

      const points = await loadPoints(c, id);
      const idleStart = detectIdle(points, s);

      if (idleStart) {
        const done = await finishWith(c, a, points, 'auto_idle', idleStart, s);
        return { code: 200, body: { activity: pub(done), auto_stopped: true } };
      }

      const calc = calculate(points, a.started_at, null, s.baseRate);
      const upd = await c.query(
        `UPDATE activities SET duration_seconds = $2, earnings_toman = $3
         WHERE id = $1 RETURNING *`,
        [id, calc.seconds, calc.earnings]
      );
      return { code: 200, body: { activity: pub(upd.rows[0]), auto_stopped: false } };
    });

    res.status(result.code).json(result.body);
  } catch (e) {
    console.error('points:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

router.post('/:id/stop', async (req, res) => {
  try {
    const id = Number(req.params.id);
    if (!Number.isInteger(id)) return res.status(400).json({ error: 'invalid_input' });

    const result = await db.withTx(async (c) => {
      const a = (
        await c.query(
          'SELECT * FROM activities WHERE id = $1 AND user_id = $2 FOR UPDATE',
          [id, req.user.id]
        )
      ).rows[0];
      if (!a) return { code: 404, body: { error: 'not_found' } };
      if (a.status === 'finished') return { code: 200, body: { activity: pub(a) } };

      const s = await getSettings(c);
      const points = await loadPoints(c, id);
      const done = await finishWith(c, a, points, 'manual', null, s);
      return { code: 200, body: { activity: pub(done) } };
    });

    res.status(result.code).json(result.body);
  } catch (e) {
    console.error('stop:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

module.exports = router;
