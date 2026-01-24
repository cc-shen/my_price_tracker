import { FormEvent, useEffect, useState } from "react";
import { apiSend } from "../api/client";
import Modal from "./modal";
import { useToast } from "./toast-provider";

type RefreshPriceResponse = {
  id: string;
  price: number;
  currency?: string;
  lastCheckedAt?: string;
};

type RefreshPriceModalProps = {
  isOpen: boolean;
  onClose: () => void;
  initialPrice?: number | null;
  product?: {
    id: string;
    title: string;
    currency?: string;
  } | null;
  onRefreshed: (payload: RefreshPriceResponse) => void;
};

export default function RefreshPriceModal({
  isOpen,
  onClose,
  initialPrice,
  product,
  onRefreshed
}: RefreshPriceModalProps) {
  const [price, setPrice] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { pushToast } = useToast();

  useEffect(() => {
    if (!isOpen) {
      setPrice("");
      setError(null);
      setIsSubmitting(false);
      return;
    }
    if (initialPrice === null || initialPrice === undefined) {
      setPrice("");
    } else if (Number.isFinite(initialPrice)) {
      setPrice(initialPrice.toString());
    } else {
      setPrice("");
    }
    setError(null);
    setIsSubmitting(false);
  }, [isOpen, product?.currency, product?.id, initialPrice]);

  const hasPrefill = initialPrice !== null && initialPrice !== undefined;
  const submitLabel = hasPrefill ? "Confirm price" : "Update price";
  const submittingLabel = hasPrefill ? "Confirming..." : "Updating...";

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!product?.id) {
      setError("Select a product to update.");
      return;
    }
    const parsedPrice = Number.parseFloat(price);
    if (!Number.isFinite(parsedPrice) || parsedPrice <= 0) {
      setError("Please enter a valid price.");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const payload = await apiSend<RefreshPriceResponse>(
        `/api/products/${product.id}/refresh`,
        {
          method: "POST",
          body: JSON.stringify({
            price: parsedPrice
          })
        }
      );
      onRefreshed(payload);
      pushToast({
        message: `Updated ${product.title} price for today.`,
        tone: "success"
      });
      onClose();
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Unable to update price.";
      setError(message);
      pushToast({ message, tone: "error" });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      title={product ? `Update price · ${product.title}` : "Update price"}
      isOpen={isOpen}
      onClose={onClose}
    >
      <form className="space-y-4" onSubmit={handleSubmit}>
        <label className="flex flex-col gap-2 text-sm font-semibold text-slate-700">
          Price
          <input
            type="number"
            value={price}
            onChange={(event) => setPrice(event.target.value)}
            placeholder="129.99"
            step="0.01"
            min="0"
            className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-900 shadow-sm outline-none transition focus:border-amber-300 focus:ring-2 focus:ring-amber-200"
          />
        </label>
        <label className="flex flex-col gap-2 text-sm font-semibold text-slate-700">
          Currency (locked)
          <input
            type="text"
            value={product?.currency ?? ""}
            readOnly
            placeholder="N/A"
            maxLength={3}
            className="w-full cursor-default rounded-2xl border border-slate-200 bg-slate-100 px-4 py-3 text-sm uppercase text-slate-500 shadow-sm outline-none"
          />
        </label>
        {hasPrefill ? (
          <p className="text-xs text-slate-600">
            Fetched price pre-filled. Confirm to save.
          </p>
        ) : null}
        <p className="text-xs text-slate-600">
          One update per day. Submitting again today will overwrite today&apos;s
          price.
        </p>
        {error ? (
          <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
            {error}
          </div>
        ) : null}
        <div className="flex flex-wrap justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 transition hover:border-slate-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-300 focus-visible:ring-offset-2 focus-visible:ring-offset-white"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-full bg-amber-400 px-5 py-2 text-sm font-semibold text-slate-900 shadow-sm transition hover:bg-amber-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-300 focus-visible:ring-offset-2 focus-visible:ring-offset-white disabled:cursor-not-allowed disabled:opacity-70"
          >
            {isSubmitting ? submittingLabel : submitLabel}
          </button>
        </div>
      </form>
    </Modal>
  );
}
