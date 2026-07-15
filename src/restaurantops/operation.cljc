(ns restaurantops.operation
  "langgraph-clj StateGraph for the restaurant/mobile food service coordination actor.

  The state machine flow is:
  intake → advise → govern → decide → {commit | hold | escalate} → audit

  This is the operational orchestration layer that drives proposals through
  the advisor and governor, and decides on commitment based on governance rules."
  (:require [restaurantops.store :as store]
            [restaurantops.advisor :as advisor]
            [restaurantops.governor :as governor]
            [restaurantops.phase :as phase]))

;; ---------------------- state schema ----------------------

(defn init-state
  "Create initial state for a proposal intake."
  [s request]
  {:store s
   :request request
   :proposal nil
   :governance {:violations [] :decision nil}
   :action nil
   :ledger-entry nil})

;; ---------------------- step functions ----------------------

(defn intake
  "Step 1: Intake the request, identify operation type."
  [state]
  (let [request (:request state)
        op-type (:operation request)]
    (assoc state :intake-op op-type)))

(defn advise
  "Step 2: Advisor generates proposal with confidence and reasoning."
  [state]
  (let [request (:request state)
        s (:store state)
        op-type (:operation request)]
    (case op-type
      :schedule-reservation
      (assoc state :proposal
             (advisor/advise-reservation-proposal
              s (:reservation-id request) (:table-number request)
              (:party-size request)))

      :coordinate-order-status-update
      (assoc state :proposal
             (advisor/advise-order-status-proposal
              s (:reservation-id request) (:order-id request)
              (:status-type request)))

      :coordinate-supply-request
      (assoc state :proposal
             (advisor/advise-supply-request
              s (:supply-type request) (:quantity request)
              (:requested-delivery-date request)))

      :schedule-staff-shift-proposal
      (assoc state :proposal
             (advisor/advise-shift-proposal
              s (:staff-id request) (:shift-date request)
              (:shift-type request)))

      :flag-safety-concern
      (assoc state :proposal
             (advisor/advise-safety-concern
              s (:concern-type request)
              (:description request) (:severity request)))

      (assoc state :proposal {:status :error :reason "Unknown operation type"}))))

(defn govern
  "Step 3: Governor evaluates the proposal against HARD checks."
  [state]
  (let [proposal (:proposal state)
        s (:store state)
        gov-result (governor/govern s proposal)]
    (assoc state :governance gov-result)))

(defn decide
  "Step 4: Decide on next action based on governance decision.
  - If all checks pass: commit (for auto-commit ops) or request-approval (others)
  - If any check fails: hold with violations
  - If escalation-flagged: always escalate"
  [state]
  (let [gov-result (:governance state)
        proposal (:proposal state)
        op-id (:operation proposal)]
    (if (:passes? gov-result)
      (if (or (:escalate? proposal) (= op-id :flag-safety-concern))
        (assoc state :action :escalate)
        (assoc state :action :request-approval))
      (assoc state :action :hold))))

(defn commit
  "Step 5a: Commit proposal to coordination log."
  [state]
  (let [s (:store state)
        proposal (:proposal state)
        record {:proposal proposal :timestamp #?(:clj (System/currentTimeMillis)
                                                   :cljs (js/Date.now))
                :status :committed}]
    (store/commit-record! s record)
    (assoc state :ledger-entry record :action :committed)))

(defn escalate
  "Step 5b: Escalate to human review (append to ledger, don't auto-commit)."
  [state]
  (let [s (:store state)
        proposal (:proposal state)
        fact {:proposal proposal :timestamp #?(:clj (System/currentTimeMillis)
                                                :cljs (js/Date.now))
              :status :escalated}]
    (store/append-ledger! s fact)
    (assoc state :ledger-entry fact :action :escalated)))

(defn hold
  "Step 5c: Hold proposal due to governance violations."
  [state]
  (let [s (:store state)
        gov-result (:governance state)
        fact {:proposal (:proposal state)
              :violations (:violations gov-result)
              :timestamp #?(:clj (System/currentTimeMillis)
                            :cljs (js/Date.now))
              :status :held}]
    (store/append-ledger! s fact)
    (assoc state :ledger-entry fact :action :held)))

(defn run-proposal
  "Complete proposal orchestration: intake → advise → govern → decide → action."
  [s request]
  (let [initial-state (init-state s request)
        after-intake (intake initial-state)
        after-advise (advise after-intake)
        after-govern (govern after-advise)
        after-decide (decide after-govern)
        final-state (case (:action after-decide)
                      :committed (commit after-decide)
                      :escalate (escalate after-decide)
                      :escalated (escalate after-decide)
                      :hold (hold after-decide)
                      :request-approval (assoc after-decide :action :pending-approval)
                      after-decide)]
    {:proposal (:proposal final-state)
     :governance (:governance final-state)
     :action (:action final-state)
     :violations (get-in final-state [:governance :violations])}))
