const express = require('express');
const db = require('../config/db');
const { requireAuth } = require('../middleware/auth');
const { normalizePhone } = require('../utils/phone');

const router = express.Router();
router.use(requireAuth);

// available = admin-set stock minus motors currently busy in active orders
const TYPES_SQL = `
  SELECT t.id, t.code, t.name, t.price_per_hour_toman, t.hours_default,
         GREATEST(0, t.available_count - COALESCE((
           SELECT SUM(o.motors_count) FROM motor_orders o
           WHERE o.box_code = t.code AND o.status = 'active' AND o.ends_at > now()
         ), 0))::int AS available
  FROM motor_box_types t
  WHERE t.active
  ORDER BY t.sort, t.id`;

async function loadTypes() {
  const { rows } = await db.query(TYPES_SQL);
  return rows.map((r) => ({ ...r, price_per_hour_toman: Number(r.price_per_hour_toman) }));
}

async function loadConfig() {
  const { rows } = await db.query(
    "SELECT key, value FROM settings WHERE key IN ('support_whatsapp', 'payment_info')"
  );
  const m = Object.fromEntries(rows.map((r) => [r.key, r.value]));
  return { support_whatsapp: m.support_whatsapp || '', payment_info: m.payment_info || '' };
}

router.get('/box-types', async (req, res) => {
  try {
    res.json({ box_types: await loadTypes(), ...(await loadConfig()) });
  } catch (e) {
    console.error('box-types:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

router.post('/orders', async (req, res) => {
  try {
    const advertiser_name = String(req.body.advertiser_name || '').trim();
    const contact_phone = normalizePhone(req.body.contact_phone);
    const code = String(req.body.box_code || '');
    const motors = Number(req.body.motors_count);
    const hours = Number(req.body.hours);

    if (advertiser_name.length < 2 || advertiser_name.length > 120 || !contact_phone ||
        !Number.isInteger(motors) || motors < 1 || motors > 100 ||
        !Number.isInteger(hours) || hours < 1 || hours > 24) {
      return res.status(400).json({ error: 'invalid_input' });
    }

    const t = (await loadTypes()).find((x) => x.code === code);
    if (!t) return res.status(400).json({ error: 'invalid_box_type' });
    if (!(t.price_per_hour_toman > 0)) return res.status(400).json({ error: 'price_not_set' });
    if (motors > t.available) return res.status(409).json({ error: 'not_enough_motors' });

    const unit = t.price_per_hour_toman;
    const total = unit * hours * motors;
    const { rows } = await db.query(
      `INSERT INTO motor_orders
         (user_id, advertiser_name, contact_phone, box_code, motors_count, hours,
          unit_price_toman, total_price_toman)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
      [req.user.id, advertiser_name, contact_phone, code, motors, hours, unit, total]
    );
    const o = rows[0];
    res.status(201).json({
      order: {
        id: Number(o.id),
        advertiser_name: o.advertiser_name,
        box_code: o.box_code,
        box_name: t.name,
        motors_count: o.motors_count,
        hours: o.hours,
        total_price_toman: Number(o.total_price_toman),
        status: 'pending',
        created_at: o.created_at,
        remaining_seconds: 0,
      },
      ...(await loadConfig()),
    });
  } catch (e) {
    console.error('motor order:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

// status shown to the user: pending | active | done | rejected
router.get('/orders/mine', async (req, res) => {
  try {
    const { rows } = await db.query(
      `SELECT o.id, o.advertiser_name, o.box_code, t.name AS box_name,
              o.motors_count, o.hours, o.total_price_toman, o.note,
              o.created_at, o.started_at, o.ends_at,
              CASE WHEN o.status = 'active' AND o.ends_at <= now() THEN 'done'
                   ELSE o.status END AS status,
              CASE WHEN o.status = 'active' AND o.ends_at > now()
                   THEN EXTRACT(EPOCH FROM (o.ends_at - now()))::bigint
                   ELSE 0 END AS remaining_seconds
       FROM motor_orders o JOIN motor_box_types t ON t.code = o.box_code
       WHERE o.user_id = $1
       ORDER BY o.created_at DESC LIMIT 100`,
      [req.user.id]
    );
    res.json({
      orders: rows.map((r) => ({
        ...r,
        id: Number(r.id),
        total_price_toman: Number(r.total_price_toman),
        remaining_seconds: Number(r.remaining_seconds),
      })),
    });
  } catch (e) {
    console.error('orders mine:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

module.exports = router;
