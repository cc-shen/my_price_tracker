import { useParams } from "react-router-dom";

export default function ProductDetail() {
  const { id } = useParams();

  return (
    <section className="rounded-3xl bg-white/80 p-8 shadow-[0_20px_60px_rgba(15,23,42,0.08)] backdrop-blur">
      <div className="flex flex-col gap-3">
        <p className="text-xs uppercase tracking-[0.4em] text-slate-500">
          Product Detail
        </p>
        <h2 className="text-2xl font-semibold text-slate-900">
          Product {id ?? ""}
        </h2>
        <p className="max-w-2xl text-sm text-slate-600">
          Price history and range filters will live here once the chart component
          lands.
        </p>
      </div>
      <div className="mt-8 rounded-2xl border border-dashed border-slate-200 bg-white/70 p-6 text-sm text-slate-500">
        Chart placeholder
      </div>
    </section>
  );
}
