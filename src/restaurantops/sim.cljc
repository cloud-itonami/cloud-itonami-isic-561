(ns restaurantops.sim
  "Deterministic demo runner for restaurant operations coordination.

  Scenarios:
  1. Schedule reservation (happy path)
  2. Update order status
  3. Request non-food supplies
  4. Propose staff shift
  5. Flag facility safety concern"
  (:require [restaurantops.store :as store]
            [restaurantops.operation :as op]))

(defn scenario-1-schedule-reservation
  "Happy path: schedule table reservation for verified registration."
  [s]
  (let [request {:operation :schedule-reservation
                 :reservation-id "res-1"
                 :table-number 5
                 :party-size 4}]
    (op/run-proposal s request)))

(defn scenario-2-order-status
  "Update order-queue status for reservation."
  [s]
  (let [request {:operation :coordinate-order-status-update
                 :reservation-id "res-1"
                 :order-id "order-1001"
                 :status-type "in-preparation"}]
    (op/run-proposal s request)))

(defn scenario-3-supply-request
  "Request non-food consumables (napkins, cleaning supplies)."
  [s]
  (let [request {:operation :coordinate-supply-request
                 :supply-type "napkins"
                 :quantity 2000
                 :requested-delivery-date "2026-07-20"}]
    (op/run-proposal s request)))

(defn scenario-4-staff-shift
  "Propose staff shift (administrative, not binding)."
  [s]
  (let [request {:operation :schedule-staff-shift-proposal
                 :staff-id "staff-042"
                 :shift-date "2026-07-18"
                 :shift-type "evening"}]
    (op/run-proposal s request)))

(defn scenario-5-safety-concern
  "Flag facility safety concern for human review."
  [s]
  (let [request {:operation :flag-safety-concern
                 :concern-type "sanitation"
                 :description "Drain back-up in kitchen area"
                 :severity "high"}]
    (op/run-proposal s request)))

(defn run-all-scenarios
  "Execute all 5 demo scenarios."
  []
  (let [s (store/make-store)]
    (println "╔════════════════════════════════════════════════════════════╗")
    (println "║ ISIC-561 Restaurant Operations Coordination Actor Demo   ║")
    (println "╚════════════════════════════════════════════════════════════╝")
    (println)
    (println "[1] Schedule reservation for verified registration")
    (let [result (scenario-1-schedule-reservation s)]
      (println "  Action:" (:action result))
      (println "  Passed governance:" (:passes? (:governance result)))
      (println))

    (println "[2] Coordinate order status update")
    (let [result (scenario-2-order-status s)]
      (println "  Action:" (:action result))
      (println "  Passed governance:" (:passes? (:governance result)))
      (println))

    (println "[3] Request non-food supplies")
    (let [result (scenario-3-supply-request s)]
      (println "  Action:" (:action result))
      (println "  Passed governance:" (:passes? (:governance result)))
      (println))

    (println "[4] Propose staff shift")
    (let [result (scenario-4-staff-shift s)]
      (println "  Action:" (:action result))
      (println "  Passed governance:" (:passes? (:governance result)))
      (println))

    (println "[5] Flag facility safety concern")
    (let [result (scenario-5-safety-concern s)]
      (println "  Action:" (:action result))
      (println "  Escalated (safety always escalates):" (= :escalate (:action result)))
      (println))

    (println "╔════════════════════════════════════════════════════════════╗")
    (println "║ All 5 scenarios completed successfully                  ║")
    (println "╚════════════════════════════════════════════════════════════╝")))
