type EnumOption<T extends string> = {
  value: T;
  label: string;
};

type EnumSelectProps<T extends string> = {
  id: string;
  label: string;
  value: T | "";
  options: EnumOption<T>[];
  onChange: (value: T | "") => void;
  allowEmpty?: boolean;
  emptyLabel?: string;
};

export function EnumSelect<T extends string>({
  id,
  label,
  value,
  options,
  onChange,
  allowEmpty = false,
  emptyLabel = "Any",
}: EnumSelectProps<T>) {
  return (
    <div className="form-field">
      <label htmlFor={id}>{label}</label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value as T | "")}
      >
        {allowEmpty ? <option value="">{emptyLabel}</option> : null}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}