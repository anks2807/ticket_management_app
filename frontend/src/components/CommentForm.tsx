import { FormEvent, useState } from "react";
import type { AddCommentRequest } from "../api/client";
import { FieldErrors } from "./FieldErrors";

type CommentFormProps = {
  fieldErrors: Record<string, string>;
  submitting: boolean;
  onSubmit: (body: AddCommentRequest) => void;
};

export function CommentForm({ fieldErrors, submitting, onSubmit }: CommentFormProps) {
  const [author, setAuthor] = useState("");
  const [text, setText] = useState("");

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit({ author, text });
  };

  return (
    <form className="form-grid" onSubmit={handleSubmit} aria-label="Add comment">
      <h2>Add comment</h2>
      <div className="form-field">
        <label htmlFor="comment-author">Author</label>
        <input
          id="comment-author"
          value={author}
          onChange={(event) => setAuthor(event.target.value)}
        />
        <FieldErrors field="author" errors={fieldErrors} />
      </div>
      <div className="form-field">
        <label htmlFor="comment-text">Comment</label>
        <textarea
          id="comment-text"
          value={text}
          onChange={(event) => setText(event.target.value)}
        />
        <FieldErrors field="text" errors={fieldErrors} />
      </div>
      <button type="submit" className="primary" disabled={submitting}>
        {submitting ? "Posting…" : "Post comment"}
      </button>
    </form>
  );
}