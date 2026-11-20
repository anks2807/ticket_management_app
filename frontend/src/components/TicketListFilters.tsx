import { ticketStatusOptions } from "../lib/openapiEnums";
import type { TicketStatus } from "../lib/openapiEnums";
import { EnumSelect } from "./EnumSelect";

export type ListQueryState = {
  q: string;
  status: TicketStatus | "";
  page: number;
};

type TicketListFiltersProps = {
  query: ListQueryState;
  onChange: (next: ListQueryState) => void;
  onSearch: () => void;
  onPrevPage: () => void;
  onNextPage: () => void;
  canPrev: boolean;
  canNext: boolean;
  pageLabel: string;
};

export function TicketListFilters({
  query,
  onChange,
  onSearch,
  onPrevPage,
  onNextPage,
  canPrev,
  canNext,
  pageLabel,
}: TicketListFiltersProps) {
  return (
    <>
      <div className="filters-row">
        <div className="form-field">
          <label htmlFor="ticket-search">Keyword</label>
          <input
            id="ticket-search"
            type="search"
            value={query.q}
            onChange={(event) => onChange({ ...query, q: event.target.value })}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                onSearch();
              }
            }}
          />
        </div>
        <EnumSelect
          id="ticket-status-filter"
          label="Status"
          value={query.status}
          options={ticketStatusOptions}
          onChange={(status) =>
            onChange({ ...query, status: status as TicketStatus | "", page: 0 })
          }
          allowEmpty
        />
        <div className="form-field">
          <span aria-hidden="true">&nbsp;</span>
          <button type="button" className="primary" onClick={onSearch}>
            Search
          </button>
        </div>
      </div>
      <div className="pager">
        <button type="button" disabled={!canPrev} onClick={onPrevPage}>
          Previous
        </button>
        <span>{pageLabel}</span>
        <button type="button" disabled={!canNext} onClick={onNextPage}>
          Next
        </button>
      </div>
    </>
  );
}