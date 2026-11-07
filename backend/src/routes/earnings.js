const express = require('express');
const db = require('../config/db');
const { requireAuth, requireApproved } = require('../middleware/auth');
const { getBalance } = require('../services/balance');
const { getSettings } = require('../services/settings');

const router = express.Router();
router.use(requireAuth, requireApproved);

router.get('/summary', async (req, res) => {
  try {
    const { rows } = await db.query(
      `SELECT
         COALESCE(SUM(earnings_toman) FILTER (
           WHERE started_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Tehran')
                                AT TIME ZONE 'Asia/Tehran')
         ), 0) AS today,
         COALESCE(SUM(earnings_toman), 0) AS total
       FROM activities WHERE user_id = $1`,
      [req.user.id]
    );
    const balance = await getBalance(db, req.user.id);
    const s = await getSettings();
    res.json({
      today_toman: Number(rows[0].today),
      total_toman: Number(rows[0].total),
      available_toman: balance.available_toman,
      in_process_toman: balance.in_process_toman,
      paid_toman: balance.paid_toman,
      min_settlement_toman: s.minSettlement,
    });
  } catch (e) {
    console.error('summary:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

module.exports = router;
