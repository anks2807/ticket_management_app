import { Link } from "react-router-dom";
import type { AskResponse } from "../../api/client";

type GroundedAnswerProps = {
  response: AskResponse;
};

export function GroundedAnswer({ response }: GroundedAnswerProps) {
  return (
    <div data-outcome="GROUNDED">
      <p className="assistant-answer">{response.answer}</p>
      {response.sources && response.sources.length > 0 ? (
        <ul className="source-list">
          {response.sources.map((source) => (
            <li key={source.ticketId}>
              <Link to={`/tickets/${source.ticketId}`}>
                {source.ticketId}: {source.title} ({source.status})
              </Link>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}