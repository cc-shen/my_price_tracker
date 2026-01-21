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

export default function PriceChart({ points, currency }: PriceChartProps) {
  const { path, area, minLabel, maxLabel, lastLabel, singlePoint } = useMemo(() => {
    if (points.length === 0) {
      return {
        path: "",
        area: "",
        minLabel: "",
        maxLabel: "",
        lastLabel: "",
        singlePoint: null
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
    const width = 600;
    const height = 240;

    const mapX = (time: number) => {
      if (maxTime === minTime) {
        return width / 2;
      }
      return ((time - minTime) / (maxTime - minTime)) * width;
    };

    const mapY = (price: number) => {
      if (chartMax === chartMin) {
        return height / 2;
      }
      return height - ((price - chartMin) / (chartMax - chartMin)) * height;
    };

    const pointsPath = sorted
      .map((point) => `${mapX(new Date(point.t).getTime())},${mapY(point.price)}`)
      .join(" ");

    const linePath = `M ${pointsPath.replace(/ /g, " L ")}`;
    const areaPath = `${linePath} L ${width},${height} L 0,${height} Z`;

    const lastPoint = sorted[sorted.length - 1];
    const singlePoint =
      sorted.length === 1
        ? {
            x: mapX(new Date(lastPoint.t).getTime()),
            y: mapY(lastPoint.price)
          }
        : null;

    return {
      path: linePath,
      area: areaPath,
      minLabel: formatPrice(minPrice, currency),
      maxLabel: formatPrice(maxPrice, currency),
      lastLabel: formatPrice(prices[prices.length - 1], currency),
      singlePoint
    };
  }, [points, currency]);

  if (points.length === 0) {
    return (
      <div className="flex h-56 items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-white/70 text-sm text-slate-500">
        No price history yet.
      </div>
    );
  }

  return (
    <div className="rounded-2xl border border-slate-200 bg-white/90 p-5">
      <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500">
        <span>Low: {minLabel}</span>
        <span>Last: {lastLabel}</span>
        <span>High: {maxLabel}</span>
      </div>
      <svg
        className="mt-4 h-56 w-full"
        viewBox="0 0 600 240"
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
      </svg>
    </div>
  );
}
