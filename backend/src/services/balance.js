async function getBalance(client, userId) {
  const a = await client.query(
    `SELECT COALESCE(SUM(earnings_toman), 0) AS finished
     FROM activities WHERE user_id = $1 AND status = 'finished'`,
    [userId]
  );
  const s = await client.query(
    `SELECT
       COALESCE(SUM(amount_toman) FILTER (WHERE status IN ('pending','approved')), 0) AS in_process,
       COALESCE(SUM(amount_toman) FILTER (WHERE status = 'paid'), 0) AS paid
     FROM settlements WHERE user_id = $1`,
    [userId]
  );
  const finished = Number(a.rows[0].finished);
  const inProcess = Number(s.rows[0].in_process);
  const paid = Number(s.rows[0].paid);
  return {
    finished_toman: finished,
    in_process_toman: inProcess,
    paid_toman: paid,
    available_toman: finished - inProcess - paid,
  };
}

module.exports = { getBalance };
