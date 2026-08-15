(ns restaurantops.test
  "Full test suite for ISIC-561 restaurant operations coordination actor.

  Tests store, governor, operations, and phases."
  (:require [restaurantops.store :as store]
            [restaurantops.governor :as governor]
            [restaurantops.operation :as op]
            [restaurantops.phase :as phase]
            [restaurantops.yotei-intake :as intake]))

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


;; ==================== yotei intake tests ====================
;;
;; The seam between the telephone and this actor. Before it, a reservation could
;; only be coordinated if `demo-data` had invented one, so nothing a caller did
;; ever reached the governor.

(def ^:private confirmed-yoyaku
  {"state" "confirmed" "status" "confirmed"
   "yoyakuId" "y-1"
   "calendarDid" "did:web:app.itonami.cloud:calendar:torikai#table:t4"
   "startEpochMin" 29631000                ; 2026-08-20 19:00 JST, as UTC minutes
   "durationMin" 90
   "partySize" 4
   "confirmedSig" "sig:y-1"
   "confirmedVia" "delegate"
   "consentKind" "telephone-attested"
   "authorizedBy" "yotei/uketsuke/v1\ndelegate=..."})

(defn test-intake-accepts-a-confirmed-yoyaku
  []
  (let [r (intake/record confirmed-yoyaku {:tz-offset-min 540})]
    (assert-equal (:reservation-id r) "y-1" "reservation id is the 予約 id")
    (assert-equal (:table-number r) "t4" "table comes out of the calendar DID")
    (assert-equal (:party-size r) 4 "party size carried")
    (assert-true (:registered? r) "a confirmed 予約 is registered")
    (assert-true (:verified? r) "a confirmed 予約 is verified")))

(defn test-intake-refuses-a-proposal
  []
  (assert-equal (:refused (intake/record (assoc confirmed-yoyaku "state" "proposed") {}))
                :not-confirmed
                "a proposed 予約 is a request, not a reservation"))

(defn test-intake-refuses-a-confirmation-with-no-signature
  []
  (assert-equal (:refused (intake/record (dissoc confirmed-yoyaku "confirmedSig") {}))
                :no-confirming-signature
                "verified? must not be settable by saying 'confirmed'"))

(defn test-intake-refuses-a-calendar-that-is-not-a-table
  []
  (assert-equal (:refused (intake/record (assoc confirmed-yoyaku "calendarDid"
                                                "did:web:app.itonami.cloud:calendar:alice") {}))
                :not-a-table-calendar
                "a person's calendar is not a table"))

(defn test-intake-records-how-far-the-consent-goes
  []
  (let [r (intake/record confirmed-yoyaku {:tz-offset-min 540})]
    (assert-equal (:yotei/confirmed-via r) "delegate" "unattended confirm is recorded as such")
    (assert-equal (:yotei/consent-kind r) "telephone-attested"
                  "an attested consent must not read as a signed one downstream")))

(defn test-intake-lets-the-governor-pass-a-telephone-reservation
  []
  (let [admitted (intake/admit {} confirmed-yoyaku {:tz-offset-min 540})
        s (store/make-store (assoc (store/demo-data) :reservations (:reservations admitted)))]
    (assert-equal (governor/reservation-unverified-violations s :schedule-reservation "y-1")
                  []
                  "a confirmed telephone 予約 is coordinatable")))

(defn test-intake-leaves-the-governor-refusing-an-unconfirmed-one
  []
  (let [refused (intake/admit {} (assoc confirmed-yoyaku "state" "proposed") {})
        s (store/make-store (assoc (store/demo-data) :reservations {}))]
    (assert-true (:refused refused) "nothing was admitted")
    (assert-true (seq (governor/reservation-unverified-violations s :schedule-reservation "y-1"))
                 "and the governor still refuses to coordinate it")))

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

  ;; yotei intake tests
  (test "Intake: accepts a confirmed 予約" test-intake-accepts-a-confirmed-yoyaku)
  (test "Intake: refuses a proposal" test-intake-refuses-a-proposal)
  (test "Intake: refuses a confirmation with no signature" test-intake-refuses-a-confirmation-with-no-signature)
  (test "Intake: refuses a calendar that is not a table" test-intake-refuses-a-calendar-that-is-not-a-table)
  (test "Intake: records how far the consent goes" test-intake-records-how-far-the-consent-goes)
  (test "Intake: governor passes a telephone reservation" test-intake-lets-the-governor-pass-a-telephone-reservation)
  (test "Intake: governor still refuses an unconfirmed one" test-intake-leaves-the-governor-refusing-an-unconfirmed-one)

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
