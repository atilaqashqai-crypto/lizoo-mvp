const express = require('express');
const db = require('../config/db');
const { requireAuth, requireAdmin } = require('../middleware/auth');

const router = express.Router();
router.use(requireAuth, requireAdmin);

router.get('/', async (req, res) => {
  try {
    const status = req.query.status || 'pending';
    const { rows } = await db.query(
      `SELECT s.*, u.full_name, u.phone, u.username
       FROM settlements s JOIN users u ON u.id = s.user_id
       WHERE s.status = $1
       ORDER BY s.requested_at ASC LIMIT 200`,
      [status]
    );
    res.json({ settlements: rows });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

function transition(from, to) {
  return async (req, res) => {
    try {
      const id = Number(req.params.id);
      if (!Number.isInteger(id)) return res.status(400).json({ error: 'invalid_input' });
      const note = req.body.note ? String(req.body.note).slice(0, 500) : null;

      const { rows } = await db.query(
        `UPDATE settlements
         SET status = $1, reviewed_at = now(), reviewed_by = $2,
             note = COALESCE($3, note)
         WHERE id = $4 AND status = ANY($5)
         RETURNING *`,
        [to, req.user.id, note, id, from]
      );
      if (!rows[0]) return res.status(409).json({ error: 'invalid_state' });
      res.json({ settlement: rows[0] });
    } catch (e) {
      console.error('settlement transition:', e.message);
      res.status(500).json({ error: 'server_error' });
    }
  };
}

router.post('/:id/approve', transition(['pending'], 'approved'));
router.post('/:id/pay', transition(['approved'], 'paid'));
router.post('/:id/reject', transition(['pending', 'approved'], 'rejected'));

module.exports = router;
