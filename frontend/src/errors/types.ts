export type FieldErrorsResult = {
  kind: "fieldErrors";
  fieldErrors: Record<string, string>;
  title: string;
};

export type InvalidTransitionResult = {
  kind: "invalidTransition";
  title: string;
  detail?: string;
  currentStatus: string;
  allowedTransitions: string[];
};

export type FrozenResult = {
  kind: "frozen";
  errorCode: "TICKET_FROZEN" | "RESOLUTION_NOTES_NOT_EDITABLE";
  title: string;
  detail?: string;
  currentStatus?: string;
};

export type NotFoundResult = {
  kind: "notFound";
  title: string;
  detail?: string;
};

export type BusyResult = {
  kind: "busy";
  title: string;
  detail?: string;
  retryAfterSeconds?: number;
};

export type GenericResult = {
  kind: "generic";
  title: string;
  detail?: string;
  errorCode?: string;
};

export type ParseProblemResult =
  | FieldErrorsResult
  | InvalidTransitionResult
  | FrozenResult
  | NotFoundResult
  | BusyResult
  | GenericResult;