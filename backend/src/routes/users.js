const express = require('express');
const db = require('../config/db');
const { requireAuth } = require('../middleware/auth');
const { publicUser } = require('./auth');

const router = express.Router();
router.use(requireAuth);

router.get('/me', (req, res) => {
  res.json({ user: publicUser(req.user) });
});

router.put('/me/profile', async (req, res) => {
  try {
    const full_name = String(req.body.full_name || '').trim();
    const city = String(req.body.city || '').trim();
    const vehicle_plate = String(req.body.vehicle_plate || '').trim();

    if (full_name.length < 3 || full_name.length > 120 ||
        city.length < 2 || city.length > 80 ||
        vehicle_plate.length < 3 || vehicle_plate.length > 30) {
      return res.status(400).json({ error: 'invalid_input' });
    }

    // after rejection, editing resubmits the request for review
    const { rows } = await db.query(
      `UPDATE users
       SET full_name = $1, city = $2, vehicle_plate = $3,
           status = CASE WHEN status = 'rejected' THEN 'pending' ELSE status END
       WHERE id = $4
       RETURNING *`,
      [full_name, city, vehicle_plate, req.user.id]
    );
    res.json({ user: publicUser(rows[0]) });
  } catch (e) {
    console.error('profile:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

module.exports = router;
