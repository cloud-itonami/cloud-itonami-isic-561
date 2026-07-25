(ns restaurantops.test
  "Full test suite for ISIC-561 restaurant operations coordination actor.

  Tests store, governor, operations, and phases."
  (:require [restaurantops.store :as store]
            [restaurantops.governor :as governor]
            [restaurantops.operation :as op]
            [restaurantops.phase :as phase]))

;; ======================== Test Utilities ========================

(def test-results (atom []))

(defn test
  "Run a test function, record result."
  [name f]
  (try
    (f)
    (swap! test-results conj {:name name :status :pass})
    true
    (catch #?(:clj Exception :cljs js/Error) e
      (swap! test-results conj {:name name :status :fail :error (str e)})
      false)))

(defn assert-equal
  "Assert equality."
  [a b msg]
  (if (= a b)
    true
    (throw (#?(:clj Exception :cljs js/Error) (str msg " (expected " b " got " a ")")))))

(defn assert-true
  "Assert truthiness."
  [v msg]
  (if v true (throw (#?(:clj Exception :cljs js/Error) msg))))

;; ======================== Store Tests ========================

(defn test-store-reservation-lookup
  []
  (let [s (store/make-store)]
    (assert-equal (not (nil? (store/reservation s "res-1")))
                  true
                  "Reservation lookup failed")))

(defn test-store-all-reservations
  []
  (let [s (store/make-store)
        all-res (store/all-reservations s)]
    (assert-equal (> (count all-res) 0) true "No reservations found")))

(defn test-store-supply-lookup
  []
  (let [s (store/make-store)
        supply (store/supply-item s "supply-1")]
    (assert-equal (not (nil? supply)) true "Supply lookup failed")))

(defn test-store-ledger-append
  []
  (let [s (store/make-store)]
    (store/append-ledger! s {:test "fact"})
    (assert-equal (> (count (store/ledger s)) 0) true "Ledger append failed")))

;; ======================== Governor Tests ========================

(defn test-governor-reservation-unverified
  []
  (let [s (store/make-store)
        proposal {:operation :schedule-reservation
                  :effect :propose
                  :reservation-id "res-3"
                  :party-size 2}
        result (governor/govern s proposal)]
    (assert-equal (:passes? result) false "Should fail on unverified reservation")))

(defn test-governor-effect-not-propose
  []
  (let [s (store/make-store)
        proposal {:operation :schedule-reservation
                  :effect :commit
                  :reservation-id "res-1"}
        result (governor/govern s proposal)]
    (assert-equal (:passes? result) false "Should fail on non-propose effect")))

(defn test-governor-scope-exclusion-food-safety
  []
  (let [s (store/make-store)
        proposal {:operation :schedule-reservation
                  :effect :propose
                  :reservation-id "res-1"
                  :content-note "food safety check required"}
        result (governor/govern s proposal)]
    (assert-equal (:passes? result) false "Should block food-safety mention")))

(defn test-governor-scope-exclusion-recipe
  []
  (let [s (store/make-store)
        proposal {:operation :schedule-reservation
                  :effect :propose
                  :reservation-id "res-1"
                  :content-note "menu-decision for Tuesday"}
        result (governor/govern s proposal)]
    (assert-equal (:passes? result) false "Should block menu-decision mention")))

(defn test-governor-safety-escalation
  []
  (let [s (store/make-store)
        proposal {:operation :flag-safety-concern
                  :effect :propose
                  :concern-type "facility-hazard"
                  :description "Water leak in kitchen"}
        result (governor/govern s proposal)]
    (assert-equal (:passes? result) true "Safety concern should pass governor")))

(defn test-governor-happy-path
  []
  (let [s (store/make-store)
        proposal {:operation :schedule-reservation
                  :effect :propose
                  :reservation-id "res-1"
                  :party-size 4}
        result (governor/govern s proposal)]
    (assert-equal (:passes? result) true "Happy path should pass")))

;; ======================== Operation Tests ========================

(defn test-operation-reservation-proposal
  []
  (let [s (store/make-store)
        request {:operation :schedule-reservation
                 :reservation-id "res-1"
                 :table-number 5
                 :party-size 4}
        result (op/run-proposal s request)]
    (assert-equal (= :APPROVE (get-in result [:governance :decision]))
                  true
                  "Reservation proposal should approve")))

(defn test-operation-unverified-rejection
  []
  (let [s (store/make-store)
        request {:operation :schedule-reservation
                 :reservation-id "res-3"
                 :table-number 9}
        result (op/run-proposal s request)]
    (assert-equal (= :REJECT (get-in result [:governance :decision]))
                  true
                  "Unverified reservation should be rejected")))

(defn test-operation-safety-escalation
  []
  (let [s (store/make-store)
        request {:operation :flag-safety-concern
                 :concern-type "sanitation"
                 :description "Drain backup"
                 :severity "high"}
        result (op/run-proposal s request)]
    (assert-equal (or (= :escalate (:action result)) (= :escalated (:action result)))
                  true
                  "Safety concern should escalate")))

;; ======================== Phase Tests ========================

(defn test-phase-0-readonly
  []
  (let [phase 0
        op-id :schedule-reservation]
    (assert-equal (phase/can-auto-commit? phase op-id)
                  false
                  "Phase 0 should not auto-commit")))

(defn test-phase-1-reservation-status
  []
  (let [phase 1
        op-ids [:schedule-reservation :coordinate-order-status-update]]
    (assert-equal (every? #(phase/can-auto-commit? phase %) op-ids)
                  true
                  "Phase 1 should auto-commit reservation and status")))

(defn test-phase-3-full
  []
  (let [phase 3
        op-ids [:schedule-reservation :coordinate-order-status-update
                :coordinate-supply-request :schedule-staff-shift-proposal]]
    (assert-equal (every? #(phase/can-auto-commit? phase %) op-ids)
                  true
                  "Phase 3 should auto-commit all non-safety")))

;; ======================== Test Runner ========================

(defn run-tests
  "Run all tests and report results."
  []
  (reset! test-results [])

  ;; Store tests
  (test "Store: reservation lookup" test-store-reservation-lookup)
  (test "Store: all reservations" test-store-all-reservations)
  (test "Store: supply lookup" test-store-supply-lookup)
  (test "Store: ledger append" test-store-ledger-append)

  ;; Governor tests
  (test "Governor: reservation unverified check" test-governor-reservation-unverified)
  (test "Governor: effect not :propose check" test-governor-effect-not-propose)
  (test "Governor: scope exclusion (food-safety)" test-governor-scope-exclusion-food-safety)
  (test "Governor: scope exclusion (recipe)" test-governor-scope-exclusion-recipe)
  (test "Governor: flag-safety-concern allowed" test-governor-safety-escalation)
  (test "Governor: full governor decision (pass)" test-governor-happy-path)

  ;; Operation tests
  (test "Operation: appointment proposal (happy path)" test-operation-reservation-proposal)
  (test "Operation: unverified client rejection" test-operation-unverified-rejection)
  (test "Operation: safety concern escalation" test-operation-safety-escalation)

  ;; Phase tests
  (test "Phase: phase 0 (read-only)" test-phase-0-readonly)
  (test "Phase: phase 1 (reservation + status)" test-phase-1-reservation-status)
  (test "Phase: phase 3 (full auto-commit)" test-phase-3-full)

  ;; Print results
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║ ISIC-561 Restaurant Operations Coordination Actor Tests   ║")
  (println "╚════════════════════════════════════════════════════════════╝")
  (println)

  (let [results @test-results
        passed (filter #(= :pass (:status %)) results)
        failed (filter #(= :fail (:status %)) results)]
    (doseq [i (range (count results))]
      (let [result (nth results i)
            icon (if (= :pass (:status result)) "✓" "✗")]
        (println (str "[" (inc i) "] " (:name result) " " icon))))
    (println)
    ;; The message used to be printed unconditionally, so a run with failures
    ;; still announced success. It now follows the actual verdict.
    (println (str (if (empty? failed) "All tests passed!" "TESTS FAILED")
                  " (" (count passed) "/" (count results) ")")))

  ;; The verdict used to be `(empty? @test-results)`, which is inverted: it is
  ;; true only when NO test ran, and false for a fully-passing run. A suite
  ;; that reports failure when everything passes -- and success when nothing
  ;; executed -- cannot gate anything. Correct verdict: results exist and none
  ;; of them failed.
  (let [results @test-results]
    (and (seq results)
         (every? #(= :pass (:status %)) results))))
