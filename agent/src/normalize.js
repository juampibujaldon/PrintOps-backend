// Traducción del estado de cada fuente al formato de PrintOps (estado normalizado,
// progreso 0-100, temperaturas, nombre del trabajo y segundos restantes). Funciones puras:
// se testean sin impresoras reales.

const DEFAULT_DIAMETER_MM = 1.75;
const DEFAULT_DENSITY_G_CM3 = 1.24; // PLA

// Milímetros de filamento → gramos (volumen del cilindro × densidad).
export function filamentMmToGrams(mm, diameter = DEFAULT_DIAMETER_MM, density = DEFAULT_DENSITY_G_CM3) {
  if (!Number.isFinite(mm) || mm <= 0) return 0;
  const radiusCm = diameter / 20;
  const volumeCm3 = Math.PI * radiusCm * radiusCm * (mm / 10);
  return round(volumeCm3 * density, 2);
}

export function round(v, decimals = 1) {
  const f = 10 ** decimals;
  return Math.round(v * f) / f;
}

const num = (v) => (Number.isFinite(Number(v)) ? Number(v) : undefined);

// OctoPrint: GET /api/job + GET /api/printer.
export function fromOctoPrint(job, printer, opts = {}) {
  const stateText = (job?.state ?? '').toLowerCase();
  let state = 'IDLE';
  if (stateText.startsWith('printing')) state = 'PRINTING';
  else if (stateText.startsWith('paused') || stateText.startsWith('pausing')) state = 'PAUSED';
  else if (stateText.includes('error') || stateText.startsWith('offline')) state = 'ERROR';
  else if (stateText.startsWith('operational') && (job?.progress?.completion ?? 0) >= 100) state = 'FINISHED';

  const completion = num(job?.progress?.completion);
  const lengthMm = num(job?.job?.filament?.tool0?.length);
  return {
    report: {
      state,
      progress: completion !== undefined ? round(Math.min(100, completion), 1) : undefined,
      nozzleTemp: num(printer?.temperature?.tool0?.actual),
      bedTemp: num(printer?.temperature?.bed?.actual),
      jobName: job?.job?.file?.name ?? undefined,
      remainingSeconds: num(job?.progress?.printTimeLeft),
      message: state === 'ERROR' ? job?.state : undefined,
    },
    printSeconds: num(job?.progress?.printTime),
    filamentGrams: lengthMm !== undefined
      ? filamentMmToGrams(lengthMm * (completion ?? 100) / 100, opts.filamentDiameter, opts.filamentDensity)
      : undefined,
  };
}

// Moonraker: GET /printer/objects/query?print_stats&extruder&heater_bed&display_status.
export function fromMoonraker(result, opts = {}) {
  const s = result?.status ?? {};
  const ps = s.print_stats ?? {};
  const map = { printing: 'PRINTING', paused: 'PAUSED', complete: 'FINISHED', error: 'ERROR', cancelled: 'IDLE', standby: 'IDLE' };
  const state = map[ps.state] ?? 'IDLE';
  const progress = num(s.display_status?.progress);
  const duration = num(ps.print_duration);
  let remaining;
  if (state === 'PRINTING' && progress && progress > 0.01 && duration) {
    remaining = Math.round(duration / progress - duration);
  }
  return {
    report: {
      state,
      progress: progress !== undefined ? round(progress * 100, 1) : undefined,
      nozzleTemp: num(s.extruder?.temperature),
      bedTemp: num(s.heater_bed?.temperature),
      jobName: ps.filename || undefined,
      remainingSeconds: remaining,
      message: state === 'ERROR' ? ps.message || undefined : undefined,
    },
    printSeconds: duration,
    filamentGrams: num(ps.filament_used) !== undefined
      ? filamentMmToGrams(num(ps.filament_used), opts.filamentDiameter, opts.filamentDensity)
      : undefined,
  };
}

// Bambu Lab (MQTT device/<serial>/report, objeto "print"). Los reportes son parciales:
// se combinan con el último estado conocido.
export function mergeBambu(previous, message) {
  const p = message?.print;
  if (!p) return previous ?? {};
  return { ...(previous ?? {}), ...p };
}

export function fromBambu(print) {
  const map = { RUNNING: 'PRINTING', PREPARE: 'PRINTING', PAUSE: 'PAUSED', FINISH: 'FINISHED', FAILED: 'ERROR', IDLE: 'IDLE' };
  const state = map[print?.gcode_state] ?? 'IDLE';
  const minutesLeft = num(print?.mc_remaining_time);
  return {
    report: {
      state,
      progress: num(print?.mc_percent),
      nozzleTemp: num(print?.nozzle_temper),
      bedTemp: num(print?.bed_temper),
      jobName: print?.subtask_name || print?.gcode_file || undefined,
      remainingSeconds: minutesLeft !== undefined ? Math.round(minutesLeft * 60) : undefined,
      message: state === 'ERROR' && print?.print_error ? `Código ${print.print_error}` : undefined,
    },
    // Bambu no informa gramos ni tiempo total por MQTT: el agente mide el tiempo entre
    // el inicio y el fin del trabajo.
    printSeconds: undefined,
    filamentGrams: undefined,
  };
}

// Arma el envío: agrega `usage` solo en la transición a FINISHED (una vez por trabajo).
export function withUsage(previousState, reading, startedAtMs, nowMs) {
  const body = Object.fromEntries(Object.entries(reading.report).filter(([, v]) => v !== undefined));
  const justFinished = reading.report.state === 'FINISHED' && previousState !== undefined
    && previousState !== 'FINISHED' && previousState !== 'IDLE';
  if (justFinished) {
    let seconds = reading.printSeconds;
    if (seconds === undefined && startedAtMs) seconds = Math.round((nowMs - startedAtMs) / 1000);
    const usage = {};
    if (seconds !== undefined) usage.printSeconds = Math.max(0, Math.round(seconds));
    if (reading.filamentGrams !== undefined) usage.filamentGrams = reading.filamentGrams;
    if (Object.keys(usage).length) body.usage = usage;
  }
  return body;
}
