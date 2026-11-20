import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ParseProblemResult } from "../errors/types";
import { ProblemAlert } from "./ProblemAlert";

describe("ProblemAlert", () => {
  it("renders invalidTransition with allowed moves from problem body", () => {
    const problem: ParseProblemResult = {
      kind: "invalidTransition",
      title: "Invalid transition",
      detail: "Cannot move",
      currentStatus: "OPEN",
      allowedTransitions: ["IN_PROGRESS", "CANCELLED"],
    };
    render(<ProblemAlert problem={problem} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Invalid transition");
    expect(screen.getByText(/OPEN/)).toBeInTheDocument();
    expect(screen.getByText(/IN_PROGRESS, CANCELLED/)).toBeInTheDocument();
  });

  it("renders frozen", () => {
    const problem: ParseProblemResult = {
      kind: "frozen",
      errorCode: "TICKET_FROZEN",
      title: "Ticket frozen",
      detail: "Cannot edit",
    };
    render(<ProblemAlert problem={problem} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Ticket frozen");
    expect(screen.getByText(/Cannot edit/)).toBeInTheDocument();
  });

  it("renders busy with retry-after hint", () => {
    const problem: ParseProblemResult = {
      kind: "busy",
      title: "Ticket busy",
      detail: "Try later",
      retryAfterSeconds: 2,
    };
    render(<ProblemAlert problem={problem} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Ticket busy");
    expect(screen.getByText(/Retry after 2 second/)).toBeInTheDocument();
  });

  it("renders generic fallback", () => {
    const problem: ParseProblemResult = {
      kind: "generic",
      title: "Internal error",
      detail: "The request could not be completed",
    };
    render(<ProblemAlert problem={problem} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Internal error");
    expect(screen.getByText(/could not be completed/)).toBeInTheDocument();
  });
});