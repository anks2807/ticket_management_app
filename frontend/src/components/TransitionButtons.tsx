import type { Ticket } from "../api/client";

type TargetStatus = Ticket["allowedTransitions"][number];

type TransitionButtonsProps = {
  ticket: Ticket;
  onSelectStatus: (status: TargetStatus) => void;
  disabled?: boolean;
};

export function TransitionButtons({
  ticket,
  onSelectStatus,
  disabled = false,
}: TransitionButtonsProps) {
  if (ticket.allowedTransitions.length === 0) {
    return <p>No status moves available for this ticket.</p>;
  }

  return (
    <div className="transition-buttons" role="group" aria-label="Move ticket">
      <h2>Move ticket</h2>
      {ticket.allowedTransitions.map((status) => (
        <button
          key={status}
          type="button"
          disabled={disabled}
          onClick={() => onSelectStatus(status)}
        >
          Move to {status.replaceAll("_", " ")}
        </button>
      ))}
    </div>
  );
}