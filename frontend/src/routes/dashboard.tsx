import { useEffect, useState } from "react";
import { apiGet } from "../api/client";

type ProductSummary = {
  id: string;
  title: string;
  domain: string;
  currentPrice: number;
  currency?: string;
  lastCheckedAt?: string;
};

export default function Dashboard() {
  const [items, setItems] = useState<ProductSummary[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    apiGet<ProductSummary[]>("/api/products")
      .then((data) => {
        if (isMounted) {
          setItems(data);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err instanceof Error ? err.message : "Failed to load products");
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <section className="rounded-3xl bg-white/80 p-8 shadow-[0_20px_60px_rgba(15,23,42,0.08)] backdrop-blur">
      <div className="flex flex-col gap-3">
        <h2 className="text-2xl font-semibold text-slate-900">Dashboard</h2>
        <p className="max-w-2xl text-sm text-slate-600">
          Add a product URL to begin tracking. This placeholder view will be wired
          to the live product list in the next milestone.
        </p>
      </div>

      <div className="mt-8 grid gap-4">
        {error ? (
          <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
            {error}
          </div>
        ) : null}

        {items.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-200 bg-white/70 p-6 text-sm text-slate-500">
            No products tracked yet.
          </div>
        ) : (
          items.map((item) => (
            <article
              key={item.id}
              className="flex flex-col gap-2 rounded-2xl border border-slate-200 bg-white/90 p-5"
            >
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-base font-semibold text-slate-900">
                    {item.title}
                  </p>
                  <p className="text-xs uppercase tracking-[0.2em] text-slate-400">
                    {item.domain}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-lg font-semibold text-slate-900">
                    {item.currency ? `${item.currency} ` : ""}
                    {item.currentPrice.toFixed(2)}
                  </p>
                  <p className="text-xs text-slate-500">
                    {item.lastCheckedAt ? `Updated ${item.lastCheckedAt}` : "Not yet updated"}
                  </p>
                </div>
              </div>
            </article>
          ))
        )}
      </div>
    </section>
  );
}
