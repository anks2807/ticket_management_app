import type { components } from "../api/schema-001";

export type Priority = components["schemas"]["Priority"];
export type Category = components["schemas"]["Category"];
export type TicketStatus = components["schemas"]["TicketStatus"];

function formatLabel(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

const PRIORITY_VALUES: Priority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const CATEGORY_VALUES: Category[] = ["BILLING", "TECHNICAL", "ACCOUNT", "SHIPPING", "OTHER"];
const STATUS_VALUES: TicketStatus[] = [
  "OPEN",
  "IN_PROGRESS",
  "RESOLVED",
  "CLOSED",
  "CANCELLED",
];

export const priorityOptions = PRIORITY_VALUES.map((value) => ({
  value,
  label: formatLabel(value),
}));

export const categoryOptions = CATEGORY_VALUES.map((value) => ({
  value,
  label: formatLabel(value),
}));

export const ticketStatusOptions = STATUS_VALUES.map((value) => ({
  value,
  label: formatLabel(value),
}));