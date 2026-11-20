import { useCallback, useEffect, useState } from "react";
import { listTickets, toApiError, type TicketPage } from "../api/client";
import { ProblemAlert } from "../components/ProblemAlert";
import {
  TicketListFilters,
  type ListQueryState,
} from "../components/TicketListFilters";
import { TicketListTable } from "../components/TicketListTable";
import type { ParseProblemResult } from "../errors/types";

export function TicketListPage() {
  const [query, setQuery] = useState<ListQueryState>({ q: "", status: "", page: 0 });
  const [appliedQuery, setAppliedQuery] = useState<ListQueryState>({
    q: "",
    status: "",
    page: 0,
  });
  const [page, setPage] = useState<TicketPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<ParseProblemResult | null>(null);

  const load = useCallback(async (next: ListQueryState) => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await listTickets({
        q: next.q,
        status: next.status || undefined,
        page: next.page,
      });
      setPage(data);
    } catch (error) {
      const { problem } = toApiError(error);
      setLoadError(problem);
      setPage(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(appliedQuery);
  }, [appliedQuery, load]);

  const runSearch = () => {
    const next = { ...query, page: 0 };
    setQuery(next);
    setAppliedQuery(next);
  };

  const pageLabel =
    page !== null
      ? `Page ${page.page + 1} of ${Math.max(page.totalPages, 1)} (${page.totalItems} tickets)`
      : "—";

  return (
    <div className="panel">
      <h1>Tickets</h1>
      <TicketListFilters
        query={query}
        onChange={setQuery}
        onSearch={runSearch}
        canPrev={appliedQuery.page > 0}
        canNext={page !== null && appliedQuery.page + 1 < page.totalPages}
        onPrevPage={() => {
          const next = { ...appliedQuery, page: Math.max(0, appliedQuery.page - 1) };
          setQuery(next);
          setAppliedQuery(next);
        }}
        onNextPage={() => {
          if (!page || appliedQuery.page + 1 >= page.totalPages) {
            return;
          }
          const next = { ...appliedQuery, page: appliedQuery.page + 1 };
          setQuery(next);
          setAppliedQuery(next);
        }}
        pageLabel={pageLabel}
      />
      {loadError ? (
        <ProblemAlert problem={loadError} />
      ) : null}
      {loadError ? (
        <p>
          <button type="button" onClick={() => void load(appliedQuery)}>
            Retry
          </button>
        </p>
      ) : null}
      {loading ? <p>Loading…</p> : null}
      {!loading && !loadError && page ? <TicketListTable items={page.items} /> : null}
    </div>
  );
}