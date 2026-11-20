type FieldErrorsProps = {
  field: string;
  errors?: Record<string, string>;
};

export function FieldErrors({ field, errors }: FieldErrorsProps) {
  const message = errors?.[field];
  if (!message) {
    return null;
  }
  return (
    <div className="field-error" role="alert">
      {message}
    </div>
  );
}