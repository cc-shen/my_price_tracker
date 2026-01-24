import { useMemo } from "react";

type PricePoint = {
  t: string;
  price: number;
};

type PriceChartProps = {
  points: PricePoint[];
  currency?: string;
};

const formatPrice = (value: number, currency?: string) =>
  currency ? `${currency} ${value.toFixed(2)}` : value.toFixed(2);

const CHART_WIDTH = 600;
const CHART_HEIGHT = 200;
const AXIS_HEIGHT = 36;
const VIEW_HEIGHT = CHART_HEIGHT + AXIS_HEIGHT;
const AXIS_Y = CHART_HEIGHT;
const TICK_Y = CHART_HEIGHT + 6;
const LABEL_Y = CHART_HEIGHT + 26;

export default function PriceChart({ points, currency }: PriceChartProps) {
  const { path, area, minLabel, maxLabel, lastLabel, singlePoint, xTicks } = useMemo(() => {
    if (points.length === 0) {
      return {
        path: "",
        area: "",
        minLabel: "",
        maxLabel: "",
        lastLabel: "",
        singlePoint: null,
        xTicks: [] as { x: number; label: string }[]
      };
    }

    const sorted = [...points].sort((a, b) =>
      new Date(a.t).getTime() - new Date(b.t).getTime()
    );
    const prices = sorted.map((point) => point.price);
    const times = sorted.map((point) => new Date(point.t).getTime());
    const minPrice = Math.min(...prices);
    const maxPrice = Math.max(...prices);
    const minTime = Math.min(...times);
    const maxTime = Math.max(...times);
    const spread = maxPrice - minPrice;
    const padding = spread === 0 ? Math.max(1, minPrice * 0.05) : spread * 0.2;
    const chartMin = minPrice - padding;
    const chartMax = maxPrice + padding;
    const width = CHART_WIDTH;
    const chartHeight = CHART_HEIGHT;

    const mapX = (time: number) => {
      if (maxTime === minTime) {
        return width / 2;
      }
      return ((time - minTime) / (maxTime - minTime)) * width;
    };

    const mapY = (price: number) => {
      if (chartMax === chartMin) {
        return chartHeight / 2;
      }
      return (
        chartHeight - ((price - chartMin) / (chartMax - chartMin)) * chartHeight
      );
    };

    const pointsPath = sorted
      .map((point) => `${mapX(new Date(point.t).getTime())},${mapY(point.price)}`)
      .join(" ");

    const linePath = `M ${pointsPath.replace(/ /g, " L ")}`;
    const areaPath = `${linePath} L ${width},${chartHeight} L 0,${chartHeight} Z`;

    const lastPoint = sorted[sorted.length - 1];
    const singlePoint =
      sorted.length === 1
        ? {
            x: mapX(new Date(lastPoint.t).getTime()),
            y: mapY(lastPoint.price)
          }
        : null;

    const includeYear =
      new Date(minTime).getFullYear() !== new Date(maxTime).getFullYear() ||
      maxTime - minTime > 1000 * 60 * 60 * 24 * 320;

    const formatDate = (value: number) =>
      new Date(value).toLocaleDateString("en-US", {
        timeZone: "America/New_York",
        month: "short",
        day: "numeric",
        ...(includeYear ? { year: "numeric" } : {})
      });

    const tickTimes =
      minTime === maxTime
        ? [minTime]
        : [minTime, minTime + (maxTime - minTime) / 2, maxTime];

    const xTicks = tickTimes.map((time) => ({
      x: mapX(time),
      label: formatDate(time)
    }));

    return {
      path: linePath,
      area: areaPath,
      minLabel: formatPrice(minPrice, currency),
      maxLabel: formatPrice(maxPrice, currency),
      lastLabel: formatPrice(prices[prices.length - 1], currency),
      singlePoint,
      xTicks
    };
  }, [points, currency]);

  if (points.length === 0) {
    return (
      <div className="flex h-56 items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-white/70 text-sm text-slate-600">
        No price history yet.
      </div>
    );
  }

  return (
    <div className="rounded-2xl border border-slate-200 bg-white/90 p-5">
      <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-slate-600">
        <span>Low: {minLabel}</span>
        <span>Last: {lastLabel}</span>
        <span>High: {maxLabel}</span>
      </div>
      <svg
        className="mt-4 h-64 w-full"
        viewBox={`0 0 ${CHART_WIDTH} ${VIEW_HEIGHT}`}
        preserveAspectRatio="none"
      >
        <defs>
          <linearGradient id="priceFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#fbbf24" stopOpacity="0.35" />
            <stop offset="100%" stopColor="#f8fafc" stopOpacity="0.1" />
          </linearGradient>
        </defs>
        <path d={area} fill="url(#priceFill)" />
        <path d={path} fill="none" stroke="#0f172a" strokeWidth="2" />
        {singlePoint ? (
          <circle cx={singlePoint.x} cy={singlePoint.y} r="6" fill="#0f172a" />
        ) : null}
        <line
          x1="0"
          y1={AXIS_Y}
          x2={CHART_WIDTH}
          y2={AXIS_Y}
          stroke="#e2e8f0"
          strokeWidth="2"
        />
        {xTicks.map((tick) => (
          <g key={`${tick.x}-${tick.label}`}>
            <line
              x1={tick.x}
              y1={AXIS_Y}
              x2={tick.x}
              y2={TICK_Y}
              stroke="#cbd5e1"
              strokeWidth="2"
            />
            <text
              x={tick.x}
              y={LABEL_Y}
              textAnchor="middle"
              fontSize="11"
              fill="#475569"
            >
              {tick.label}
            </text>
          </g>
        ))}
      </svg>
    </div>
  );
}
