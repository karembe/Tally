import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:18484';
const ADMIN = __ENV.ADMIN_TOKEN;
const ACCOUNTS = 50;
const json = { 'Content-Type': 'application/json' };
const failed = new Counter('transfer_failed');

export const options = {
  vus: 50,
  duration: '2m',
  thresholds: { transfer_failed: ['count==0'] },
};

export function setup() {
  const ids = [];
  for (let i = 0; i < ACCOUNTS; i++) {
    const r = http.post(`${BASE}/accounts`, JSON.stringify({ ownerName: `load-${i}`, currency: 'EUR' }), { headers: json });
    const id = r.json('id');
    http.post(`${BASE}/funding`, JSON.stringify({ toAccountId: id, amount: 100000000 }), {
      headers: { ...json, 'X-Admin-Token': ADMIN, 'Idempotency-Key': `load-fund-${Date.now()}-${i}` },
    });
    ids.push(id);
  }
  return { ids };
}

export default function (data) {
  const a = Math.floor(Math.random() * ACCOUNTS);
  let b = Math.floor(Math.random() * ACCOUNTS);
  if (b === a) b = (b + 1) % ACCOUNTS;
  const body = JSON.stringify({ fromAccountId: data.ids[a], toAccountId: data.ids[b], amount: 1 + Math.floor(Math.random() * 100), currency: 'EUR' });
  const r = http.post(`${BASE}/transfers`, body, {
    headers: { ...json, 'Idempotency-Key': `${__VU}-${__ITER}-${Date.now()}` },
  });
  if (!check(r, { 'transfer 201': (x) => x.status === 201 })) failed.add(1);
}
