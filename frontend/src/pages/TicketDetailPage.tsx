import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  addComment,
  editTicket,
  getTicket,
  toApiError,
  transitionTicket,
  type AddCommentRequest,
  type EditTicketRequest,
  type Ticket,
  type TransitionRequest,
} from "../api/client";
import { CommentForm } from "../components/CommentForm";
import { ProblemAlert } from "../components/ProblemAlert";
import { TicketDetailView } from "../components/TicketDetailView";
import { TicketEditForm } from "../components/TicketEditForm";
import { TransitionButtons } from "../components/TransitionButtons";
import { TransitionDialog } from "../components/TransitionDialog";
import type { ParseProblemResult } from "../errors/types";

export function TicketDetailPage() {
  const { ticketId } = useParams<{ ticketId: string }>();
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState<ParseProblemResult | null>(null);
  const [pageBanner, setPageBanner] = useState<ParseProblemResult | null>(null);
  const [busyProblem, setBusyProblem] = useState<ParseProblemResult | null>(null);
  const [transitionTarget, setTransitionTarget] = useState<
    Ticket["allowedTransitions"][number] | null
  >(null);

  const [editFieldErrors, setEditFieldErrors] = useState<Record<string, string>>({});
  const [commentFieldErrors, setCommentFieldErrors] = useState<Record<string, string>>({});
  const [transitionFieldErrors, setTransitionFieldErrors] = useState<Record<string, string>>({});

  const [editSubmitting, setEditSubmitting] = useState(false);
  const [commentSubmitting, setCommentSubmitting] = useState(false);
  const [transitionSubmitting, setTransitionSubmitting] = useState(false);

  const loadTicket = useCallback(async () => {
    if (!ticketId) {
      setNotFound({ kind: "generic", title: "Missing ticket id" });
      setTicket(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setNotFound(null);
    setBusyProblem(null);
    try {
      const data = await getTicket(ticketId);
      setTicket(data);
    } catch (error) {
      const { problem } = toApiError(error);
      setTicket(null);
      if (problem.kind === "notFound") {
        setNotFound(problem);
      } else {
        setPageBanner(problem);
      }
    } finally {
      setLoading(false);
    }
  }, [ticketId]);

  useEffect(() => {
    void loadTicket();
  }, [loadTicket]);

  const handleBusy = (problem: ParseProblemResult) => {
    if (problem.kind === "busy") {
      setBusyProblem(problem);
    }
  };

  const handleEdit = async (body: EditTicketRequest) => {
    if (!ticketId) {
      return;
    }
    setEditSubmitting(true);
    setEditFieldErrors({});
    setPageBanner(null);
    setBusyProblem(null);
    try {
      const updated = await editTicket(ticketId, body);
      setTicket(updated);
    } catch (error) {
      const { problem } = toApiError(error);
      if (problem.kind === "fieldErrors") {
        setEditFieldErrors(problem.fieldErrors);
        setPageBanner(problem);
      } else if (problem.kind === "frozen") {
        setPageBanner(problem);
      } else if (problem.kind === "busy") {
        handleBusy(problem);
      } else {
        setPageBanner(problem);
      }
    } finally {
      setEditSubmitting(false);
    }
  };

  const handleComment = async (body: AddCommentRequest) => {
    if (!ticketId) {
      return;
    }
    setCommentSubmitting(true);
    setCommentFieldErrors({});
    setBusyProblem(null);
    try {
      await addComment(ticketId, body);
      const updated = await getTicket(ticketId);
      setTicket(updated);
    } catch (error) {
      const { problem } = toApiError(error);
      if (problem.kind === "fieldErrors") {
        setCommentFieldErrors(problem.fieldErrors);
      } else if (problem.kind === "busy") {
        handleBusy(problem);
      } else {
        setPageBanner(problem);
      }
    } finally {
      setCommentSubmitting(false);
    }
  };

  const handleTransition = async (body: TransitionRequest) => {
    if (!ticketId) {
      return;
    }
    setTransitionSubmitting(true);
    setTransitionFieldErrors({});
    setPageBanner(null);
    setBusyProblem(null);
    try {
      const updated = await transitionTicket(ticketId, body);
      setTicket(updated);
      setTransitionTarget(null);
    } catch (error) {
      const { problem } = toApiError(error);
      if (problem.kind === "fieldErrors") {
        setTransitionFieldErrors(problem.fieldErrors);
      } else if (problem.kind === "invalidTransition") {
        setPageBanner(problem);
        setTransitionTarget(null);
      } else if (problem.kind === "busy") {
        handleBusy(problem);
      } else {
        setPageBanner(problem);
      }
    } finally {
      setTransitionSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="panel">
        <p>Loading ticket…</p>
      </div>
    );
  }

  if (notFound) {
    return (
      <div className="panel">
        <ProblemAlert problem={notFound} />
        <p>
          <Link to="/">Back to list</Link>
        </p>
      </div>
    );
  }

  if (!ticket) {
    return (
      <div className="panel">
        {pageBanner ? <ProblemAlert problem={pageBanner} /> : null}
        <p>
          <button type="button" onClick={() => void loadTicket()}>
            Retry
          </button>
        </p>
      </div>
    );
  }

  const actionDisabled = Boolean(busyProblem);

  return (
    <div className="panel">
      <h1>{ticket.title}</h1>
      <p>
        <Link to="/">Back to list</Link>
      </p>
      {busyProblem ? (
        <>
          <ProblemAlert problem={busyProblem} />
          <p>
            <button type="button" onClick={() => void loadTicket()}>
              Reload ticket
            </button>
          </p>
        </>
      ) : null}
      {pageBanner ? <ProblemAlert problem={pageBanner} /> : null}
      <TicketDetailView ticket={ticket} />
      <TicketEditForm
        ticket={ticket}
        fieldErrors={editFieldErrors}
        submitting={editSubmitting}
        onSubmit={(body) => void handleEdit(body)}
      />
      <CommentForm
        fieldErrors={commentFieldErrors}
        submitting={commentSubmitting}
        onSubmit={(body) => void handleComment(body)}
      />
      <TransitionButtons
        ticket={ticket}
        disabled={actionDisabled || transitionSubmitting}
        onSelectStatus={(status) => setTransitionTarget(status)}
      />
      <TransitionDialog
        targetStatus={transitionTarget}
        fieldErrors={transitionFieldErrors}
        submitting={transitionSubmitting}
        onClose={() => setTransitionTarget(null)}
        onSubmit={(body) => void handleTransition(body)}
      />
    </div>
  );
}