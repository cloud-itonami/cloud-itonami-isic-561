(ns restaurantops.phase
  "Rollout phases (0→3) for restaurant operations coordination.

  Phase 0: Read-only. All proposals held for human review.
  Phase 1: Auto-commit reservation scheduling + order status updates.
  Phase 2: + supply coordination + staff shift proposals auto-commit.
  Phase 3: All non-safety auto-commit. Safety concerns always escalate.")

(def phase-config
  {0 {:name "Read-only"
      :auto-commit-ops #{}
      :description "All proposals held for human review"}
   1 {:name "Reservation + Order Status"
      :auto-commit-ops #{:schedule-reservation :coordinate-order-status-update}
      :description "Reservation + order status auto-commit"}
   2 {:name "Reservation + Status + Supply + Shift"
      :auto-commit-ops #{:schedule-reservation
                         :coordinate-order-status-update
                         :coordinate-supply-request
                         :schedule-staff-shift-proposal}
      :description "Supply + shift proposals also auto-commit"}
   3 {:name "Full (except safety)"
      :auto-commit-ops #{:schedule-reservation
                         :coordinate-order-status-update
                         :coordinate-supply-request
                         :schedule-staff-shift-proposal}
      :description "All non-safety auto-commit; safety always escalates"}})

(defn can-auto-commit?
  "Check if proposal can auto-commit at given phase."
  [phase op-id]
  (let [config (phase-config phase)
        auto-commit-ops (:auto-commit-ops config)]
    (contains? auto-commit-ops op-id)))

(defn gate-op
  "Gate a proposal based on phase: allow, hold for review, or escalate."
  [phase proposal governance-result]
  (let [op-id (:operation proposal)]
    (cond
      (not (:passes? governance-result))
      {:action :hold
       :reason "Governor rejected"}

      (= op-id :flag-safety-concern)
      {:action :escalate
       :reason "Safety concerns always escalate"}

      (can-auto-commit? phase op-id)
      {:action :commit
       :reason "Auto-commit in current phase"}

      :else
      {:action :pending-approval
       :reason "Awaiting human approval"})))
