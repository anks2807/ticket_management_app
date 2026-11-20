import type { Ticket } from "../api/client";

type TicketDetailViewProps = {
  ticket: Ticket;
};

export function TicketDetailView({ ticket }: TicketDetailViewProps) {
  return (
    <section aria-label="Ticket details">
      <dl className="detail-dl">
        <div>
          <dt>ID</dt>
          <dd>{ticket.id}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>{ticket.status}</dd>
        </div>
        <div>
          <dt>Priority</dt>
          <dd>{ticket.priority}</dd>
        </div>
        <div>
          <dt>Category</dt>
          <dd>{ticket.category}</dd>
        </div>
        <div>
          <dt>Assignee</dt>
          <dd>{ticket.assignee ?? "—"}</dd>
        </div>
        <div>
          <dt>Description</dt>
          <dd>{ticket.description}</dd>
        </div>
        {ticket.resolutionNotes ? (
          <div>
            <dt>Resolution notes</dt>
            <dd>{ticket.resolutionNotes}</dd>
          </div>
        ) : null}
        {ticket.cancellationReason ? (
          <div>
            <dt>Cancellation reason</dt>
            <dd>{ticket.cancellationReason}</dd>
          </div>
        ) : null}
        <div>
          <dt>Created</dt>
          <dd>{new Date(ticket.createdAt).toLocaleString()}</dd>
        </div>
        <div>
          <dt>Updated</dt>
          <dd>{new Date(ticket.updatedAt).toLocaleString()}</dd>
        </div>
      </dl>
      <h2>Comments</h2>
      {ticket.comments.length === 0 ? (
        <p>No comments yet.</p>
      ) : (
        <ul className="comment-list">
          {ticket.comments.map((comment, index) => (
            <li key={`${comment.createdAt}-${index}`}>
              <strong>{comment.author}</strong>
              <span className="comment-meta">
                {" "}
                · {new Date(comment.createdAt).toLocaleString()}
              </span>
              <p>{comment.text}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}