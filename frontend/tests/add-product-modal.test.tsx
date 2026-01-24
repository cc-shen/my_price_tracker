import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AddProductModal from "../src/components/add-product-modal";
import { ToastProvider } from "../src/components/toast-provider";
import { apiSend } from "../src/api/client";

vi.mock("../src/api/client", () => {
  class ApiError extends Error {
    type?: string;
    constructor(message?: string) {
      super(message);
      this.name = "ApiError";
    }
  }
  return {
    apiSend: vi.fn(),
    ApiError
  };
});

describe("AddProductModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("validates the URL before fetching", async () => {
    const onCreated = vi.fn();
    const onClose = vi.fn();

    render(
      <ToastProvider>
        <AddProductModal isOpen onClose={onClose} onCreated={onCreated} />
      </ToastProvider>
    );

    await userEvent.click(screen.getByRole("button", { name: "Fetch details" }));

    expect(screen.getByText("Please enter a product URL.")).toBeInTheDocument();
    expect(apiSend).not.toHaveBeenCalled();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it("fetches details then submits a product", async () => {
    const onCreated = vi.fn();
    const onClose = vi.fn();
    const previewPayload = {
      url: "https://example.com/product",
      domain: "example.com",
      title: "Test item",
      price: 19.99,
      currency: "USD"
    };
    const payload = {
      id: "123",
      title: "Test item",
      price: 19.99,
      currency: "USD",
      domain: "example.com",
      lastCheckedAt: "2024-01-01T00:00:00Z"
    };

    vi.mocked(apiSend).mockResolvedValueOnce(previewPayload).mockResolvedValueOnce(payload);

    render(
      <ToastProvider>
        <AddProductModal isOpen onClose={onClose} onCreated={onCreated} />
      </ToastProvider>
    );

    await userEvent.type(
      screen.getByLabelText("Product URL"),
      "https://example.com/product"
    );
    await userEvent.click(screen.getByRole("button", { name: "Fetch details" }));

    expect(await screen.findByLabelText("Product title")).toHaveValue("Test item");
    expect(screen.getByLabelText("Price")).toHaveValue(19.99);
    expect(screen.getByLabelText("Currency (optional)")).toHaveValue("USD");

    await userEvent.click(screen.getByRole("button", { name: "Add product" }));

    await waitFor(() => {
      expect(onCreated).toHaveBeenCalledWith({
        ...payload,
        url: "https://example.com/product"
      });
      expect(onClose).toHaveBeenCalled();
    });

    expect(apiSend).toHaveBeenNthCalledWith(
      1,
      "/api/products/preview",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ url: "https://example.com/product" })
      })
    );
    expect(apiSend).toHaveBeenNthCalledWith(
      2,
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

    await userEvent.click(screen.getByRole("button", { name: "Enter manually" }));
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
