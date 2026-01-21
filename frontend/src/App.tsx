import { BrowserRouter, NavLink, Route, Routes } from "react-router-dom";
import Dashboard from "./routes/dashboard";
import ProductDetail from "./routes/product-detail";

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  [
    "rounded-full px-4 py-2 text-sm font-semibold tracking-wide",
    isActive
      ? "bg-amber-400 text-slate-900 shadow-sm"
      : "text-slate-700 hover:bg-amber-100"
  ].join(" ");

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-app bg-cover bg-fixed text-slate-900">
        <div className="mx-auto flex min-h-screen max-w-6xl flex-col px-6 pb-16 pt-10">
          <header className="flex flex-col gap-6">
            <div className="flex flex-col gap-2">
              <p className="text-xs uppercase tracking-[0.4em] text-slate-600">
                Local Price Tracker
              </p>
              <h1 className="text-3xl font-semibold text-slate-900 sm:text-4xl">
                Track price shifts without the noise.
              </h1>
            </div>
            <nav className="flex flex-wrap gap-3">
              <NavLink to="/" className={navLinkClass} end>
                Dashboard
              </NavLink>
              <NavLink to="/products/preview" className={navLinkClass}>
                Product Detail
              </NavLink>
            </nav>
          </header>
          <main className="mt-10 flex-1">
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/products/:id" element={<ProductDetail />} />
            </Routes>
          </main>
          <footer className="mt-16 text-xs uppercase tracking-[0.3em] text-slate-500">
            Local-only build. Data stays on your machine.
          </footer>
        </div>
      </div>
    </BrowserRouter>
  );
}
