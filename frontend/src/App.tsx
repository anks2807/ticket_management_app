import { useState } from "react";
import { BrowserRouter, NavLink } from "react-router-dom";
import { AssistantPanel } from "./components/assistant/AssistantPanel";
import { AppRoutes } from "./routes";
import type { AssistantSession } from "./types/AssistantSession";

export function App() {
  const [assistantSession, setAssistantSession] = useState<AssistantSession>({});

  return (
    <BrowserRouter>
      <div className="app-shell">
        <header className="app-header">
          <p className="app-title">Support Agent</p>
          <nav aria-label="Main">
            <NavLink to="/" end>
              Tickets
            </NavLink>
            <NavLink to="/tickets/new">New ticket</NavLink>
          </nav>
        </header>
        <div className="app-main">
          <AppRoutes />
          <aside className="panel" aria-label="Assistant">
            <h2>Assistant</h2>
            <AssistantPanel
              session={assistantSession}
              onSessionChange={setAssistantSession}
            />
          </aside>
        </div>
      </div>
    </BrowserRouter>
  );
}