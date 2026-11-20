import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CitationFailure } from "./CitationFailure";

describe("CitationFailure", () => {
  it("shows citation verification failure messaging", () => {
    render(<CitationFailure />);
    expect(screen.getByRole("alert")).toHaveAttribute(
      "data-outcome",
      "CITATION_VERIFICATION_FAILED",
    );
    expect(screen.getByText(/Citation verification failed/)).toBeInTheDocument();
  });
});