const express = require('express');
const db = require('../config/db');
const { requireAuth } = require('../middleware/auth');
const { normalizePhone } = require('../utils/phone');

const router = express.Router();
router.use(requireAuth);

const TYPES = ['busy', 'normal', 'low'];

async function loadConfig(client = db) {
  const { rows } = await client.query(
    "SELECT key, value FROM settings WHERE key LIKE 'ad_price_%' OR key = 'support_whatsapp'"
  );
  const m = Object.fromEntries(rows.map((r) => [r.key, r.value]));
  return {
    prices: {
      busy: Number(m.ad_price_busy || 0),
      normal: Number(m.ad_price_normal || 0),
      low: Number(m.ad_price_low || 0),
    },
    support_whatsapp: m.support_whatsapp || '',
  };
}

router.get('/prices', async (req, res) => {
  try {
    res.json(await loadConfig());
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

router.post('/orders', async (req, res) => {
  try {
    const advertiser_name = String(req.body.advertiser_name || '').trim();
    const contact_phone = normalizePhone(req.body.contact_phone);
    const boxes = Number(req.body.boxes_count);
    const type = String(req.body.location_type || '');

    if (advertiser_name.length < 2 || advertiser_name.length > 120 ||
        !contact_phone || !Number.isInteger(boxes) || boxes < 1 || boxes > 1000 ||
        !TYPES.includes(type)) {
      return res.status(400).json({ error: 'invalid_input' });
    }

    const cfg = await loadConfig();
    const unit = cfg.prices[type];
    if (!(unit > 0)) return res.status(400).json({ error: 'price_not_set' });

    const { rows } = await db.query(
      `INSERT INTO ad_orders
         (user_id, advertiser_name, contact_phone, boxes_count, location_type,
          unit_price_toman, total_price_toman)
       VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING *`,
      [req.user.id, advertiser_name, contact_phone, boxes, type, unit, unit * boxes]
    );
    res.status(201).json({ order: rows[0], support_whatsapp: cfg.support_whatsapp });
  } catch (e) {
    console.error('ad order:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

router.get('/orders/mine', async (req, res) => {
  try {
    const { rows } = await db.query(
      `SELECT id, advertiser_name, boxes_count, location_type, total_price_toman,
              status, note, created_at
       FROM ad_orders WHERE user_id = $1 ORDER BY created_at DESC LIMIT 50`,
      [req.user.id]
    );
    res.json({ orders: rows });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

module.exports = router;
