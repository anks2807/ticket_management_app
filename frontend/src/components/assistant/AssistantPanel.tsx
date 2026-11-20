import { useState } from "react";
import { askAssistant, toApiError } from "../../api/client";
import { ProblemAlert } from "../ProblemAlert";
import type { AssistantSession } from "../../types/AssistantSession";
import type { ParseProblemResult } from "../../errors/types";
import { AskForm } from "./AskForm";
import { CitationFailure } from "./CitationFailure";
import { GroundedAnswer } from "./GroundedAnswer";
import { NoRelevantTickets } from "./NoRelevantTickets";

type AssistantPanelProps = {
  session: AssistantSession;
  onSessionChange: (session: AssistantSession) => void;
};

export function AssistantPanel({ session, onSessionChange }: AssistantPanelProps) {
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [banner, setBanner] = useState<ParseProblemResult | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleAsk = async (question: string) => {
    setSubmitting(true);
    setFieldErrors({});
    setBanner(null);
    try {
      const response = await askAssistant({ question });
      onSessionChange({ lastQuestion: question, lastResponse: response });
    } catch (error) {
      const { problem } = toApiError(error);
      if (problem.kind === "fieldErrors") {
        setFieldErrors(problem.fieldErrors);
        setBanner(problem);
      } else {
        setBanner(problem);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const outcome = session.lastResponse?.outcome;

  return (
    <>
      <AskForm
        defaultQuestion={session.lastQuestion ?? ""}
        fieldErrors={fieldErrors}
        submitting={submitting}
        onSubmit={(question) => void handleAsk(question)}
      />
      {banner ? <ProblemAlert problem={banner} /> : null}
      {outcome === "GROUNDED" && session.lastResponse ? (
        <GroundedAnswer response={session.lastResponse} />
      ) : null}
      {outcome === "NO_RELEVANT_TICKETS" ? <NoRelevantTickets /> : null}
      {outcome === "CITATION_VERIFICATION_FAILED" ? <CitationFailure /> : null}
    </>
  );
}