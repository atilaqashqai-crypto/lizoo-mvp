const express = require('express');
const db = require('../config/db');
const { requireAuth, requireAdmin } = require('../middleware/auth');

const router = express.Router();
router.use(requireAuth, requireAdmin);

router.get('/', async (req, res) => {
  try {
    const status = ['pending', 'confirmed', 'rejected'].includes(req.query.status)
      ? req.query.status
      : 'pending';
    const { rows } = await db.query(
      `SELECT * FROM ad_orders WHERE status = $1
       ORDER BY created_at DESC LIMIT 200`,
      [status]
    );
    res.json({ orders: rows });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

function transition(to) {
  return async (req, res) => {
    try {
      const id = Number(req.params.id);
      if (!Number.isInteger(id)) return res.status(400).json({ error: 'invalid_input' });
      const note = req.body.note ? String(req.body.note).slice(0, 500) : null;
      const { rows } = await db.query(
        `UPDATE ad_orders
         SET status = $1, reviewed_at = now(), reviewed_by = $2,
             note = COALESCE($3, note)
         WHERE id = $4 AND status = 'pending' RETURNING *`,
        [to, req.user.id, note, id]
      );
      if (!rows[0]) return res.status(409).json({ error: 'invalid_state' });
      res.json({ order: rows[0] });
    } catch (e) {
      res.status(500).json({ error: 'server_error' });
    }
  };
}

router.post('/:id/confirm', transition('confirmed'));
router.post('/:id/reject', transition('rejected'));

module.exports = router;
