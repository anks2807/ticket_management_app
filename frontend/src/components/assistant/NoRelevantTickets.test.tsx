import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { NoRelevantTickets } from "./NoRelevantTickets";

describe("NoRelevantTickets", () => {
  it("shows dedicated no-match messaging", () => {
    render(<NoRelevantTickets />);
    expect(screen.getByRole("status")).toHaveAttribute(
      "data-outcome",
      "NO_RELEVANT_TICKETS",
    );
    expect(screen.getByText(/No relevant tickets/)).toBeInTheDocument();
  });
});