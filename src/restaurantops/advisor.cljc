(ns restaurantops.advisor
  "Advisor that generates proposals with confidence scores for restaurant operations.

  This is a deterministic demo advisor. Production would use LLM-based scoring.
  Returns proposals with :effect :propose and confidence levels."
  (:require [clojure.string :as str]))

(defn advise-reservation-proposal
  "Generate a proposal for table/reservation scheduling."
  [store reservation-id table-number party-size]
  {:operation :schedule-reservation
   :effect :propose
   :reservation-id reservation-id
   :table-number table-number
   :party-size party-size
   :confidence 0.95
   :reasoning "Reservation scheduling is administrative logistics only"})

(defn advise-order-status-proposal
  "Generate a proposal for order-queue status-tracking logistics."
  [store reservation-id order-id status-type]
  {:operation :coordinate-order-status-update
   :effect :propose
   :reservation-id reservation-id
   :order-id order-id
   :status-type status-type
   :confidence 0.90
   :reasoning "Order status tracking is administrative coordination only"})

(defn advise-supply-request
  "Generate a proposal for non-food supply coordination."
  [store supply-type quantity requested-delivery-date]
  {:operation :coordinate-supply-request
   :effect :propose
   :supply-type supply-type
   :quantity quantity
   :requested-delivery-date requested-delivery-date
   :confidence 0.92
   :reasoning "Supply request for non-food consumables is administrative logistics only"})

(defn advise-shift-proposal
  "Generate a proposal for staff shift scheduling (proposal only, not binding)."
  [store staff-id shift-date shift-type]
  {:operation :schedule-staff-shift-proposal
   :effect :propose
   :staff-id staff-id
   :shift-date shift-date
   :shift-type shift-type
   :confidence 0.88
   :reasoning "Staff shift proposal is administrative scheduling only, not binding"})

(defn advise-safety-concern
  "Generate a proposal to flag facility/sanitation safety concerns for human review."
  [store concern-type description severity]
  {:operation :flag-safety-concern
   :effect :propose
   :concern-type concern-type
   :description description
   :severity severity
   :confidence 0.98
   :reasoning "Safety concerns always escalate to human review"
   :escalate? true})
