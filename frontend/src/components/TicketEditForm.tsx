import { FormEvent, useEffect, useState } from "react";
import type { EditTicketRequest, Ticket } from "../api/client";
import {
  categoryOptions,
  priorityOptions,
  type Category,
  type Priority,
} from "../lib/openapiEnums";
import { EnumSelect } from "./EnumSelect";
import { FieldErrors } from "./FieldErrors";

export type DetailDraft = {
  title: string;
  description: string;
  priority: Priority;
  category: Category;
  assignee: string;
  resolutionNotes: string;
};

function draftFromTicket(ticket: Ticket): DetailDraft {
  return {
    title: ticket.title,
    description: ticket.description,
    priority: ticket.priority,
    category: ticket.category,
    assignee: ticket.assignee ?? "",
    resolutionNotes: ticket.resolutionNotes ?? "",
  };
}

type TicketEditFormProps = {
  ticket: Ticket;
  fieldErrors: Record<string, string>;
  submitting: boolean;
  onSubmit: (body: EditTicketRequest) => void;
};

export function TicketEditForm({
  ticket,
  fieldErrors,
  submitting,
  onSubmit,
}: TicketEditFormProps) {
  const [draft, setDraft] = useState<DetailDraft>(() => draftFromTicket(ticket));

  useEffect(() => {
    setDraft(draftFromTicket(ticket));
  }, [ticket]);

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    const body: EditTicketRequest = {
      title: draft.title,
      description: draft.description,
      priority: draft.priority,
      category: draft.category,
    };
    const assigneeTrimmed = draft.assignee.trim();
    if (assigneeTrimmed === "") {
      body.assignee = null;
    } else {
      body.assignee = assigneeTrimmed;
    }
    if (draft.resolutionNotes.trim() !== "") {
      body.resolutionNotes = draft.resolutionNotes;
    }
    onSubmit(body);
  };

  return (
    <form className="form-grid" onSubmit={handleSubmit} aria-label="Edit ticket">
      <h2>Edit fields</h2>
      <div className="form-field">
        <label htmlFor="edit-title">Title</label>
        <input
          id="edit-title"
          value={draft.title}
          onChange={(event) => setDraft({ ...draft, title: event.target.value })}
        />
        <FieldErrors field="title" errors={fieldErrors} />
      </div>
      <div className="form-field">
        <label htmlFor="edit-description">Description</label>
        <textarea
          id="edit-description"
          value={draft.description}
          onChange={(event) => setDraft({ ...draft, description: event.target.value })}
        />
        <FieldErrors field="description" errors={fieldErrors} />
      </div>
      <EnumSelect
        id="edit-priority"
        label="Priority"
        value={draft.priority}
        options={priorityOptions}
        onChange={(priority) =>
          setDraft({ ...draft, priority: (priority || ticket.priority) as Priority })
        }
      />
      <FieldErrors field="priority" errors={fieldErrors} />
      <EnumSelect
        id="edit-category"
        label="Category"
        value={draft.category}
        options={categoryOptions}
        onChange={(category) =>
          setDraft({ ...draft, category: (category || ticket.category) as Category })
        }
      />
      <FieldErrors field="category" errors={fieldErrors} />
      <div className="form-field">
        <label htmlFor="edit-assignee">Assignee</label>
        <input
          id="edit-assignee"
          value={draft.assignee}
          onChange={(event) => setDraft({ ...draft, assignee: event.target.value })}
        />
        <FieldErrors field="assignee" errors={fieldErrors} />
      </div>
      {ticket.resolutionNotes !== null && ticket.resolutionNotes !== undefined ? (
        <div className="form-field">
          <label htmlFor="edit-resolution-notes">Resolution notes</label>
          <textarea
            id="edit-resolution-notes"
            value={draft.resolutionNotes}
            onChange={(event) =>
              setDraft({ ...draft, resolutionNotes: event.target.value })
            }
          />
          <FieldErrors field="resolutionNotes" errors={fieldErrors} />
        </div>
      ) : null}
      <button type="submit" className="primary" disabled={submitting}>
        {submitting ? "Saving…" : "Save changes"}
      </button>
    </form>
  );
}