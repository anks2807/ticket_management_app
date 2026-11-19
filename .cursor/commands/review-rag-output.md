# Review RAG output (hallucination / grounding check)

Input: a question, the assistant's response from `POST /api/ai/ask`, and the retrieved context (tickets/chunks with IDs). If the context is not provided, fetch the cited tickets via `GET /api/tickets/{id}` before judging. Never judge from memory.

For each sentence or claim in the answer, classify:
- **SUPPORTED** — stated in a retrieved ticket; quote the supporting fragment and its ticket ID.
- **UNSUPPORTED** — not found in any retrieved ticket (hallucination or general-knowledge fallback).
- **CONTRADICTED** — conflicts with a retrieved ticket.

Then check:
1. Every cited ticket ID is in the retrieved set (no invented citations).
2. Every ticket the answer relies on is cited.
3. Status/priority/category claims match ticket metadata.
4. If the context was empty or irrelevant, the response is the explicit no-relevant-tickets message — not a plausible answer.
5. The answer does not suggest or take actions (out of scope).

Output a table (claim, verdict, evidence ticket + quote), then a verdict: GROUNDED / PARTIALLY GROUNDED / UNGROUNDED.
If not fully grounded, draft a `docs/ai-mistakes.md` entry (category UNGROUNDED-ANSWER) with the question, the bad claim, the evidence, and a suspected cause (retrieval miss, threshold too low, prompt weakness, chunking).
