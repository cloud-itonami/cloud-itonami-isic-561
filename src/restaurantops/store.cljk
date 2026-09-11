(ns restaurantops.store
  "SSoT for the ISIC-561 restaurant/mobile food service administrative coordination actor.

  This actor coordinates the back-office operations of restaurants and mobile food service:
  table/reservation scheduling, order-queue status tracking, non-food supply coordination,
  staff shift proposals, and safety-concern flagging (facility hazards, sanitation issues).

  It NEVER touches food-safety/health-inspection determinations, recipe/menu-content
  decisions, food-handling-technique decisions, or safety-authority overrides
  — see `restaurantops.governor`'s `scope-exclusion-violations`, a HARD, permanent,
  un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `reservations` directory keyed by `:reservation-id` STRING and a
  `supply_inventory` directory keyed by `:supply-id` STRING.

  A registered/verified reservation record must exist before ANY proposal for that
  reservation may ever commit or escalate.")

(defprotocol Store
  (reservation [s reservation-id] "Registered reservation record, or nil.
    Reservation map: {:reservation-id .. :table-number .. :registered? bool :verified? bool}.")
  (all-reservations [s])
  (supply-item [s supply-id] "Supply inventory item record, or nil.")
  (all-supplies [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-reservations [s reservations] "replace/seed the reservation directory")
  (with-supplies [s supplies] "replace/seed the supply inventory directory"))

;; ----------------------------- demo data --------------------------------------

(defn demo-data
  "A small, self-contained reservation and supply inventory covering both the
  happy path and the governor's own hard checks, so the actor + tests run offline."
  []
  {:reservations
   {"res-1" {:reservation-id "res-1" :table-number 5 :registered? true :verified? true
             :party-size 4 :time-slot "19:00"}
    "res-2" {:reservation-id "res-2" :table-number 7 :registered? true :verified? true
             :party-size 2 :time-slot "20:00"}
    "res-3" {:reservation-id "res-3" :table-number 9 :registered? true :verified? false
             :party-size 6 :time-slot "18:30"}}
   :supplies
   {"supply-1" {:supply-id "supply-1" :supply-type "napkins" :name "White Paper Napkins"
                :unit-count 1000}
    "supply-2" {:supply-id "supply-2" :supply-type "cleaning" :name "All-Purpose Cleaner"
                :unit-count 5}
    "supply-3" {:supply-id "supply-3" :supply-type "utensils" :name "Disposable Forks"
                :unit-count 500}}
   :ledger []
   :coordination-log []})

;; ----------------------------- MemStore implementation ----------------------

(deftype MemStore [atom-data]
  Store
  (reservation [_s reservation-id]
    (get-in @atom-data [:reservations reservation-id]))
  (all-reservations [_s]
    (vals (get @atom-data :reservations {})))
  (supply-item [_s supply-id]
    (get-in @atom-data [:supplies supply-id]))
  (all-supplies [_s]
    (vals (get @atom-data :supplies {})))
  (ledger [_s]
    (get @atom-data :ledger []))
  (coordination-log [_s]
    (get @atom-data :coordination-log []))
  (commit-record! [_s record]
    (swap! atom-data update :coordination-log conj record))
  (append-ledger! [_s fact]
    (swap! atom-data update :ledger conj fact))
  (with-reservations [_s reservations]
    (swap! atom-data assoc :reservations reservations)
    _s)
  (with-supplies [_s supplies]
    (swap! atom-data assoc :supplies supplies)
    _s))

(defn make-store
  "Create a fresh MemStore from demo data (or seeded with custom data)."
  ([]
   (make-store (demo-data)))
  ([data]
   (MemStore. (atom data))))
