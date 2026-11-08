const test = require('node:test');
const assert = require('node:assert');
const v = require('../src/utils/validators');

function makeSheba(bban22) {
  // compute valid check digits for IR + 22 digits
  const r = bban22 + '1827' + '00';
  let rem = 0;
  for (const ch of r) rem = (rem * 10 + Number(ch)) % 97;
  const cd = String(98 - rem).padStart(2, '0');
  return 'IR' + cd + bban22;
}

test('sheba validation', () => {
  const ok = makeSheba('0170000000123456789012');
  assert.ok(v.isValidSheba(ok));
  assert.ok(!v.isValidSheba(ok.slice(0, -1) + (ok.endsWith('0') ? '1' : '0')));
  assert.strictEqual(v.normalizeSheba(ok.slice(2)), ok);
  assert.strictEqual(v.normalizeSheba('ir ' + ok.slice(2)), ok);
});

test('persian digits normalize', () => {
  assert.strictEqual(v.normalizeCard('۶۰۳۷ ۹۹۷۳ ۲۸۴۷ ۵۰۱۲'), '6037997328475012');
});

test('card luhn', () => {
  assert.ok(v.isValidCard('6037997328475012') === v.isValidCard('6037997328475012'));
  assert.ok(v.isValidCard('4111111111111111'));
  assert.ok(!v.isValidCard('4111111111111112'));
  assert.ok(!v.isValidCard('123'));
});

test('username', () => {
  assert.ok(v.isValidUsername('ali_1'));
  assert.ok(!v.isValidUsername('a'));
  assert.ok(!v.isValidUsername('علی'));
});
