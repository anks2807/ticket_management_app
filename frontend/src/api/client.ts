import { parseProblemResponse } from "../errors/parseProblem";
import type { ParseProblemResult } from "../errors/types";
import type { components as components001 } from "./schema-001";
import type { components as components002 } from "./schema-002";

export type Ticket = components001["schemas"]["Ticket"];
export type TicketPage = components001["schemas"]["TicketPage"];
export type CreateTicketRequest = components001["schemas"]["CreateTicketRequest"];
export type EditTicketRequest = components001["schemas"]["EditTicketRequest"];
export type TransitionRequest = components001["schemas"]["TransitionRequest"];
export type AddCommentRequest = components001["schemas"]["AddCommentRequest"];
export type Comment = components001["schemas"]["Comment"];
export type AskRequest = components002["schemas"]["AskRequest"];
export type AskResponse = components002["schemas"]["AskResponse"];

export type ApiError = {
  problem: ParseProblemResult;
};

export type ListTicketsParams = {
  q?: string;
  status?: string;
  page?: number;
};

function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === "object" &&
    value !== null &&
    "problem" in value &&
    typeof (value as ApiError).problem === "object"
  );
}

export function toApiError(error: unknown): ApiError {
  if (isApiError(error)) {
    return error;
  }
  return {
    problem: {
      kind: "generic",
      title: "Something went wrong",
      detail: error instanceof Error ? error.message : undefined,
    },
  };
}

export async function listTickets(params: ListTicketsParams): Promise<TicketPage> {
  const search = new URLSearchParams();
  const trimmedQ = params.q?.trim();
  if (trimmedQ) {
    search.set("q", trimmedQ);
  }
  if (params.status) {
    search.set("status", params.status);
  }
  if (params.page !== undefined) {
    search.set("page", String(params.page));
  }
  const query = search.toString();
  const response = await fetch(`/api/tickets${query ? `?${query}` : ""}`);
  if (!response.ok) {
    throw { problem: await parseProblemResponse(response) } satisfies ApiError;
  }
  return (await response.json()) as TicketPage;
}

export async function createTicket(body: CreateTicketRequest): Promise<Ticket> {
  const response = await fetch("/api/tickets", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw { problem: await parseProblemResponse(response) } satisfies ApiError;
  }
  return (await response.json()) as Ticket;
}

export async function getTicket(ticketId: string): Promise<Ticket> {
  const response = await fetch(`/api/tickets/${encodeURIComponent(ticketId)}`);
  if (!response.ok) {
    throw { problem: await parseProblemResponse(response) } satisfies ApiError;
  }
  return (await response.json()) as Ticket;
}

export async function editTicket(ticketId: string, body: EditTicketRequest): Promise<Ticket> {
  const response = await fetch(`/api/tickets/${encodeURIComponent(ticketId)}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/merge-patch+json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw { problem: await parseProblemResponse(response) } satisfies ApiError;
  }
  return (await response.json()) as Ticket;
}

export async function addComment(
  ticketId: string,
  body: AddCommentRequest,
): Promise<Comment> {
  const response = await fetch(
    `/api/tickets/${encodeURIComponent(ticketId)}/comments`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
  );
  if (!response.ok) {
    throw { problem: await parseProblemResponse(response) } satisfies ApiError;
  }
  return (await response.json()) as Comment;
}

export async function transitionTicket(
  ticketId: string,
  body: TransitionRequest,
): Promise<Ticket> {
  const response = await fetch(
    `/api/tickets/${encodeURIComponent(ticketId)}/transitions`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
  );
  if (!response.ok) {
    throw { problem: await parseProblemResponse(response) } satisfies ApiError;
  }
  return (await response.json()) as Ticket;
}

export async function askAssistant(body: AskRequest): Promise<AskResponse> {
  const response = await fetch("/api/ai/ask", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw { problem: await parseProblemResponse(response) } satisfies ApiError;
  }
  return (await response.json()) as AskResponse;
}