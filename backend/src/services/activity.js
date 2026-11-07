const { distanceM } = require('../utils/geo');

const MAX_GAP_SECONDS = 300; // longer gaps between points are capped

async function loadPoints(c, activityId) {
  const { rows } = await c.query(
    `SELECT lat, lng, multiplier, recorded_at
     FROM location_points WHERE activity_id = $1 ORDER BY recorded_at`,
    [activityId]
  );
  return rows.map((r) => ({
    lat: r.lat,
    lng: r.lng,
    multiplier: Number(r.multiplier),
    recorded_at: r.recorded_at,
  }));
}

// earnings = sum over segments of (seconds / 3600) x base rate x zone multiplier
function calculate(points, startedAt, untilMs, baseRate) {
  let prev = new Date(startedAt).getTime();
  let seconds = 0;
  let money = 0;
  for (const p of points) {
    const t = p.recorded_at.getTime();
    if (untilMs != null && t > untilMs) break;
    const dt = Math.min(Math.max((t - prev) / 1000, 0), MAX_GAP_SECONDS);
    seconds += dt;
    money += (dt / 3600) * baseRate * p.multiplier;
    prev = t;
  }
  return { seconds: Math.round(seconds), earnings: Math.round(money) };
}

// Returns the Date the courier became stationary, or null
function detectIdle(points, s) {
  if (points.length < 2) return null;
  const last = points[points.length - 1];
  let idx = points.length - 1;
  while (idx > 0 && distanceM(points[idx - 1], last) <= s.idleRadiusM) idx--;
  const idleStart = points[idx].recorded_at;
  const minutes = (last.recorded_at - idleStart) / 60000;
  return minutes >= s.idleMinutes ? idleStart : null;
}

async function finishWith(c, activity, points, reason, endAt, s) {
  const calc = calculate(
    points,
    activity.started_at,
    endAt ? endAt.getTime() : null,
    s.baseRate
  );
  const { rows } = await c.query(
    `UPDATE activities
     SET status = 'finished', ended_at = $2, end_reason = $3,
         duration_seconds = $4, earnings_toman = $5
     WHERE id = $1 RETURNING *`,
    [activity.id, endAt || new Date(), reason, calc.seconds, calc.earnings]
  );
  return rows[0];
}

function publicActivity(a) {
  if (!a) return null;
  return {
    id: Number(a.id),
    status: a.status,
    started_at: a.started_at,
    ended_at: a.ended_at,
    end_reason: a.end_reason,
    duration_seconds: Number(a.duration_seconds),
    earnings_toman: Number(a.earnings_toman),
  };
}

module.exports = {
  loadPoints,
  calculate,
  detectIdle,
  finishWith,
  publicActivity,
};
