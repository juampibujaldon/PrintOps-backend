import { test } from 'node:test';
import assert from 'node:assert/strict';
import { filamentMmToGrams, fromBambu, fromMoonraker, fromOctoPrint, mergeBambu, withUsage } from '../src/normalize.js';

test('mm de filamento a gramos (1,75 mm, PLA)', () => {
  // 1 m de PLA 1,75 mm ≈ 2,98 g
  assert.equal(filamentMmToGrams(1000), 2.98);
  assert.equal(filamentMmToGrams(0), 0);
  assert.equal(filamentMmToGrams(-5), 0);
});

test('OctoPrint imprimiendo', () => {
  const r = fromOctoPrint(
    { state: 'Printing', progress: { completion: 42.37, printTime: 1200, printTimeLeft: 1800 },
      job: { file: { name: 'gear.gcode' }, filament: { tool0: { length: 2000 } } } },
    { temperature: { tool0: { actual: 210.4 }, bed: { actual: 60 } } });
  assert.equal(r.report.state, 'PRINTING');
  assert.equal(r.report.progress, 42.4);
  assert.equal(r.report.nozzleTemp, 210.4);
  assert.equal(r.report.bedTemp, 60);
  assert.equal(r.report.remainingSeconds, 1800);
  assert.equal(r.printSeconds, 1200);
  assert.ok(r.filamentGrams > 2.5 && r.filamentGrams < 2.6); // 42% de 2 m
});

test('OctoPrint con error', () => {
  const r = fromOctoPrint({ state: 'Offline after error', progress: {} }, null);
  assert.equal(r.report.state, 'ERROR');
  assert.equal(r.report.message, 'Offline after error');
});

test('Moonraker completo', () => {
  const r = fromMoonraker({ status: {
    print_stats: { state: 'complete', print_duration: 3600, filament_used: 5000, filename: 'part.gcode' },
    extruder: { temperature: 40 }, heater_bed: { temperature: 30 }, display_status: { progress: 1 } } });
  assert.equal(r.report.state, 'FINISHED');
  assert.equal(r.report.progress, 100);
  assert.equal(r.printSeconds, 3600);
  assert.equal(r.filamentGrams, 14.91);
});

test('Moonraker estima los segundos restantes', () => {
  const r = fromMoonraker({ status: { print_stats: { state: 'printing', print_duration: 600 }, display_status: { progress: 0.25 } } });
  assert.equal(r.report.remainingSeconds, 1800);
});

test('Bambu combina reportes parciales', () => {
  let s = mergeBambu({}, { print: { gcode_state: 'RUNNING', mc_percent: 10, nozzle_temper: 220 } });
  s = mergeBambu(s, { print: { mc_percent: 55, mc_remaining_time: 30 } });
  s = mergeBambu(s, { info: { other: true } }); // mensajes sin "print" no pisan nada
  const r = fromBambu(s);
  assert.equal(r.report.state, 'PRINTING');
  assert.equal(r.report.progress, 55);
  assert.equal(r.report.nozzleTemp, 220);
  assert.equal(r.report.remainingSeconds, 1800);
});

test('usage solo en la transición a terminado', () => {
  const finished = { report: { state: 'FINISHED', progress: 100 }, printSeconds: 3600, filamentGrams: 12 };
  assert.deepEqual(withUsage('PRINTING', finished, 0, 0).usage, { printSeconds: 3600, filamentGrams: 12 });
  assert.equal(withUsage('FINISHED', finished, 0, 0).usage, undefined); // ya informado
  assert.equal(withUsage(undefined, finished, 0, 0).usage, undefined); // arranque del agente
  // Sin tiempo informado (Bambu): se mide desde el inicio.
  const bambu = { report: { state: 'FINISHED' } };
  assert.deepEqual(withUsage('PRINTING', bambu, 1000, 61000).usage, { printSeconds: 60 });
  // Campos vacíos no se mandan.
  assert.deepEqual(Object.keys(withUsage('IDLE', { report: { state: 'IDLE', progress: undefined } }, 0, 0)), ['state']);
});
