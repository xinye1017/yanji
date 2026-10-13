import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { buildLineChartGeometry, lineChartXCenters } from '../src/components/lineChartGeometry.ts';

function pointOnCubic(segment, t) {
  const a = (1 - t) ** 3;
  const b = 3 * (1 - t) ** 2 * t;
  const c = 3 * (1 - t) * t * t;
  const d = t ** 3;
  return {
    x: a * segment.from.x + b * segment.control1.x + c * segment.control2.x + d * segment.to.x,
    y: a * segment.from.y + b * segment.control1.y + c * segment.control2.y + d * segment.to.y,
  };
}

test('monthly peak lies on the curve and inside SVG boundaries, never above it', () => {
  const values = [0, 0, 0, 0, 0, 175, 0, 15, 100, 0, 0, 0, 0, 0];
  const chart = buildLineChartGeometry(values, 320, 64, 8, 24);
  const peak = chart.points[5];
  assert.equal(peak.y, 8);
  assert.ok(peak.y >= 4.5, 'peak radius plus stroke must not clip');
  assert.equal(peak.x, 24 + (320 - 48) * 5 / 13);
  assert.deepEqual(pointOnCubic(chart.segments[4], 1), peak);
  assert.deepEqual(pointOnCubic(chart.segments[5], 0), peak);
  assert.ok(chart.linePath.includes(` ${peak.x} ${peak.y}`), 'SVG path must end a segment at the peak');
});

test('curve never exceeds observed extrema and zero baseline stays within plot', () => {
  for (const values of [[0, 180, 0, 110, 0], [90, 60, 30, 0], [0, 40, 80, 80, 100], [5]]) {
    const chart = buildLineChartGeometry(values, 240, 64);
    assert.ok(chart.plotTop > 4.5 && chart.plotBottom < 64);
    for (const segment of chart.segments) {
      for (let t = 0; t <= 1; t += 0.05) {
        const { x, y } = pointOnCubic(segment, t);
        assert.ok(x >= Math.min(segment.from.x, segment.to.x) - 1e-8);
        assert.ok(x <= Math.max(segment.from.x, segment.to.x) + 1e-8);
        assert.ok(y >= Math.min(segment.from.y, segment.to.y) - 1e-8);
        assert.ok(y <= Math.max(segment.from.y, segment.to.y) + 1e-8);
      }
    }
    assert.ok(chart.areaPath.endsWith('Z'));
  }
});

test('first and last peaks in month/hour windows never clip and remain on the shared X axis', () => {
  for (const count of [14, 24]) {
    for (const peakIndex of [0, count - 1]) {
      const values = Array.from({ length: count }, (_, i) => i === peakIndex ? 175 : i % 4 === 0 ? 20 : 0);
      const original = [...values];
      const width = 320;
      const chart = buildLineChartGeometry(values, width, 64, 8, 24);
      const centers = lineChartXCenters(count, width, 24);
      const peak = chart.points[peakIndex];
      const markerOuterRadius = 3.5 + 2; // conservative: radius plus full outline
      assert.deepEqual(chart.points.map(point => point.x), centers);
      assert.equal(peak.x, centers[peakIndex]);
      assert.ok(peak.x >= markerOuterRadius, 'first marker must clear the left clip edge');
      assert.ok(width - peak.x >= markerOuterRadius, 'last marker must clear the right clip edge');
      assert.equal(chart.plotLeft, centers[0], 'average line must start at the first point');
      assert.equal(chart.plotRight, centers[count - 1], 'average line must end at the last point');
      const edgeSegment = peakIndex === 0 ? chart.segments[0] : chart.segments[count - 2];
      assert.deepEqual(pointOnCubic(edgeSegment, peakIndex === 0 ? 0 : 1), peak);
      assert.deepEqual(values, original, 'visual mapping must not modify study statistics');
    }
  }
});

test('review axis and chart average line are wired to the same inset domain', () => {
  const review = readFileSync(new URL('../src/screens/ReviewScreen.tsx', import.meta.url), 'utf8');
  const component = readFileSync(new URL('../src/components/YanjiUI.tsx', import.meta.url), 'utf8');
  assert.match(review, /lineChartXCenters\(24, chartWidth, YanjiSpacing\.xl\)/);
  assert.match(review, /lineChartXCenters\(columns\.length, chartWidth, YanjiSpacing\.xl\)/);
  assert.match(component, /buildLineChartGeometry\(values, width, height, YanjiSpacing\.sm, YanjiSpacing\.xl\)/);
  assert.match(component, /x1=\{chart\.plotLeft\}/);
  assert.match(component, /x2=\{chart\.plotRight\}/);
});

test('chart tolerates unmeasured and single-point layouts without altering input', () => {
  const values = [0, 45, 0];
  const before = [...values];
  const zeroWidth = buildLineChartGeometry(values, 0, 64);
  assert.equal(zeroWidth.points.length, 3);
  assert.deepEqual(values, before);
  assert.equal(buildLineChartGeometry([], 240, 64).linePath, '');
  assert.equal(buildLineChartGeometry([45], 240, 64).points[0].x, 120);
});
