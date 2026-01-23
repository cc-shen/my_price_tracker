import { FormEvent, useEffect, useState } from "react";
import { apiSend } from "../api/client";
import Modal from "./modal";
import { useToast } from "./toast-provider";

type CreateProductResponse = {
  id: string;
  title: string;
  price: number;
  currency?: string;
  domain: string;
  lastCheckedAt?: string;
};

type AddProductModalProps = {
  isOpen: boolean;
  onClose: () => void;
  onCreated: (product: CreateProductResponse) => void;
};

export default function AddProductModal({
  isOpen,
  onClose,
  onCreated
}: AddProductModalProps) {
  const [url, setUrl] = useState("");
  const [manualTitle, setManualTitle] = useState("");
  const [manualPrice, setManualPrice] = useState("");
  const [manualCurrency, setManualCurrency] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { pushToast } = useToast();

  useEffect(() => {
    if (!isOpen) {
      setUrl("");
      setManualTitle("");
      setManualPrice("");
      setManualCurrency("");
      setError(null);
      setIsSubmitting(false);
    }
  }, [isOpen]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!url.trim()) {
      setError("Please enter a product URL.");
      return;
    }
    if (!manualTitle.trim()) {
      setError("Please enter a product title.");
      return;
    }
    const parsedPrice = Number.parseFloat(manualPrice);
    if (!Number.isFinite(parsedPrice) || parsedPrice <= 0) {
      setError("Please enter a valid price.");
      return;
    }
    if (manualCurrency.trim() && !/^[a-zA-Z]{3}$/.test(manualCurrency.trim())) {
      setError("Currency must be a 3-letter code (e.g., CAD).");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const requestPayload: Record<string, unknown> = {
        url: url.trim(),
        manual: {
          title: manualTitle.trim(),
          price: Number.parseFloat(manualPrice),
          currency: manualCurrency.trim()
            ? manualCurrency.trim().toUpperCase()
            : undefined
        }
      };
      const payload = await apiSend<CreateProductResponse>("/api/products", {
        method: "POST",
        body: JSON.stringify(requestPayload)
      });
      if (!payload) {
        throw new Error("No response returned.");
      }
      onCreated(payload);
      pushToast({
        message: "Product added. Tracking starts now.",
        tone: "success"
      });
      onClose();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unable to add product.";
      setError(message);
      pushToast({ message, tone: "error" });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal title="Add a product" isOpen={isOpen} onClose={onClose}>
      <form className="space-y-4" onSubmit={handleSubmit}>
        <label className="flex flex-col gap-2 text-sm font-semibold text-slate-700">
          Product URL
          <input
            type="url"
            value={url}
            onChange={(event) => setUrl(event.target.value)}
            placeholder="https://..."
            className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm outline-none transition focus:border-amber-300 focus:ring-2 focus:ring-amber-200"
          />
        </label>
        <div className="grid gap-3 md:grid-cols-2">
          <label className="flex flex-col gap-2 text-sm font-semibold text-slate-700 md:col-span-2">
            Product title
            <input
              type="text"
              value={manualTitle}
              onChange={(event) => setManualTitle(event.target.value)}
              placeholder="Blue Light Blocking Glasses"
              className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm outline-none transition focus:border-amber-300 focus:ring-2 focus:ring-amber-200"
            />
          </label>
          <label className="flex flex-col gap-2 text-sm font-semibold text-slate-700">
            Price
            <input
              type="number"
              value={manualPrice}
              onChange={(event) => setManualPrice(event.target.value)}
              placeholder="19.99"
              step="0.01"
              min="0"
              className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm outline-none transition focus:border-amber-300 focus:ring-2 focus:ring-amber-200"
            />
          </label>
          <label className="flex flex-col gap-2 text-sm font-semibold text-slate-700">
            Currency (optional)
            <input
              type="text"
              value={manualCurrency}
              onChange={(event) => setManualCurrency(event.target.value)}
              placeholder="CAD"
              maxLength={3}
              className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm uppercase text-slate-900 shadow-sm outline-none transition focus:border-amber-300 focus:ring-2 focus:ring-amber-200"
            />
          </label>
        </div>
        {error ? (
          <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
            {error}
          </div>
        ) : null}
        <div className="flex flex-wrap justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 transition hover:border-slate-300"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-full bg-amber-400 px-5 py-2 text-sm font-semibold text-slate-900 shadow-sm transition hover:bg-amber-300 disabled:cursor-not-allowed disabled:opacity-70"
          >
            {isSubmitting ? "Adding..." : "Add product"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
