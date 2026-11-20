import type { ParseProblemResult } from "../errors/types";

type ProblemAlertProps = {
  problem: ParseProblemResult;
};

export function ProblemAlert({ problem }: ProblemAlertProps) {
  switch (problem.kind) {
    case "invalidTransition":
      return (
        <div className="problem-alert" data-variant="error" role="alert">
          <strong>{problem.title}</strong>
          {problem.detail ? <p>{problem.detail}</p> : null}
          <p>
            Current status: <code>{problem.currentStatus}</code>
          </p>
          <p>
            Allowed moves:{" "}
            {problem.allowedTransitions.length > 0
              ? problem.allowedTransitions.join(", ")
              : "(none)"}
          </p>
        </div>
      );
    case "frozen":
      return (
        <div className="problem-alert" data-variant="error" role="alert">
          <strong>{problem.title}</strong>
          {problem.detail ? <p>{problem.detail}</p> : null}
        </div>
      );
    case "notFound":
      return (
        <div className="problem-alert" data-variant="error" role="alert">
          <strong>{problem.title}</strong>
          {problem.detail ? <p>{problem.detail}</p> : null}
        </div>
      );
    case "busy":
      return (
        <div className="problem-alert" data-variant="info" role="alert">
          <strong>{problem.title}</strong>
          {problem.detail ? <p>{problem.detail}</p> : null}
          {problem.retryAfterSeconds !== undefined ? (
            <p>Retry after {problem.retryAfterSeconds} second(s).</p>
          ) : null}
        </div>
      );
    case "fieldErrors":
      return (
        <div className="problem-alert" data-variant="error" role="alert">
          <strong>{problem.title}</strong>
          <p>Please fix the highlighted fields below.</p>
        </div>
      );
    case "generic":
    default:
      return (
        <div className="problem-alert" data-variant="error" role="alert">
          <strong>{problem.title}</strong>
          {problem.detail ? <p>{problem.detail}</p> : null}
        </div>
      );
  }
}