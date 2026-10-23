import mqtt from 'mqtt';
import { fromBambu, fromMoonraker, fromOctoPrint, mergeBambu } from './normalize.js';

const TIMEOUT_MS = 8000;

async function getJson(url, headers = {}) {
  const res = await fetch(url, { headers, signal: AbortSignal.timeout(TIMEOUT_MS) });
  if (!res.ok) throw new Error(`${url} respondió ${res.status}`);
  return res.json();
}

const trimSlash = (u) => u.replace(/\/+$/, '');

// Cada fuente expone read(): Promise<{report, printSeconds?, filamentGrams?}> y close().
export function createSource(cfg) {
  switch (cfg.type) {
    case 'octoprint': {
      const base = trimSlash(cfg.url);
      const headers = { 'X-Api-Key': cfg.apiKey };
      return {
        async read() {
          const [job, printer] = await Promise.all([
            getJson(`${base}/api/job`, headers),
            getJson(`${base}/api/printer`, headers).catch(() => null), // 409 si no hay conexión serie
          ]);
          return fromOctoPrint(job, printer, cfg);
        },
        close() {},
      };
    }
    case 'moonraker': {
      const base = trimSlash(cfg.url);
      const headers = cfg.apiKey ? { 'X-Api-Key': cfg.apiKey } : {};
      return {
        async read() {
          const data = await getJson(`${base}/printer/objects/query?print_stats&extruder&heater_bed&display_status`, headers);
          return fromMoonraker(data.result, cfg);
        },
        close() {},
      };
    }
    case 'bambu': {
      // Conexión MQTT local (modo LAN) con el código de acceso de la impresora.
      let state = {};
      let lastMessageAt = 0;
      const client = mqtt.connect(`mqtts://${cfg.host}:8883`, {
        username: 'bblp',
        password: cfg.accessCode,
        rejectUnauthorized: false, // certificado autofirmado de la impresora
        protocolVersion: 4,
        reconnectPeriod: 5000,
        connectTimeout: TIMEOUT_MS,
      });
      client.on('connect', () => {
        client.subscribe(`device/${cfg.serial}/report`);
        client.publish(`device/${cfg.serial}/request`,
          JSON.stringify({ pushing: { sequence_id: '0', command: 'pushall' } }));
      });
      client.on('message', (_topic, payload) => {
        try {
          state = mergeBambu(state, JSON.parse(payload.toString()));
          lastMessageAt = Date.now();
        } catch {
          // mensaje no JSON: se ignora
        }
      });
      client.on('error', () => {});
      return {
        async read() {
          if (!lastMessageAt || Date.now() - lastMessageAt > 120_000) {
            throw new Error('sin datos de la impresora por MQTT');
          }
          return fromBambu(state);
        },
        close() {
          client.end(true);
        },
      };
    }
    default:
      throw new Error(`Tipo de impresora desconocido: ${cfg.type}`);
  }
}
