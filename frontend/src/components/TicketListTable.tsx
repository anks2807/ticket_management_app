import { Link } from "react-router-dom";
import type { components } from "../api/schema-001";

export type TicketSummary = components["schemas"]["TicketSummary"];

type TicketListTableProps = {
  items: TicketSummary[];
};

export function TicketListTable({ items }: TicketListTableProps) {
  if (items.length === 0) {
    return <p>No tickets match your filters.</p>;
  }

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th scope="col">ID</th>
            <th scope="col">Title</th>
            <th scope="col">Status</th>
            <th scope="col">Priority</th>
            <th scope="col">Category</th>
            <th scope="col">Updated</th>
          </tr>
        </thead>
        <tbody>
          {items.map((ticket) => (
            <tr key={ticket.id}>
              <td>
                <Link to={`/tickets/${ticket.id}`}>{ticket.id}</Link>
              </td>
              <td>{ticket.title}</td>
              <td>{ticket.status}</td>
              <td>{ticket.priority}</td>
              <td>{ticket.category}</td>
              <td>{new Date(ticket.updatedAt).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}