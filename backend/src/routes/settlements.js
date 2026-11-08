const express = require('express');
const db = require('../config/db');
const { requireAuth, requireApproved } = require('../middleware/auth');
const { getBalance } = require('../services/balance');
const { getSettings } = require('../services/settings');

const router = express.Router();
router.use(requireAuth, requireApproved);

router.post('/request', async (req, res) => {
  try {
    const result = await db.withTx(async (c) => {
      await c.query('SELECT id FROM users WHERE id = $1 FOR UPDATE', [req.user.id]);
      const u = (await c.query('SELECT sheba, card_number, id_doc_file FROM users WHERE id = $1', [req.user.id])).rows[0];
      if (!u.id_doc_file) return { code: 400, body: { error: 'id_document_required' } };
      if (!u.sheba || !u.card_number) return { code: 400, body: { error: 'bank_info_required' } };
      const s = await getSettings(c);
      const b = await getBalance(c, req.user.id);

      const amount =
        req.body.amount == null ? b.available_toman : Number(req.body.amount);

      if (!Number.isInteger(amount) || amount <= 0) {
        return { code: 400, body: { error: 'invalid_amount' } };
      }
      if (amount > b.available_toman) {
        return {
          code: 400,
          body: { error: 'insufficient_balance', available_toman: b.available_toman },
        };
      }
      if (amount < s.minSettlement) {
        return { code: 400, body: { error: 'below_minimum', min_toman: s.minSettlement } };
      }

      const ins = await c.query(
        `INSERT INTO settlements (user_id, amount_toman, sheba, card_number)
         VALUES ($1, $2, $3, $4) RETURNING *`,
        [req.user.id, amount, u.sheba, u.card_number]
      );
      return { code: 201, body: { settlement: ins.rows[0] } };
    });
    res.status(result.code).json(result.body);
  } catch (e) {
    if (e.code === '23505') {
      return res.status(409).json({ error: 'open_request_exists' });
    }
    console.error('settlement request:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

router.get('/mine', async (req, res) => {
  try {
    const { rows } = await db.query(
      `SELECT id, amount_toman, status, note, requested_at, reviewed_at
       FROM settlements WHERE user_id = $1
       ORDER BY requested_at DESC LIMIT 30`,
      [req.user.id]
    );
    res.json({ settlements: rows });
  } catch (e) {
    res.status(500).json({ error: 'server_error' });
  }
});

module.exports = router;
