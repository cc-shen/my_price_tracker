import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import PriceChart from "../src/components/price-chart";


describe("PriceChart", () => {
  it("renders an empty state when there is no history", () => {
    render(<PriceChart points={[]} />);

    expect(screen.getByText("No price history yet.")).toBeInTheDocument();
  });

  it("renders summary labels for provided points", () => {
    const points = [
      { t: "2024-01-01T00:00:00Z", price: 100 },
      { t: "2024-01-02T00:00:00Z", price: 120 }
    ];

    const { container } = render(
      <PriceChart points={points} currency="USD" />
    );

    expect(screen.getByText("Low: USD 100.00")).toBeInTheDocument();
    expect(screen.getByText("Last: USD 120.00")).toBeInTheDocument();
    expect(screen.getByText("High: USD 120.00")).toBeInTheDocument();
    expect(container.querySelector("svg")).toBeInTheDocument();
  });
});
