require('dotenv').config();
const app = require('./app');
const { startSweeper } = require('./services/sweeper');

const port = process.env.PORT || 3000;
app.listen(port, () => {
  console.log(`Lizoo API on :${port}`);
  startSweeper();
});
