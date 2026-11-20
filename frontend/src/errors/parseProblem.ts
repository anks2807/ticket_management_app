import type { ParseProblemResult } from "./types";

type ProblemBody = {
  title?: string;
  detail?: string;
  errorCode?: string;
  currentStatus?: string;
  allowedTransitions?: string[];
  errors?: Array<{ field?: string; message?: string }>;
};

function asRecord(value: unknown): Record<string, unknown> | null {
  if (typeof value === "object" && value !== null) {
    return value as Record<string, unknown>;
  }
  return null;
}

function fieldErrorsFrom(body: ProblemBody): Record<string, string> {
  const map: Record<string, string> = {};
  for (const entry of body.errors ?? []) {
    if (entry.field && entry.message) {
      map[entry.field] = entry.message;
    }
  }
  return map;
}

function genericFallback(body: ProblemBody): ParseProblemResult {
  return {
    kind: "generic",
    title: body.title ?? "Something went wrong",
    detail: body.detail,
    errorCode: body.errorCode,
  };
}

export function parseProblemBody(
  body: unknown,
  options?: { retryAfterSeconds?: number },
): ParseProblemResult {
  const record = asRecord(body);
  if (!record) {
    return { kind: "generic", title: "Something went wrong" };
  }

  const problem = record as ProblemBody;
  const code = problem.errorCode;

  if (code === "VALIDATION_FAILED" || code === "INVALID_QUESTION") {
    const fieldErrors = fieldErrorsFrom(problem);
    if (Object.keys(fieldErrors).length > 0) {
      return {
        kind: "fieldErrors",
        fieldErrors,
        title: problem.title ?? "Validation failed",
      };
    }
    return genericFallback(problem);
  }

  if (code === "MALFORMED_REQUEST") {
    const fieldErrors = fieldErrorsFrom(problem);
    if (Object.keys(fieldErrors).length > 0) {
      return {
        kind: "fieldErrors",
        fieldErrors,
        title: problem.title ?? "Malformed request",
      };
    }
    return genericFallback(problem);
  }

  if (code === "INVALID_TRANSITION") {
    return {
      kind: "invalidTransition",
      title: problem.title ?? "Invalid transition",
      detail: problem.detail,
      currentStatus: problem.currentStatus ?? "",
      allowedTransitions: [...(problem.allowedTransitions ?? [])],
    };
  }

  if (code === "TICKET_FROZEN" || code === "RESOLUTION_NOTES_NOT_EDITABLE") {
    return {
      kind: "frozen",
      errorCode: code,
      title: problem.title ?? "Ticket state conflict",
      detail: problem.detail,
      currentStatus: problem.currentStatus,
    };
  }

  if (code === "TICKET_NOT_FOUND") {
    return {
      kind: "notFound",
      title: problem.title ?? "Ticket not found",
      detail: problem.detail,
    };
  }

  if (code === "TICKET_BUSY") {
    return {
      kind: "busy",
      title: problem.title ?? "Ticket busy",
      detail: problem.detail,
      retryAfterSeconds: options?.retryAfterSeconds,
    };
  }

  return genericFallback(problem);
}

function parseRetryAfter(header: string | null): number | undefined {
  if (header === null || header.trim() === "") {
    return undefined;
  }
  const parsed = Number.parseInt(header, 10);
  return Number.isNaN(parsed) ? undefined : parsed;
}

export async function parseProblemResponse(response: Response): Promise<ParseProblemResult> {
  const retryAfterSeconds = parseRetryAfter(response.headers.get("Retry-After"));
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    return {
      kind: "generic",
      title: "Something went wrong",
      detail: `HTTP ${response.status}`,
    };
  }
  return parseProblemBody(body, { retryAfterSeconds });
}