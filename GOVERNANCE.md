# Governance: cloud-itonami-isic-561

## Decision Authority

- **Maintainer**: Jun Kawasaki (jun784@gmail.com)
- **Steering**: cloud-itonami organization

## Three HARD, Permanent, Un-overridable Checks

The Governor enforces three checks at every proposal stage. **No exception, no override path.**

### Check 1: Reservation-Record Unverified
- Target reservation must exist in store
- Reservation must be `:registered?` (true)
- Reservation must be `:verified?` (true)
- Re-derived from store every time (never from proposal self-report)
- **Exception**: `:flag-safety-concern` (facility-level) doesn't require reservation verification

### Check 2: Effect Not `:propose`
- Effect must be `:propose`
- No other effect values (`:commit`, `:execute`, etc.) accepted
- Rejected outright if violated

### Check 3: Scope Exclusion
- **BLOCKED** (permanently, by design):
  - Food-safety/health-inspection determinations
  - Recipe/menu-content decisions
  - Food-handling-technique decisions
  - Safety-authority overrides

- **ALLOWED** (closed allowlist only):
  - `:schedule-reservation` — table/reservation scheduling logistics
  - `:coordinate-order-status-update` — administrative order-queue status tracking
  - `:coordinate-supply-request` — non-food consumables
  - `:schedule-staff-shift-proposal` — administrative shift proposals
  - `:flag-safety-concern` — facility/sanitation/safety escalation

- Scan: EN+JA substring matching (food-safety, health-code, menu-decision, recipe, etc.)
- Legitimate `:flag-safety-concern` ops can mention safety without self-blocking

## Rollout Phases (0→3)

- **Phase 0**: Read-only. All proposals held for human review.
- **Phase 1**: Auto-commit reservation + order status updates
- **Phase 2**: + supply coordination + staff shift proposals
- **Phase 3**: All non-safety auto-commit. Safety always escalates.

## Change Policy

Changes to the Governor or the Three Checks require consensus from the steering committee
and a new ADR.

Changes to the operation allowlist require ADR review.

## Escalation

All `:flag-safety-concern` proposals always escalate to human review.
No auto-commit for safety-related proposals, regardless of phase.
