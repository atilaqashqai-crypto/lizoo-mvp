const { distanceM } = require('../utils/geo');

// Nearest zone center among zones that contain the point
function findZone(point, zones) {
  let best = null;
  let bestDist = Infinity;
  for (const z of zones) {
    const d = distanceM(point, { lat: z.center_lat, lng: z.center_lng });
    if (d <= z.radius_m && d < bestDist) {
      best = z;
      bestDist = d;
    }
  }
  return best;
}

module.exports = { findZone };
