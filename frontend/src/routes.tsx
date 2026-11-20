import { Routes, Route } from "react-router-dom";
import { CreateTicketPage } from "./pages/CreateTicketPage";
import { TicketDetailPage } from "./pages/TicketDetailPage";
import { TicketListPage } from "./pages/TicketListPage";

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<TicketListPage />} />
      <Route path="/tickets/new" element={<CreateTicketPage />} />
      <Route path="/tickets/:ticketId" element={<TicketDetailPage />} />
    </Routes>
  );
}