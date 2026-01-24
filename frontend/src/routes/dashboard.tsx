import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiGet, apiSend } from "../api/client";
import AddProductModal from "../components/add-product-modal";
import ConfirmDialog from "../components/confirm-dialog";
import RefreshPriceModal from "../components/refresh-price-modal";
import { useToast } from "../components/toast-provider";

type ProductSummary = {
  id: string;
  url?: string;
  title: string;
  domain: string;
  currentPrice: number;
  currency?: string;
  lastCheckedAt?: string;
};

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

export default function Dashboard() {
  const [items, setItems] = useState<ProductSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<ProductSummary | null>(null);
  const [pendingRefresh, setPendingRefresh] = useState<ProductSummary | null>(null);
  const { pushToast } = useToast();

  useEffect(() => {
    let isMounted = true;

    apiGet<ProductSummary[]>("/api/products")
      .then((data) => {
        if (isMounted) {
          setItems(data);
          setIsLoading(false);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err instanceof Error ? err.message : "Failed to load products");
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const handleCreated = (product: {
    id: string;
    url?: string;
    title: string;
    price: number;
    currency?: string;
    domain: string;
    lastCheckedAt?: string;
  }) => {
    setItems((prev) => [
      {
        id: product.id,
        url: product.url,
        title: product.title,
        domain: product.domain,
        currentPrice: product.price,
        currency: product.currency,
        lastCheckedAt: product.lastCheckedAt
      },
      ...prev
    ]);
  };

  const handleDelete = async () => {
    if (!pendingDelete) {
      return;
    }

    try {
      await apiSend<void>(`/api/products/${pendingDelete.id}`, { method: "DELETE" });
      setItems((prev) => prev.filter((item) => item.id !== pendingDelete.id));
      pushToast({ message: "Product removed from tracking.", tone: "success" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unable to delete product.";
      pushToast({ message, tone: "error" });
    } finally {
      setPendingDelete(null);
    }
  };

  const handleRefreshed = (payload: {
    id: string;
    price: number;
    currency?: string;
    lastCheckedAt?: string;
  }) => {
    setItems((prev) =>
      prev.map((item) =>
        item.id === payload.id
          ? {
              ...item,
              currentPrice: payload.price,
              currency: payload.currency ?? item.currency,
              lastCheckedAt: payload.lastCheckedAt ?? item.lastCheckedAt
            }
          : item
      )
    );
  };

  return (
    <section className="rounded-3xl bg-white/80 p-8 shadow-[0_20px_60px_rgba(15,23,42,0.08)] backdrop-blur">
      <div className="flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
        <div className="flex flex-col gap-3">
          <h2 className="text-2xl font-semibold text-slate-900">Dashboard</h2>
          <p className="max-w-2xl text-sm text-slate-600">
            Add a product URL to begin tracking. Each entry keeps the latest price
            plus a full history.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setIsAddOpen(true)}
          className="inline-flex items-center justify-center rounded-full bg-slate-900 px-5 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-300 focus-visible:ring-offset-2 focus-visible:ring-offset-white"
        >
          Add product
        </button>
      </div>

      <div className="mt-8 grid gap-4">
        {error ? (
          <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
            {error}
          </div>
        ) : null}

        {isLoading ? (
          <div className="rounded-2xl border border-slate-200 bg-white/70 p-6 text-sm text-slate-600">
            Loading products...
          </div>
        ) : items.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-200 bg-white/70 p-6 text-sm text-slate-600">
            No products tracked yet.
          </div>
        ) : (
          items.map((item) => (
            <article
              key={item.id}
              className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white/90 p-5"
            >
              <div className="flex items-start justify-between gap-4">
                <div>
                  {item.url ? (
                    <a
                      href={item.url}
                      target="_blank"
                      rel="noreferrer"
                      className="text-base font-semibold text-slate-900 transition hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-300 focus-visible:ring-offset-2 focus-visible:ring-offset-white"
                    >
                      {item.title}
                    </a>
                  ) : (
                    <p className="text-base font-semibold text-slate-900">
                      {item.title}
                    </p>
                  )}
                  <p className="text-xs uppercase tracking-[0.2em] text-slate-600">
                    {item.domain}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-lg font-semibold text-slate-900">
                    {item.currency ? `${item.currency} ` : ""}
                    {item.currentPrice.toFixed(2)}
                  </p>
                  <p className="text-xs text-slate-600">
                    {item.lastCheckedAt
                      ? `Updated ${formatDateTime(item.lastCheckedAt)}`
                      : "Not yet updated"}
                  </p>
                </div>
              </div>
              <div className="flex flex-wrap items-center justify-between gap-3 text-xs">
                <span className="rounded-full bg-amber-100 px-3 py-1 font-semibold uppercase tracking-[0.2em] text-amber-900">
                  Tracking
                </span>
                <div className="flex flex-wrap items-center gap-2">
                  <Link
                    to={`/products/${item.id}`}
                    className="rounded-full border border-slate-200 px-3 py-1 font-semibold uppercase tracking-[0.2em] text-slate-700 transition hover:border-slate-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-300 focus-visible:ring-offset-2 focus-visible:ring-offset-white"
                  >
                    View history
                  </Link>
                  <button
                    type="button"
                    onClick={() => setPendingRefresh(item)}
                    className="rounded-full border border-amber-200 px-3 py-1 font-semibold uppercase tracking-[0.2em] text-amber-700 transition hover:border-amber-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-300 focus-visible:ring-offset-2 focus-visible:ring-offset-white"
                  >
                    Update price
                  </button>
                  <button
                    type="button"
                    onClick={() => setPendingDelete(item)}
                    className="rounded-full border border-rose-200 px-3 py-1 font-semibold uppercase tracking-[0.2em] text-rose-700 transition hover:border-rose-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 focus-visible:ring-offset-2 focus-visible:ring-offset-white"
                  >
                    Delete
                  </button>
                </div>
              </div>
            </article>
          ))
        )}
      </div>
      <AddProductModal
        isOpen={isAddOpen}
        onClose={() => setIsAddOpen(false)}
        onCreated={handleCreated}
      />
      <ConfirmDialog
        isOpen={Boolean(pendingDelete)}
        title="Delete product?"
        description="Are you sure? This will permanently delete the product and its price history."
        confirmLabel="Delete product"
        onConfirm={handleDelete}
        onClose={() => setPendingDelete(null)}
      />
      <RefreshPriceModal
        isOpen={Boolean(pendingRefresh)}
        product={pendingRefresh}
        onRefreshed={handleRefreshed}
        onClose={() => setPendingRefresh(null)}
      />
    </section>
  );
}
