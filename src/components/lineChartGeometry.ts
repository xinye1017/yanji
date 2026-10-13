/**
 * Pure SVG geometry for the review trend. These are *display* coordinates,
 * derived only from existing numeric data; statistics remain owned by Room.
 *
 * Monotone cubic Hermite interpolation has two important guarantees:
 * - each original day/hour is a point ON the visible curve;
 * - control points stay within each interval's observed endpoints, so smoothing
 *   never invents an unrecorded larger peak or a negative valley.
 */
export interface ChartPoint { x: number; y: number }
export interface ChartSegment {
  from: ChartPoint;
  control1: ChartPoint;
  control2: ChartPoint;
  to: ChartPoint;
}

export function buildLineChartGeometry(
  values: ReadonlyArray<number>,
  width: number,
  height: number,
  padding = 8,
): {
  points: ChartPoint[];
  segments: ChartSegment[];
  linePath: string;
  areaPath: string;
  plotTop: number;
  plotBottom: number;
  scale: number;
} {
  const plotTop = Math.min(Math.max(0, padding), height / 4);
  const plotBottom = height - plotTop;
  const max = Math.max(0, ...values);
  const scale = max > 0 ? (plotBottom - plotTop) / max : 0;
  const step = values.length > 1 ? width / (values.length - 1) : 0;
  const points = values.map((value, index) => ({
    x: values.length > 1 ? index * step : width / 2,
    y: plotBottom - Math.max(0, value) * scale,
  }));

  if (points.length === 0) {
    return { points, segments: [], linePath: '', areaPath: '', plotTop, plotBottom, scale };
  }

  // Uniform x spacing: a harmonic mean of same-sign slopes preserves
  // monotonicity. A local maximum or minimum receives a zero derivative.
  const slopes = points.slice(1).map((point, index) =>
    step > 0 ? (point.y - points[index].y) / step : 0
  );
  const tangents = points.map((_, index) => {
    if (index === 0) return slopes[0] ?? 0;
    if (index === points.length - 1) return slopes[index - 1] ?? 0;
    const left = slopes[index - 1];
    const right = slopes[index];
    return left * right > 0 ? 2 * left * right / (left + right) : 0;
  });

  const segments: ChartSegment[] = [];
  let linePath = `M ${points[0].x} ${points[0].y}`;
  for (let index = 0; index < points.length - 1; index++) {
    const from = points[index];
    const to = points[index + 1];
    const dx = (to.x - from.x) / 3;
    const control1 = { x: from.x + dx, y: from.y + dx * tangents[index] };
    const control2 = { x: to.x - dx, y: to.y - dx * tangents[index + 1] };
    segments.push({ from, control1, control2, to });
    linePath += ` C ${control1.x} ${control1.y} ${control2.x} ${control2.y} ${to.x} ${to.y}`;
  }
  const last = points[points.length - 1];
  const first = points[0];
  const areaPath = `${linePath} L ${last.x} ${plotBottom} L ${first.x} ${plotBottom} Z`;
  return { points, segments, linePath, areaPath, plotTop, plotBottom, scale };
}
