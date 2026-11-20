export function CitationFailure() {
  return (
    <div
      className="problem-alert"
      data-variant="error"
      data-outcome="CITATION_VERIFICATION_FAILED"
      role="alert"
    >
      <strong>Citation verification failed</strong>
      <p>
        The assistant could not verify citations for this answer. Do not rely on ticket
        links until you refresh and try again.
      </p>
    </div>
  );
}