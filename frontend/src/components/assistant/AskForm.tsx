import { FormEvent, useState } from "react";
import { FieldErrors } from "../FieldErrors";

const QUESTION_MAX_LENGTH = 1000;

type AskFormProps = {
  defaultQuestion?: string;
  fieldErrors: Record<string, string>;
  submitting: boolean;
  onSubmit: (question: string) => void;
};

export function AskForm({
  defaultQuestion = "",
  fieldErrors,
  submitting,
  onSubmit,
}: AskFormProps) {
  const [question, setQuestion] = useState(defaultQuestion);

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit(question);
  };

  return (
    <form className="form-grid" onSubmit={handleSubmit} aria-label="Ask assistant">
      <div className="form-field">
        <label htmlFor="assistant-question">Question</label>
        <textarea
          id="assistant-question"
          maxLength={QUESTION_MAX_LENGTH}
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
        />
        <p className="field-hint">
          {question.length} / {QUESTION_MAX_LENGTH}
        </p>
        <FieldErrors field="question" errors={fieldErrors} />
      </div>
      <button type="submit" className="primary" disabled={submitting}>
        {submitting ? "Asking…" : "Ask"}
      </button>
    </form>
  );
}