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

router.get('/week', async (req, res) => {
  try {
    const { rows } = await db.query(
      `WITH days AS (
         SELECT d::date AS day FROM generate_series(
           (now() AT TIME ZONE 'Asia/Tehran')::date - 6,
           (now() AT TIME ZONE 'Asia/Tehran')::date,
           interval '1 day') d
       )
       SELECT to_char(days.day, 'YYYY-MM-DD') AS day,
              COALESCE(SUM(a.earnings_toman), 0) AS toman,
              COALESCE(SUM(a.duration_seconds), 0) AS seconds
       FROM days
       LEFT JOIN activities a ON a.user_id = $1
         AND (a.started_at AT TIME ZONE 'Asia/Tehran')::date = days.day
       GROUP BY days.day ORDER BY days.day`,
      [req.user.id]
    );
    const days = rows.map((r) => ({
      day: r.day, toman: Number(r.toman), seconds: Number(r.seconds),
    }));
    res.json({
      days,
      total_toman: days.reduce((a, d) => a + d.toman, 0),
      total_seconds: days.reduce((a, d) => a + d.seconds, 0),
    });
  } catch (e) {
    console.error('week:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

module.exports = router;
