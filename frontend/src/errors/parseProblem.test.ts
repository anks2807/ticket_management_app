import { describe, expect, it } from "vitest";
import fixtures from "./__fixtures__/problems.json";
import { parseProblemBody, parseProblemResponse } from "./parseProblem";

describe("parseProblemBody", () => {
  it("maps VALIDATION_FAILED to fieldErrors", () => {
    const result = parseProblemBody(fixtures.validationFailed);
    expect(result.kind).toBe("fieldErrors");
    if (result.kind === "fieldErrors") {
      expect(result.fieldErrors.title).toBe("Title must not be blank");
      expect(result.fieldErrors.description).toBe("Description must not be blank");
    }
  });

  it("maps MALFORMED_REQUEST with errors[] to fieldErrors", () => {
    const result = parseProblemBody(fixtures.malformedRequestWithErrors);
    expect(result.kind).toBe("fieldErrors");
    if (result.kind === "fieldErrors") {
      expect(result.fieldErrors.page).toBe("page must be a non-negative integer");
    }
  });

  it("maps INVALID_TRANSITION with currentStatus and allowedTransitions from body", () => {
    const result = parseProblemBody(fixtures.invalidTransition);
    expect(result).toEqual({
      kind: "invalidTransition",
      title: "Invalid transition",
      detail: "Cannot move to CLOSED from OPEN",
      currentStatus: "OPEN",
      allowedTransitions: ["IN_PROGRESS", "CANCELLED"],
    });
  });

  it("maps TICKET_FROZEN to frozen", () => {
    const result = parseProblemBody(fixtures.ticketFrozen);
    expect(result.kind).toBe("frozen");
    if (result.kind === "frozen") {
      expect(result.errorCode).toBe("TICKET_FROZEN");
      expect(result.currentStatus).toBe("CLOSED");
    }
  });

  it("maps RESOLUTION_NOTES_NOT_EDITABLE to frozen", () => {
    const result = parseProblemBody(fixtures.resolutionNotesNotEditable);
    expect(result.kind).toBe("frozen");
    if (result.kind === "frozen") {
      expect(result.errorCode).toBe("RESOLUTION_NOTES_NOT_EDITABLE");
    }
  });

  it("maps TICKET_NOT_FOUND to notFound", () => {
    const result = parseProblemBody(fixtures.ticketNotFound);
    expect(result.kind).toBe("notFound");
    if (result.kind === "notFound") {
      expect(result.title).toBe("Ticket not found");
      expect(result.detail).toContain("TKT-9999");
    }
  });

  it("maps TICKET_BUSY with retryAfterSeconds when provided", () => {
    const result = parseProblemBody(fixtures.ticketBusy, { retryAfterSeconds: 1 });
    expect(result.kind).toBe("busy");
    if (result.kind === "busy") {
      expect(result.retryAfterSeconds).toBe(1);
    }
  });

  it("maps unknown error codes to generic", () => {
    const result = parseProblemBody(fixtures.malformedRequestGeneric);
    expect(result.kind).toBe("generic");
    if (result.kind === "generic") {
      expect(result.title).toBe("Malformed request");
      expect(result.errorCode).toBe("MALFORMED_REQUEST");
    }
  });

  it("maps INTERNAL_ERROR to generic fallback", () => {
    const result = parseProblemBody(fixtures.internalError);
    expect(result.kind).toBe("generic");
    if (result.kind === "generic") {
      expect(result.errorCode).toBe("INTERNAL_ERROR");
      expect(result.detail).toBe("The request could not be completed");
    }
  });
});

describe("parseProblemResponse", () => {
  it("reads Retry-After header for busy responses", async () => {
    const response = new Response(JSON.stringify(fixtures.ticketBusy), {
      status: 503,
      headers: {
        "Content-Type": "application/problem+json",
        "Retry-After": "1",
      },
    });
    const result = await parseProblemResponse(response);
    expect(result.kind).toBe("busy");
    if (result.kind === "busy") {
      expect(result.retryAfterSeconds).toBe(1);
    }
  });
});