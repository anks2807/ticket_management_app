import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  createTicket,
  toApiError,
  type CreateTicketRequest,
} from "../api/client";
import { EnumSelect } from "../components/EnumSelect";
import { FieldErrors } from "../components/FieldErrors";
import { ProblemAlert } from "../components/ProblemAlert";
import {
  categoryOptions,
  priorityOptions,
  type Category,
  type Priority,
} from "../lib/openapiEnums";
import type { ParseProblemResult } from "../errors/types";

type CreateFormDraft = {
  title: string;
  description: string;
  priority: Priority;
  category: Category;
  assignee: string;
};

const initialDraft: CreateFormDraft = {
  title: "",
  description: "",
  priority: "MEDIUM",
  category: "OTHER",
  assignee: "",
};

export function CreateTicketPage() {
  const navigate = useNavigate();
  const [draft, setDraft] = useState<CreateFormDraft>(initialDraft);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [banner, setBanner] = useState<ParseProblemResult | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setBanner(null);
    setFieldErrors({});

    const body: CreateTicketRequest = {
      title: draft.title,
      description: draft.description,
      priority: draft.priority,
      category: draft.category,
      assignee: draft.assignee.trim() === "" ? undefined : draft.assignee,
    };

    try {
      const ticket = await createTicket(body);
      navigate(`/tickets/${ticket.id}`);
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

  return (
    <div className="panel">
      <h1>Create ticket</h1>
      {banner ? <ProblemAlert problem={banner} /> : null}
      <form className="form-grid" onSubmit={(event) => void onSubmit(event)}>
        <div className="form-field">
          <label htmlFor="create-title">Title</label>
          <input
            id="create-title"
            value={draft.title}
            onChange={(event) => setDraft({ ...draft, title: event.target.value })}
          />
          <FieldErrors field="title" errors={fieldErrors} />
        </div>
        <div className="form-field">
          <label htmlFor="create-description">Description</label>
          <textarea
            id="create-description"
            value={draft.description}
            onChange={(event) => setDraft({ ...draft, description: event.target.value })}
          />
          <FieldErrors field="description" errors={fieldErrors} />
        </div>
        <EnumSelect
          id="create-priority"
          label="Priority"
          value={draft.priority}
          options={priorityOptions}
          onChange={(priority) =>
            setDraft({ ...draft, priority: (priority || "MEDIUM") as Priority })
          }
        />
        <FieldErrors field="priority" errors={fieldErrors} />
        <EnumSelect
          id="create-category"
          label="Category"
          value={draft.category}
          options={categoryOptions}
          onChange={(category) =>
            setDraft({ ...draft, category: (category || "OTHER") as Category })
          }
        />
        <FieldErrors field="category" errors={fieldErrors} />
        <div className="form-field">
          <label htmlFor="create-assignee">Assignee (optional)</label>
          <input
            id="create-assignee"
            value={draft.assignee}
            onChange={(event) => setDraft({ ...draft, assignee: event.target.value })}
          />
          <FieldErrors field="assignee" errors={fieldErrors} />
        </div>
        <button type="submit" className="primary" disabled={submitting}>
          {submitting ? "Creating…" : "Create ticket"}
        </button>
      </form>
    </div>
  );
}