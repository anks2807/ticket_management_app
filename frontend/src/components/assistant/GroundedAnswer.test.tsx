import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { GroundedAnswer } from "./GroundedAnswer";

describe("GroundedAnswer", () => {
  it("links cited tickets to detail routes", () => {
    render(
      <MemoryRouter>
        <GroundedAnswer
          response={{
            outcome: "GROUNDED",
            answer: "Try restarting the printer.",
            sources: [
              { ticketId: "TKT-1001", title: "Printer jam", status: "OPEN" },
            ],
          }}
        />
      </MemoryRouter>,
    );
    const link = screen.getByRole("link", { name: /TKT-1001/ });
    expect(link).toHaveAttribute("href", "/tickets/TKT-1001");
    expect(screen.getByText(/Try restarting the printer/)).toBeInTheDocument();
  });
});