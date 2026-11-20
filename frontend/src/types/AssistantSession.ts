import type { components } from "../api/schema-002";

export type AskResponse = components["schemas"]["AskResponse"];

export type AssistantSession = {
  lastQuestion?: string;
  lastResponse?: AskResponse;
};