export function NoRelevantTickets() {
  return (
    <div className="problem-alert" data-variant="info" data-outcome="NO_RELEVANT_TICKETS" role="status">
      <strong>No relevant tickets</strong>
      <p>
        The assistant did not find tickets that answer your question. Try different
        keywords or broaden your search.
      </p>
    </div>
  );
}