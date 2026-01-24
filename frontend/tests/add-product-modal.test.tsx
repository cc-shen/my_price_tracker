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

  it("prefills fields from fetch details", async () => {
    const onCreated = vi.fn();
    const onClose = vi.fn();
    const previewPayload = {
      url: "https://example.com/product",
      domain: "example.com",
      title: "Fetched Item",
      price: 42.5,
      currency: "CAD"
    };

    vi.mocked(apiSend).mockResolvedValueOnce(previewPayload);

    render(
      <ToastProvider>
        <AddProductModal isOpen onClose={onClose} onCreated={onCreated} />
      </ToastProvider>
    );

    await userEvent.type(
      screen.getByLabelText("Product URL"),
      "https://example.com/product?utm=1"
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Fetch details" })
    );

    await waitFor(() => {
      expect(
        (screen.getByLabelText("Product title") as HTMLInputElement).value
      ).toBe("Fetched Item");
      expect(
        (screen.getByLabelText("Price") as HTMLInputElement).value
      ).toBe("42.5");
      expect(
        (screen.getByLabelText("Currency (optional)") as HTMLInputElement).value
      ).toBe("CAD");
    });

    expect(apiSend).toHaveBeenCalledWith(
      "/api/product-preview",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ url: "https://example.com/product?utm=1" })
      })
    );
  });

  it("submits a product", async () => {
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
    await userEvent.type(screen.getByLabelText("Currency (optional)"), "USD");

    await userEvent.click(screen.getByRole("button", { name: "Add product" }));

    await waitFor(() => {
      expect(onCreated).toHaveBeenCalledWith({
        ...payload,
        url: "https://example.com/product"
      });
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
