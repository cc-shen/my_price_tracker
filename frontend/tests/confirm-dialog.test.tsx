import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ConfirmDialog from "../src/components/confirm-dialog";


describe("ConfirmDialog", () => {
  it("fires confirm and cancel actions", async () => {
    const onConfirm = vi.fn();
    const onClose = vi.fn();

    render(
      <ConfirmDialog
        isOpen
        title="Delete product?"
        description="This action is permanent."
        confirmLabel="Delete product"
        onConfirm={onConfirm}
        onClose={onClose}
      />
    );

    await userEvent.click(screen.getByRole("button", { name: "Delete product" }));
    expect(onConfirm).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
