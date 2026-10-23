#!/usr/bin/env node
// Agente local de PrintOps. Uso: node src/index.js [config.json]
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createSource } from './sources.js';
import { withUsage } from './normalize.js';

const configPath = resolve(process.argv[2] ?? 'config.json');
const config = JSON.parse(readFileSync(configPath, 'utf8'));
const apiUrl = config.apiUrl.replace(/\/+$/, '');
const intervalMs = Math.max(5, config.intervalSeconds ?? 10) * 1000;

const log = (name, msg) => console.log(`${new Date().toISOString()} [${name}] ${msg}`);

async function post(token, body) {
  const res = await fetch(`${apiUrl}/api/agent/telemetry`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Device ${token}` },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(10_000),
  });
  if (res.status === 401) throw new Error('token rechazado (¿se rotó o se borró la integración?)');
  if (!res.ok) throw new Error(`el backend respondió ${res.status}`);
}

const printers = config.printers.map((cfg) => ({
  cfg, source: createSource(cfg), previous: undefined, startedAt: undefined, failing: false,
}));

async function tick(p) {
  let body;
  try {
    const reading = await p.source.read();
    const state = reading.report.state;
    if (state === 'PRINTING' && p.previous !== 'PRINTING' && p.previous !== 'PAUSED') p.startedAt = Date.now();
    body = withUsage(p.previous, reading, p.startedAt, Date.now());
    if (body.usage) log(p.cfg.name, `trabajo terminado: ${JSON.stringify(body.usage)}`);
    p.previous = state;
  } catch (e) {
    // Impresora apagada o inaccesible: se informa como error de conexión una sola vez.
    body = { state: 'OFFLINE', message: String(e.message ?? e).slice(0, 250) };
    if (!p.failing) log(p.cfg.name, `no responde: ${body.message}`);
    p.failing = true;
  }
  try {
    await post(p.cfg.token, body);
    if (p.failing && body.state !== 'OFFLINE') {
      log(p.cfg.name, 'conectada de nuevo');
      p.failing = false;
    }
  } catch (e) {
    log(p.cfg.name, `no se pudo enviar al backend: ${e.message}`);
  }
}

log('agente', `${printers.length} impresora(s), enviando a ${apiUrl} cada ${intervalMs / 1000}s`);
const loop = () => Promise.all(printers.map(tick));
loop();
const timer = setInterval(loop, intervalMs);

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, () => {
    clearInterval(timer);
    printers.forEach((p) => p.source.close());
    process.exit(0);
  });
}
