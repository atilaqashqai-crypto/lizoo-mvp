const test = require('node:test');
const assert = require('node:assert/strict');

const { normalizePhone } = require('../src/utils/phone');
const { distanceM } = require('../src/utils/geo');
const { findZone } = require('../src/services/zones');
const { calculate, detectIdle } = require('../src/services/activity');

const T0 = new Date('2026-01-01T10:00:00Z').getTime();
const at = (sec) => new Date(T0 + sec * 1000);

test('normalizePhone: common formats', () => {
  assert.equal(normalizePhone('09121234567'), '09121234567');
  assert.equal(normalizePhone('+989121234567'), '09121234567');
  assert.equal(normalizePhone('9121234567'), '09121234567');
  assert.equal(normalizePhone('۰۹۱۲۱۲۳۴۵۶۷'), '09121234567');
  assert.equal(normalizePhone('123'), null);
  assert.equal(normalizePhone(''), null);
});

test('distanceM: zero and about 111m per 0.001 degree of latitude', () => {
  const a = { lat: 35, lng: 51 };
  assert.equal(distanceM(a, a), 0);
  const d = distanceM(a, { lat: 35.001, lng: 51 });
  assert.ok(d > 110 && d < 112, `got ${d}`);
});

test('findZone: nearest center wins, outside returns null', () => {
  const zones = [
    { id: 1, center_lat: 35.0, center_lng: 51.0, radius_m: 5000, multiplier: 1 },
    { id: 2, center_lat: 35.01, center_lng: 51.0, radius_m: 5000, multiplier: 2 },
  ];
  assert.equal(findZone({ lat: 35.009, lng: 51.0 }, zones).id, 2);
  assert.equal(findZone({ lat: 36.0, lng: 52.0 }, zones), null);
});

test('calculate: hours x rate x multiplier', () => {
  const points = [
    { lat: 35, lng: 51, multiplier: 1, recorded_at: at(60) },
    { lat: 35, lng: 51, multiplier: 2, recorded_at: at(120) },
  ];
  const r = calculate(points, at(0), null, 36000);
  assert.equal(r.seconds, 120);
  assert.equal(r.earnings, 600 + 1200);
});

test('calculate: a gap is capped at 300 seconds', () => {
  const points = [{ lat: 35, lng: 51, multiplier: 1, recorded_at: at(1000) }];
  const r = calculate(points, at(0), null, 36000);
  assert.equal(r.seconds, 300);
  assert.equal(r.earnings, 3000);
});

test('calculate: stops counting after the end time', () => {
  const points = [
    { lat: 35, lng: 51, multiplier: 1, recorded_at: at(60) },
    { lat: 35, lng: 51, multiplier: 1, recorded_at: at(120) },
  ];
  const r = calculate(points, at(0), at(60).getTime(), 36000);
  assert.equal(r.seconds, 60);
});

test('detectIdle: returns the moment the courier became stationary', () => {
  const s = { idleMinutes: 30, idleRadiusM: 50 };
  const points = [{ lat: 35.0, lng: 51, recorded_at: at(0) }];
  for (let i = 0; i <= 7; i++) {
    points.push({ lat: 35.001, lng: 51, recorded_at: at(60 + i * 300) });
  }
  const idleStart = detectIdle(points, s);
  assert.equal(idleStart.getTime(), at(60).getTime());
});

test('detectIdle: short stop or movement is not idle', () => {
  const s = { idleMinutes: 30, idleRadiusM: 50 };
  const short = [];
  for (let i = 0; i <= 2; i++) {
    short.push({ lat: 35, lng: 51, recorded_at: at(i * 300) });
  }
  assert.equal(detectIdle(short, s), null);

  const moving = [];
  for (let i = 0; i <= 10; i++) {
    moving.push({ lat: 35 + i * 0.001, lng: 51, recorded_at: at(i * 300) });
  }
  assert.equal(detectIdle(moving, s), null);
});
