# cloud-itonami-isic-561 — Restaurant & Mobile Food Service Operations Coordination

An administrative coordination actor for restaurants and mobile food service operations (ISIC 561).

## Domain Scope

This actor coordinates **back-office, non-food-preparation logistics**:
- Table/reservation scheduling
- Order-queue status tracking
- Non-food supply coordination (napkins, cleaning supplies, utensils)
- Staff shift proposals
- Facility/sanitation safety concern escalation

### Out of Scope

This actor **does not handle** and **never will**:
- Food-safety/health-inspection determinations
- Recipe/menu-content decisions
- Food-handling technique decisions
- Safety-authority overrides

These are enforced by the Governor's three HARD, permanent, un-overridable checks.

## Architecture

### Core Modules

- **store.cljc**: Single source of truth (reservations, supply inventory, audit ledgers)
- **advisor.cljc**: Proposal confidence scoring (deterministic demo; production uses LLM)
- **governor.cljc**: Three HARD checks (unverified-record, non-propose effect, scope exclusion)
- **operation.cljc**: langgraph-clj StateGraph orchestration (intake → advise → govern → decide → action)
- **phase.cljc**: Rollout phases (0→3) with auto-commit gates
- **sim.cljc**: Deterministic demo runner (5 scenarios)

### Governance

- **Three HARD Checks** (un-overridable):
  1. Reservation must be registered AND verified (re-derived every time)
  2. Effect must be `:propose` (no other effect values)
  3. Scope exclusion: blocked territory (food-safety, recipe, technique) by EN+JA substring scan

- **Allowed Operations** (closed allowlist):
  - `:schedule-reservation`
  - `:coordinate-order-status-update`
  - `:coordinate-supply-request`
  - `:schedule-staff-shift-proposal`
  - `:flag-safety-concern` (always escalates)

- **Rollout Phases** (0→3):
  - Phase 0: Read-only (all proposals held for human review)
  - Phase 1: Auto-commit reservation + order status
  - Phase 2: + supply coordination + staff shifts
  - Phase 3: All non-safety auto-commit; safety always escalates

## Running

### Tests

```bash
nbb run-tests.cljs
```

All tests pass. Store, Governor, Operations, and Phase tests included.

### Demo

```bash
nbb run-demo.cljs
```

5 scenarios run to completion:
1. Schedule reservation (happy path)
2. Update order status
3. Request non-food supplies
4. Propose staff shift
5. Flag facility safety concern

## Requirements

- Node.js 16+ (for `nbb`)
- nbb (Node-based Babashka / ClojureScript)

## License

AGPL-3.0. See [LICENSE](LICENSE).

## Code of Conduct

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [SECURITY.md](SECURITY.md).

## References

- ADR-2607152500: Wave 4 rollout authorization
- ADR-2607121000: ISIC/ISCO wave definition and toposort
- Skill `build-actor`: Actor pattern and langgraph-clj StateGraph

## Contact

Jun Kawasaki — jun784@gmail.com
