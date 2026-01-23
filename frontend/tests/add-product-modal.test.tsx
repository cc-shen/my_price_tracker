import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import AddProductModal from "../src/components/add-product-modal";
import { ToastProvider } from "../src/components/toast-provider";
import { apiSend } from "../src/api/client";

vi.mock("../src/api/client", () => ({
  apiSend: vi.fn()
}));

describe("AddProductModal", () => {
  it("validates the URL before submitting", async () => {
    const onCreated = vi.fn();
    const onClose = vi.fn();

    render(
      <ToastProvider>
        <AddProductModal isOpen onClose={onClose} onCreated={onCreated} />
      </ToastProvider>
    );

    await userEvent.click(screen.getByRole("button", { name: "Add product" }));

    expect(screen.getByText("Please enter a product URL.")).toBeInTheDocument();
    expect(apiSend).not.toHaveBeenCalled();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it("submits a URL and reports success", async () => {
    const onCreated = vi.fn();
    const onClose = vi.fn();
    const payload = {
      id: "123",
      title: "Test item",
      price: 19.99,
      currency: "USD",
      domain: "example.com",
      lastCheckedAt: "2024-01-01T00:00:00Z"
    };

    vi.mocked(apiSend).mockResolvedValueOnce(payload);

    render(
      <ToastProvider>
        <AddProductModal isOpen onClose={onClose} onCreated={onCreated} />
      </ToastProvider>
    );

    await userEvent.type(
      screen.getByLabelText("Product URL"),
      "https://example.com/product"
    );
    await userEvent.type(screen.getByLabelText("Product title"), "Test item");
    await userEvent.type(screen.getByLabelText("Price"), "19.99");
    await userEvent.type(screen.getByLabelText("Currency (optional)"), "usd");
    await userEvent.click(screen.getByRole("button", { name: "Add product" }));

    await waitFor(() => {
      expect(onCreated).toHaveBeenCalledWith(payload);
      expect(onClose).toHaveBeenCalled();
    });

    expect(apiSend).toHaveBeenCalledWith(
      "/api/products",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          url: "https://example.com/product",
          manual: {
            title: "Test item",
            price: 19.99,
            currency: "USD"
          }
        })
      })
    );
    expect(
      screen.getByText("Product added. Tracking starts now.")
    ).toBeInTheDocument();
  });

  it("validates missing manual fields", async () => {
    const onCreated = vi.fn();
    const onClose = vi.fn();

    render(
      <ToastProvider>
        <AddProductModal isOpen onClose={onClose} onCreated={onCreated} />
      </ToastProvider>
    );

    await userEvent.type(
      screen.getByLabelText("Product URL"),
      "https://example.com/product"
    );
    await userEvent.click(screen.getByRole("button", { name: "Add product" }));

    expect(
      screen.getByText("Please enter a product title.")
    ).toBeInTheDocument();
    expect(apiSend).not.toHaveBeenCalled();
    expect(onCreated).not.toHaveBeenCalled();
  });
});
