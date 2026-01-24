import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { ApiError, apiGet, apiSend } from "../api/client";
import PriceChart from "../components/price-chart";
import RefreshPriceModal from "../components/refresh-price-modal";
import { useToast } from "../components/toast-provider";

type Product = {
  id: string;
  url?: string;
  canonicalUrl?: string;
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

type FetchPriceResponse = {
  id: string;
  price: number;
  currency?: string;
  lastCheckedAt?: string;
  parserVersion?: string;
  source?: string;
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
    return "N/A";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString("en-US", { timeZone: "America/New_York" });
};

const formatPrice = (value: number, currency?: string) =>
  currency ? `${currency} ${value.toFixed(2)}` : value.toFixed(2);

const upsertPoint = (current: PricePoint[], next?: PricePoint) => {
  if (!next?.t) {
    return current;
  }
  const nextDate = next.t.slice(0, 10);
  const filtered = current.filter((point) => point.t.slice(0, 10) !== nextDate);
  return [...filtered, next].sort(
    (a, b) => new Date(a.t).getTime() - new Date(b.t).getTime()
  );
};

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
  const [isRefreshOpen, setIsRefreshOpen] = useState(false);
  const [isFetching, setIsFetching] = useState(false);
  const [fetchBlocked, setFetchBlocked] = useState(false);
  const { pushToast } = useToast();

  useEffect(() => {
    setFetchBlocked(false);
    setIsFetching(false);
  }, [id]);

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

  const productLink = product?.canonicalUrl ?? product?.url ?? null;

  const handleRefreshed = (payload: {
    id: string;
    price: number;
    currency?: string;
    lastCheckedAt?: string;
  }) => {
    setProduct((prev) =>
      prev
        ? {
            ...prev,
            currency: payload.currency ?? prev.currency,
            lastCheckedAt: payload.lastCheckedAt ?? prev.lastCheckedAt
          }
        : prev
    );
    if (payload.lastCheckedAt) {
      setPoints((prev) =>
        upsertPoint(prev, {
          t: payload.lastCheckedAt,
          price: payload.price,
          currency: payload.currency
        })
      );
    }
  };

  const handleFetch = async () => {
    if (!id || !product || isFetching || fetchBlocked) {
      return;
    }

    setIsFetching(true);
    setError(null);

    try {
      const payload = await apiSend<FetchPriceResponse>(
        `/api/products/${id}/fetch`,
        {
          method: "POST"
        }
      );
      setProduct((prev) =>
        prev
          ? {
              ...prev,
              currency: payload.currency ?? prev.currency,
              lastCheckedAt: payload.lastCheckedAt ?? prev.lastCheckedAt
            }
          : prev
      );
      if (payload.lastCheckedAt) {
        setPoints((prev) =>
          upsertPoint(prev, {
            t: payload.lastCheckedAt,
            price: payload.price,
            currency: payload.currency
          })
        );
      }
      pushToast({ message: "Fetched latest price.", tone: "success" });
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Unable to fetch price.";
      setError(message);
      if (err instanceof ApiError) {
        if (err.type === "parse_failed" || err.type === "domain_not_supported") {
          setIsRefreshOpen(true);
        }
        if (err.type === "fetch_rejected") {
          setFetchBlocked(true);
        }
      }
      pushToast({ message, tone: "error" });
    } finally {
      setIsFetching(false);
    }
  };

  return (
    <section className="rounded-3xl bg-white/80 p-8 shadow-[0_20px_60px_rgba(15,23,42,0.08)] backdrop-blur">
      <div className="flex flex-col gap-6">
        <div className="flex flex-col gap-3">
          <p className="text-xs uppercase tracking-[0.4em] text-slate-600">
            Product Detail
          </p>
          <h2 className="text-2xl font-semibold text-slate-900">
            {productLink ? (
              <a
                href={productLink}
                target="_blank"
                rel="noreferrer"
                className="transition hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-300 focus-visible:ring-offset-2 focus-visible:ring-offset-white"
              >
                {product?.title ??
                  (isLoadingProduct ? "Loading..." : "Unknown product")}
              </a>
            ) : (
              product?.title ?? (isLoadingProduct ? "Loading..." : "Unknown product")
            )}
          </h2>
          <p className="text-sm text-slate-600">
            {product?.domain ?? "Domain unavailable"} • Last checked at{" "}
            {formatDateTime(product?.lastCheckedAt)}
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={handleFetch}
              disabled={!product || isFetching || fetchBlocked}
              className="rounded-full border border-slate-200 px-4 py-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-700 transition hover:border-slate-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-300 focus-visible:ring-offset-2 focus-visible:ring-offset-white disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isFetching ? "Fetching..." : "Fetch"}
            </button>
            <button
              type="button"
              onClick={() => setIsRefreshOpen(true)}
              disabled={!product}
              className="rounded-full border border-amber-200 px-4 py-2 text-xs font-semibold uppercase tracking-[0.2em] text-amber-700 transition hover:border-amber-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-300 focus-visible:ring-offset-2 focus-visible:ring-offset-white disabled:cursor-not-allowed disabled:opacity-60"
            >
              Update price
            </button>
          </div>
          {fetchBlocked ? (
            <p className="text-xs text-slate-500">
              Fetching is disabled because the retailer rejected the request.
              Enter the price manually instead.
            </p>
          ) : null}
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
              className={`rounded-full px-4 py-2 text-xs font-semibold uppercase tracking-[0.2em] transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-300 focus-visible:ring-offset-2 focus-visible:ring-offset-white ${selectedRange === option.key
                ? "bg-slate-900 text-white"
                : "border border-slate-200 text-slate-700 hover:border-slate-300"
                }`}
            >
              {option.label}
            </button>
          ))}
        </div>

        {isLoadingHistory ? (
          <div className="rounded-2xl border border-slate-200 bg-white/70 p-6 text-sm text-slate-600">
            Loading price history...
          </div>
        ) : (
          <PriceChart points={points} currency={product?.currency} />
        )}

        {stats ? (
          <div className="grid gap-3 text-sm text-slate-600 sm:grid-cols-3">
            <div className="rounded-2xl border border-slate-200 bg-white/90 p-4">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-600">Min</p>
              <p className="mt-2 text-lg font-semibold text-slate-900">
                {formatPrice(stats.min, product?.currency)}
              </p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white/90 p-4">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-600">Avg</p>
              <p className="mt-2 text-lg font-semibold text-slate-900">
                {formatPrice(stats.avg, product?.currency)}
              </p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white/90 p-4">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-600">Max</p>
              <p className="mt-2 text-lg font-semibold text-slate-900">
                {formatPrice(stats.max, product?.currency)}
              </p>
            </div>
          </div>
        ) : null}
      </div>
      <RefreshPriceModal
        isOpen={isRefreshOpen}
        product={product}
        onRefreshed={handleRefreshed}
        onClose={() => setIsRefreshOpen(false)}
      />
    </section>
  );
}
