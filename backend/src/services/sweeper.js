const db = require('../config/db');
const { getSettings } = require('./settings');
const { loadPoints, finishWith } = require('./activity');

async function sweep() {
  const s = await getSettings();
  const { rows } = await db.query(
    `SELECT a.id
     FROM activities a
     LEFT JOIN location_points p ON p.activity_id = a.id
     WHERE a.status = 'active'
     GROUP BY a.id
     HAVING COALESCE(max(p.recorded_at), a.started_at)
            < now() - ($1 || ' minutes')::interval`,
    [String(s.idleMinutes)]
  );

  for (const r of rows) {
    await db.withTx(async (c) => {
      const a = (
        await c.query('SELECT * FROM activities WHERE id = $1 FOR UPDATE', [r.id])
      ).rows[0];
      if (!a || a.status !== 'active') return;

      const points = await loadPoints(c, a.id);
      const lastSeen = points.length
        ? points[points.length - 1].recorded_at
        : a.started_at;
      if (Date.now() - lastSeen.getTime() < s.idleMinutes * 60000) return;

      await finishWith(c, a, points, 'auto_idle', lastSeen, s);
    });
  }
}

function startSweeper() {
  setInterval(() => {
    sweep().catch((e) => console.error('sweep:', e.message));
  }, 60000);
}

module.exports = { startSweeper };
