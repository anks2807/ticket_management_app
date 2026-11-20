import { FormEvent, useEffect, useState } from "react";
import type { Ticket, TransitionRequest } from "../api/client";

type TargetStatus = Ticket["allowedTransitions"][number];
import { FieldErrors } from "./FieldErrors";

type TransitionDialogProps = {
  targetStatus: TargetStatus | null;
  fieldErrors: Record<string, string>;
  submitting: boolean;
  onClose: () => void;
  onSubmit: (body: TransitionRequest) => void;
};

export function TransitionDialog({
  targetStatus,
  fieldErrors,
  submitting,
  onClose,
  onSubmit,
}: TransitionDialogProps) {
  const [resolutionNotes, setResolutionNotes] = useState("");
  const [cancellationReason, setCancellationReason] = useState("");

  useEffect(() => {
    setResolutionNotes("");
    setCancellationReason("");
  }, [targetStatus]);

  if (!targetStatus) {
    return null;
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    const body: TransitionRequest = { status: targetStatus };
    if (targetStatus === "RESOLVED") {
      body.resolutionNotes = resolutionNotes;
    }
    if (targetStatus === "CANCELLED") {
      body.cancellationReason = cancellationReason;
    }
    onSubmit(body);
  };

  return (
    <div className="dialog-backdrop" role="presentation" onClick={onClose}>
      <div
        className="dialog-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="transition-dialog-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="transition-dialog-title">Move to {targetStatus.replaceAll("_", " ")}</h2>
        <form className="form-grid" onSubmit={handleSubmit}>
          {targetStatus === "RESOLVED" ? (
            <div className="form-field">
              <label htmlFor="transition-resolution-notes">Resolution notes</label>
              <textarea
                id="transition-resolution-notes"
                value={resolutionNotes}
                onChange={(event) => setResolutionNotes(event.target.value)}
              />
              <FieldErrors field="resolutionNotes" errors={fieldErrors} />
            </div>
          ) : null}
          {targetStatus === "CANCELLED" ? (
            <div className="form-field">
              <label htmlFor="transition-cancellation-reason">Cancellation reason</label>
              <textarea
                id="transition-cancellation-reason"
                value={cancellationReason}
                onChange={(event) => setCancellationReason(event.target.value)}
              />
              <FieldErrors field="cancellationReason" errors={fieldErrors} />
            </div>
          ) : null}
          <FieldErrors field="status" errors={fieldErrors} />
          <div className="dialog-actions">
            <button type="button" onClick={onClose} disabled={submitting}>
              Cancel
            </button>
            <button type="submit" className="primary" disabled={submitting}>
              {submitting ? "Moving…" : "Confirm move"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}