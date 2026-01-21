import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { apiGet } from "../api/client";
import PriceChart from "../components/price-chart";

type Product = {
  id: string;
  title: string;
  domain: string;
  currency?: string;
  lastCheckedAt?: string;
};

type PricePoint = {
  t: string;
  price: number;
  currency?: string;
};

type PriceHistoryResponse = {
  productId: string;
  points: PricePoint[];
};

const rangeOptions = [
  { key: "7d", label: "7 days", days: 7 },
  { key: "30d", label: "30 days", days: 30 },
  { key: "90d", label: "90 days", days: 90 },
  { key: "365d", label: "1 year", days: 365 },
  { key: "all", label: "All time", days: null }
];

const formatDateTime = (value?: string) => {
  if (!value) {
    return "Not updated yet";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
};

const formatPrice = (value: number, currency?: string) =>
  currency ? `${currency} ${value.toFixed(2)}` : value.toFixed(2);

const buildHistoryUrl = (id: string, days: number | null) => {
  if (!days) {
    return `/api/products/${id}/prices`;
  }
  const now = new Date();
  const from = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
  const params = new URLSearchParams({
    from: from.toISOString(),
    to: now.toISOString()
  });
  return `/api/products/${id}/prices?${params.toString()}`;
};

export default function ProductDetail() {
  const { id } = useParams();
  const [product, setProduct] = useState<Product | null>(null);
  const [points, setPoints] = useState<PricePoint[]>([]);
  const [selectedRange, setSelectedRange] = useState("30d");
  const [error, setError] = useState<string | null>(null);
  const [isLoadingProduct, setIsLoadingProduct] = useState(true);
  const [isLoadingHistory, setIsLoadingHistory] = useState(true);

  useEffect(() => {
    if (!id) {
      return;
    }

    let isMounted = true;
    setIsLoadingProduct(true);
    setError(null);
    apiGet<Product>(`/api/products/${id}`)
      .then((data) => {
        if (isMounted) {
          setProduct(data);
          setIsLoadingProduct(false);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err instanceof Error ? err.message : "Failed to load product.");
          setIsLoadingProduct(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [id]);

  useEffect(() => {
    if (!id) {
      return;
    }

    const range = rangeOptions.find((option) => option.key === selectedRange);
    let isMounted = true;
    setIsLoadingHistory(true);
    setError(null);
    apiGet<PriceHistoryResponse>(buildHistoryUrl(id, range?.days ?? null))
      .then((data) => {
        if (isMounted) {
          setPoints(data.points);
          setIsLoadingHistory(false);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err instanceof Error ? err.message : "Failed to load history.");
          setIsLoadingHistory(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [id, selectedRange]);

  const stats = useMemo(() => {
    if (points.length === 0) {
      return null;
    }
    const prices = points.map((point) => point.price);
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const avg = prices.reduce((sum, price) => sum + price, 0) / prices.length;
    return { min, max, avg };
  }, [points]);

  return (
    <section className="rounded-3xl bg-white/80 p-8 shadow-[0_20px_60px_rgba(15,23,42,0.08)] backdrop-blur">
      <div className="flex flex-col gap-6">
        <div className="flex flex-col gap-3">
          <p className="text-xs uppercase tracking-[0.4em] text-slate-500">
            Product Detail
          </p>
          <h2 className="text-2xl font-semibold text-slate-900">
            {product?.title ?? (isLoadingProduct ? "Loading..." : "Unknown product")}
          </h2>
          <p className="text-sm text-slate-600">
            {product?.domain ?? "Domain unavailable"} • {formatDateTime(product?.lastCheckedAt)}
          </p>
          {error ? (
            <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
              {error}
            </div>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {rangeOptions.map((option) => (
            <button
              key={option.key}
              type="button"
              onClick={() => setSelectedRange(option.key)}
              className={`rounded-full px-4 py-2 text-xs font-semibold uppercase tracking-[0.2em] transition ${
                selectedRange === option.key
                  ? "bg-slate-900 text-white"
                  : "border border-slate-200 text-slate-600 hover:border-slate-300"
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>

        {isLoadingHistory ? (
          <div className="rounded-2xl border border-slate-200 bg-white/70 p-6 text-sm text-slate-500">
            Loading price history...
          </div>
        ) : (
          <PriceChart points={points} currency={product?.currency} />
        )}

        {stats ? (
          <div className="grid gap-3 text-sm text-slate-600 sm:grid-cols-3">
            <div className="rounded-2xl border border-slate-200 bg-white/90 p-4">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-400">Min</p>
              <p className="mt-2 text-lg font-semibold text-slate-900">
                {formatPrice(stats.min, product?.currency)}
              </p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white/90 p-4">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-400">Avg</p>
              <p className="mt-2 text-lg font-semibold text-slate-900">
                {formatPrice(stats.avg, product?.currency)}
              </p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white/90 p-4">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-400">Max</p>
              <p className="mt-2 text-lg font-semibold text-slate-900">
                {formatPrice(stats.max, product?.currency)}
              </p>
            </div>
          </div>
        ) : null}
      </div>
    </section>
  );
}
