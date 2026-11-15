const express = require('express');
const db = require('../config/db');
const { requireAuth, requireAdmin } = require('../middleware/auth');

const router = express.Router();
router.use(requireAuth, requireAdmin);

const STATUSES = ['pending', 'active', 'done', 'rejected', 'all'];

router.get('/', async (req, res) => {
  try {
    const status = STATUSES.includes(req.query.status) ? req.query.status : 'pending';
    const { rows } = await db.query(
      `SELECT * FROM (
         SELECT o.*, t.name AS box_name,
                CASE WHEN o.status = 'active' AND o.ends_at <= now() THEN 'done'
                     ELSE o.status END AS shown_status
         FROM motor_orders o JOIN motor_box_types t ON t.code = o.box_code
       ) x
       WHERE ($1 = 'all' OR shown_status = $1)
       ORDER BY created_at DESC LIMIT 200`,
      [status]
    );
    res.json({ orders: rows });
  } catch (e) {
    console.error('admin ads:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

// ----- box types (price per hour, default hours, stock) -----
router.get('/box-types', async (req, res) => {
  try {
    const { rows } = await db.query('SELECT * FROM motor_box_types ORDER BY sort, id');
    res.json({ box_types: rows });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

router.put('/box-types/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const price = Number(req.body.price_per_hour_toman);
    const hours = Number(req.body.hours_default);
    const stock = Number(req.body.available_count);
    const name = String(req.body.name || '').trim();
    const active = req.body.active !== false;
    if (!Number.isInteger(id) || name.length < 2 || name.length > 60 ||
        !Number.isInteger(price) || price < 0 || price > 1e9 ||
        !Number.isInteger(hours) || hours < 1 || hours > 24 ||
        !Number.isInteger(stock) || stock < 0 || stock > 10000) {
      return res.status(400).json({ error: 'invalid_input' });
    }
    const { rows } = await db.query(
      `UPDATE motor_box_types
       SET name = $1, price_per_hour_toman = $2, hours_default = $3,
           available_count = $4, active = $5
       WHERE id = $6 RETURNING *`,
      [name, price, hours, stock, active, id]
    );
    if (!rows[0]) return res.status(404).json({ error: 'not_found' });
    res.json({ box_type: rows[0] });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

// confirm = payment verified: ad goes live now and runs for `hours`
router.post('/:id/confirm', async (req, res) => {
  try {
    const id = Number(req.params.id);
    if (!Number.isInteger(id)) return res.status(400).json({ error: 'invalid_input' });
    const { rows } = await db.query(
      `UPDATE motor_orders
       SET status = 'active', started_at = now(),
           ends_at = now() + (hours * interval '1 hour'), reviewed_by = $2
       WHERE id = $1 AND status = 'pending' RETURNING *`,
      [id, req.user.id]
    );
    if (!rows[0]) return res.status(409).json({ error: 'invalid_state' });
    res.json({ order: rows[0] });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

router.post('/:id/reject', async (req, res) => {
  try {
    const id = Number(req.params.id);
    if (!Number.isInteger(id)) return res.status(400).json({ error: 'invalid_input' });
    const note = req.body.note ? String(req.body.note).slice(0, 500) : null;
    const { rows } = await db.query(
      `UPDATE motor_orders SET status = 'rejected', note = $2, reviewed_by = $3
       WHERE id = $1 AND status = 'pending' RETURNING *`,
      [id, note, req.user.id]
    );
    if (!rows[0]) return res.status(409).json({ error: 'invalid_state' });
    res.json({ order: rows[0] });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

module.exports = router;
