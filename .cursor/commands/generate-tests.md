# Generate tests

Generate tests for the given class, endpoint, or spec scenario, following `.cursor/rules/testing.mdc`.

Steps:
1. Read the relevant spec scenarios (`specs/<feature>/spec.md`) and list them with IDs before writing code.
2. For each scenario write at least one test; name it after the behaviour; reference the scenario/FR in a comment.
3. Deterministic logic → Testcontainers PostgreSQL integration tests (extend the shared base class). Pure domain rules → plain unit tests.
4. Errors → assert HTTP status, `errorCode`, field names, and that stored data and refresh intents are unchanged.
5. AI features → assert grounding properties, never exact wording; mock only the chat model, never the retriever, for no-match tests.
6. Run the tests. If they are meant to be red-first, show the failing output; otherwise show them passing.

Output: the test files, then a coverage table (scenario → test method → pass/fail). List any scenario you could not test and why.
