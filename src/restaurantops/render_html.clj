(ns restaurantops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for `cloud-itonami-isic-561`: this repo had
  NO demo page and no generator at all. This namespace drives the REAL actor
  stack -- `restaurantops.store` -> `restaurantops.advisor` ->
  `restaurantops.governor` -> `restaurantops.operation/run-proposal` -- and
  renders the page from what that stack actually returned. There is no
  hand-written HTML table content here: every id, number, decision, action and
  violation string on the page is read back out of the real store ledger or the
  real `run-proposal` return values.

  This repo has NO langgraph/StateGraph wiring (its `deps.edn` carries no
  langgraph dependency and `restaurantops.operation` is a plain function
  pipeline despite its docstring claiming a StateGraph), so the entry point
  used here is the repo's own `restaurantops.operation/run-proposal`.

  Determinism: no wall-clock value reaches the page. The actor DOES stamp
  `System/currentTimeMillis` into every ledger fact and every committed record
  (`restaurantops.operation/commit`, `/escalate`, `/hold`) with no injection
  point for a clock, so those timestamps are deliberately NOT rendered --
  see `docs/samples/README` note in the generated page footer. Directory
  listings are sorted by id rather than taken in map order, so the output is
  byte-identical across reruns from the same seed.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [jp-go-dds.skin]
            [restaurantops.store :as store]
            [restaurantops.operation :as op]
            [restaurantops.governor :as governor]
            [restaurantops.phase :as phase]))

;; ----------------------------- scenario -----------------------------

(defn seed-data
  "This repo's own `restaurantops.store/demo-data`, plus ONE extra reservation.

  `demo-data` seeds res-1/res-2 (registered+verified) and res-3
  (registered, NOT verified). It has no record that is registered? false, so
  the governor's `Reservation is not registered` branch is unreachable from
  the stock seed. `res-4` exists purely to exercise that branch; it is a real
  store record in the real record shape, and it is rendered on the page like
  any other."
  []
  (assoc-in (store/demo-data) [:reservations "res-4"]
            {:reservation-id "res-4" :table-number 11 :registered? false :verified? false
             :party-size 2 :time-slot "21:00"}))

(def scenario
  "Requests fed to the REAL actor, chosen to reach every hard rule the
  governor actually implements plus every clean disposition it can reach.

  Clean paths (governor :APPROVE):
    1-4  the four non-safety ops
    5    `:flag-safety-concern`, which always escalates to a human

  HARD holds (governor :REJECT, never reaches a human):
    6    check 1 / `Reservation is not verified`      (res-3)
    7    check 1 / `Reservation is not registered`    (res-4)
    8    check 1 / `Reservation not found in store`   (res-99)
    9    check 1 / `Reservation ID required but not provided`
    10   check 3 / scope exclusion, EN pattern, on an otherwise-clean op
    11   check 3 / scope exclusion, JA pattern, on a facility-level op
    12   checks 1+2+3 together -- an op outside the closed allowlist. This is
         the ONLY route by which check 2 (`effect-not-propose`) is reachable
         through the real pipeline, because `restaurantops.advisor` hardcodes
         `:effect :propose` on every proposal it builds; the unknown-op branch
         returns `{:status :error ...}`, which carries no `:effect` at all."
  [{:n 1 :note "table reservation for a verified booking"
    :request {:operation :schedule-reservation
              :reservation-id "res-1" :table-number 5 :party-size 4}}
   {:n 2 :note "order-queue status tracking"
    :request {:operation :coordinate-order-status-update
              :reservation-id "res-2" :order-id "order-2001"
              :status-type "ready-for-service"}}
   {:n 3 :note "non-food consumables"
    :request {:operation :coordinate-supply-request
              :supply-type "napkins" :quantity 2000
              :requested-delivery-date "2026-07-20"}}
   {:n 4 :note "administrative shift proposal (non-binding)"
    :request {:operation :schedule-staff-shift-proposal
              :staff-id "staff-042" :shift-date "2026-07-18" :shift-type "evening"}}
   {:n 5 :note "facility hazard - always escalates to a human"
    :request {:operation :flag-safety-concern
              :concern-type "sanitation"
              :description "Drain back-up in kitchen area" :severity "high"}}
   {:n 6 :note "booking exists and is registered, but is not verified"
    :request {:operation :schedule-reservation
              :reservation-id "res-3" :table-number 9 :party-size 6}}
   {:n 7 :note "booking exists but was never registered"
    :request {:operation :schedule-reservation
              :reservation-id "res-4" :table-number 11 :party-size 2}}
   {:n 8 :note "booking id is not in the store at all"
    :request {:operation :coordinate-order-status-update
              :reservation-id "res-99" :order-id "order-9001"
              :status-type "seated"}}
   {:n 9 :note "reservation-scoped op with no booking id"
    :request {:operation :schedule-reservation
              :reservation-id nil :table-number 3 :party-size 2}}
   {:n 10 :note "status update that smuggles in a food-safety determination"
    :request {:operation :coordinate-order-status-update
              :reservation-id "res-1" :order-id "order-2002"
              :status-type "blocked-pending-food-safety-signoff"}}
   {:n 11 :note "supply request that smuggles in recipe/menu content (JA)"
    :request {:operation :coordinate-supply-request
              :supply-type "レシピ印刷用カード" :quantity 200
              :requested-delivery-date "2026-07-22"}}
   {:n 12 :note "op outside the closed allowlist"
    :request {:operation :override-health-inspection
              :reservation-id "res-1"}}])

(defn run-demo!
  "Runs the scenario through the REAL actor against a fresh seeded store.

  Returns `{:store <store> :runs [{:n .. :note .. :request .. :result ..}]}`
  where every `:result` is the untouched return value of
  `restaurantops.operation/run-proposal`."
  []
  (let [s (store/make-store (seed-data))
        runs (mapv (fn [{:keys [n note request]}]
                     {:n n :note note :request request
                      :result (op/run-proposal s request)})
                   scenario)]
    {:store s :runs runs}))

;; ----------------------------- derived facts -----------------------------

(defn hold-facts
  "Ledger facts the governor HARD-held.

  `restaurantops.operation/hold` is this repo's `:governor-hold`: it is the
  only writer that attaches `:violations`, and it stamps `:status :held`."
  [s]
  (filterv #(= :held (:status %)) (store/ledger s)))

(defn escalated-facts [s]
  (filterv #(= :escalated (:status %)) (store/ledger s)))

(defn approved-runs
  "Runs the governor let through (decision :APPROVE)."
  [runs]
  (filterv #(= :APPROVE (get-in % [:result :governance :decision])) runs))

(defn observed-action-by-op
  "op-id -> the action `run-proposal` actually took, for runs the governor
  approved. Used to compare the phase ladder against reality instead of
  asserting in prose what the phase ladder does."
  [runs]
  (reduce (fn [m {:keys [result]}]
            (if (= :APPROVE (get-in result [:governance :decision]))
              (assoc m (get-in result [:proposal :operation]) (:action result))
              m))
          {}
          runs))

(defn violations-by-check
  "check/id -> sorted distinct violation messages, taken from the real holds."
  [s]
  (->> (hold-facts s)
       (mapcat :violations)
       (reduce (fn [m {:check/keys [id] :keys [violation]}]
                 (update m id (fnil conj #{}) violation))
               {})))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw [v] (if (keyword? v) (name v) (str v)))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- subject-of
  "Best available subject label for a request, read from the request itself."
  [request]
  (or (:reservation-id request)
      (:supply-type request)
      (:staff-id request)
      (:concern-type request)
      "—"))

(defn- disposition-cell [{:keys [governance action]}]
  (cond
    (= :REJECT (:decision governance))
    "<span class=\"critical\">HARD hold · no override path</span>"

    (= :escalated action)
    "<span class=\"warn\">escalated to human review</span>"

    (= :pending-approval action)
    "<span class=\"ok\">approved by governor · awaiting human sign-off</span>"

    (= :committed action)
    "<span class=\"ok\">auto-committed</span>"

    :else (str "<span class=\"muted\">" (esc (kw action)) "</span>")))

(defn- violations-cell [violations]
  (if (empty? violations)
    "<span class=\"muted\">—</span>"
    (str/join "<br>"
              (map (fn [{:check/keys [id] :keys [violation]}]
                     (str (code (str ":" (kw id))) " " (esc violation)))
                   violations))))

(defn- run-row [{:keys [n request result]}]
  (let [{:keys [proposal governance action]} result]
    (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
            n
            (code (str ":" (kw (:operation request))))
            (esc (subject-of request))
            (esc (or (:confidence proposal) "—"))
            (disposition-cell result)
            (esc (kw action))
            (violations-cell (:violations governance)))
    ;; `note` is scenario framing, not actor output, so it is rendered in its
    ;; own column below rather than mixed into any decision column.
    ))

(defn- run-note-row [{:keys [n note]}]
  (format "        <tr><td>%s</td><td>%s</td></tr>" n (esc note)))

(defn- reservation-row [{:keys [reservation-id table-number party-size time-slot
                                registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc reservation-id) (esc table-number) (esc party-size) (esc time-slot)
          (cond
            (and registered? verified?) "<span class=\"ok\">registered &amp; verified</span>"
            registered? "<span class=\"critical\">registered, NOT verified</span>"
            :else "<span class=\"critical\">NOT registered</span>")))

(defn- supply-row [{:keys [supply-id supply-type name unit-count]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc supply-id) (esc supply-type) (esc name) (esc unit-count)))

(defn- allowlist-rows
  "Derived from `restaurantops.governor/allowed-ops` and
  `/facility-level-ops` -- the vars the checks themselves read."
  []
  (map (fn [op-id]
         (format "        <tr><td>%s</td><td>%s</td></tr>"
                 (code (str ":" (kw op-id)))
                 (if (governor/facility-level-ops op-id)
                   "<span class=\"muted\">facility-level · skips the reservation check</span>"
                   "<span class=\"ok\">reservation must be registered &amp; verified</span>")))
       (sort (seq governor/allowed-ops))))

(defn- check-rows
  "Derived from the violations the governor actually emitted in this run."
  [s]
  (let [by-check (violations-by-check s)]
    (map (fn [[check-id msgs]]
           (format "        <tr><td>%s</td><td>%s</td><td>%s</td></tr>"
                   (code (str ":" (kw check-id)))
                   (count (filter (fn [f] (some #(= check-id (:check/id %)) (:violations f)))
                                  (hold-facts s)))
                   (str/join "<br>" (map esc (sort msgs)))))
         (sort-by (comp kw key) by-check))))

(defn- phase-rows
  "Derived from `restaurantops.phase/phase-config` plus the actions actually
  observed in this run -- so a phase ladder that is declared but not wired in
  shows up as a measurement, not as prose."
  [observed]
  (map (fn [[p {:keys [name auto-commit-ops]}]]
         (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
                 p (esc name)
                 (if (empty? auto-commit-ops)
                   "<span class=\"muted\">none</span>"
                   (str/join " " (map #(code (str ":" (kw %))) (sort (seq auto-commit-ops)))))
                 (if (empty? auto-commit-ops)
                   "<span class=\"muted\">—</span>"
                   (str/join "<br>"
                             (map (fn [op-id]
                                    (str (code (str ":" (kw op-id))) " &rarr; "
                                         (if-let [a (observed op-id)]
                                           (str "<code>" (esc (kw a)) "</code>")
                                           "<span class=\"muted\">not exercised</span>")))
                                  (sort (seq auto-commit-ops)))))))
       (sort-by key phase/phase-config)))

(defn render
  "Renders the whole document from a real `run-demo!` result. Every cell below
  is derived from `db`/`runs`; nothing is a hand-typed decision value."
  [{:keys [store runs]}]
  (let [s store
        holds (hold-facts s)
        escalated (escalated-facts s)
        approved (approved-runs runs)
        observed (observed-action-by-op runs)
        commits (store/coordination-log s)
        reservations (sort-by :reservation-id (store/all-reservations s))
        supplies (sort-by :supply-id (store/all-supplies s))
        phase3-auto (get-in phase/phase-config [3 :auto-commit-ops])
        phase-wired? (boolean (some #(= :committed (observed %)) phase3-auto))]
    (str
     "<!doctype html>\n"
     "<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-561 · restaurant &amp; mobile food service coordination</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Restaurants &amp; mobile food service (ISIC 561) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · food-safety, menu/recipe and food-handling decisions are permanently out of scope</span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>This run</h2>\n"
     "    <p class=\"muted\">Build-time generated by <code>restaurantops.render-html</code> (<code>clojure -M:dev:render-html</code>) by executing "
     (count runs) " requests through the real <code>restaurantops.operation/run-proposal</code> pipeline against a freshly seeded <code>restaurantops.store</code>. "
     "Governor approved " (count approved) ", HARD-held " (count holds) ", escalated " (count escalated)
     ", auto-committed " (count commits) ".</p>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Proposals put to the governor</h2>\n"
     "    <table>\n"
     "      <thead><tr><th>#</th><th>Op</th><th>Subject</th><th>Advisor confidence</th><th>Disposition</th><th>Action</th><th>Violations (verbatim)</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map run-row runs)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "    <h3>What each request was</h3>\n"
     "    <table>\n"
     "      <thead><tr><th>#</th><th>Scenario</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map run-note-row scenario)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Hard checks that actually fired</h2>\n"
     "    <p class=\"muted\">Rows below are grouped from the <code>:violations</code> the governor emitted in this run — not from a description of the rules. A HARD hold has no override path and never reaches a human.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Check</th><th>Holds in this run</th><th>Violation messages emitted</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (check-rows s)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Closed allowlist</h2>\n"
     "    <p class=\"muted\">Read from <code>restaurantops.governor/allowed-ops</code> and <code>/facility-level-ops</code> — the same vars <code>scope-exclusion-violations</code> and <code>reservation-unverified-violations</code> read. Anything not listed is permanently blocked. Proposal content is additionally scanned against "
     (count governor/forbidden-patterns)
     " excluded-territory patterns (English + Japanese); <code>:flag-safety-concern</code> is exempt from that scan so a real safety escalation is not self-blocked.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Reservation gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (allowlist-rows)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Rollout phase ladder vs. observed behaviour</h2>\n"
     "    <p class=\"muted\">Left two columns are read from <code>restaurantops.phase/phase-config</code>; the right column is what <code>run-proposal</code> actually did with those ops in this run.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Phase</th><th>Name</th><th>Declared auto-commit ops</th><th>Observed action this run</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (phase-rows observed)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     (if phase-wired?
       "    <p class=\"ok\">The phase ladder is wired in: at least one declared auto-commit op reached <code>:committed</code>.</p>\n"
       (str "    <p class=\"critical\">Measured defect: the phase ladder is declared but NOT wired in. "
            "Every op the phase-3 config declares auto-committable was observed as something other than <code>:committed</code>, and the committed-proposal log holds "
            (count commits) " records after " (count runs)
            " requests. <code>restaurantops.operation/decide</code> never consults <code>restaurantops.phase</code>, so <code>phase/gate-op</code>, <code>phase/can-auto-commit?</code> and <code>operation/commit</code> are unreachable from <code>run-proposal</code>.</p>\n"))
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Reservation directory</h2>\n"
     "    <p class=\"muted\">Read back out of the seeded store after the run. Registration and verification are re-derived from here on every proposal — a proposal's own claim about its booking is never trusted.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Reservation</th><th>Table</th><th>Party</th><th>Slot</th><th>Standing</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map reservation-row reservations)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Non-food supply inventory</h2>\n"
     "    <table>\n"
     "      <thead><tr><th>Item</th><th>Type</th><th>Name</th><th>Units</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map supply-row supplies)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log, in append order. "
     "Only holds and escalations are written to it: a proposal the governor approves but that stops at <code>:pending-approval</code> leaves no ledger entry at all, so the ledger under-reports approvals by design of the current actor. "
     "Ledger facts carry a <code>System/currentTimeMillis</code> stamp with no clock injection point; those stamps are omitted here so this page stays byte-identical across reruns.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>#</th><th>Status</th><th>Op</th><th>Subject</th><th>Violations</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n"
               (map-indexed
                (fn [i {:keys [status proposal violations]}]
                  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
                          (inc i)
                          (if (= :held status)
                            "<span class=\"critical\">held</span>"
                            (str "<span class=\"warn\">" (esc (kw status)) "</span>"))
                          (if-let [o (:operation proposal)]
                            (code (str ":" (kw o)))
                            "<span class=\"muted\">unknown op</span>")
                          (esc (or (:reservation-id proposal) (:supply-type proposal)
                                   (:concern-type proposal) "—"))
                          (violations-cell violations)))
                (store/ledger s)))
     "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "<footer>\n"
     "  <p class=\"muted\">Generated from source by <code>restaurantops.render-html</code>. No value on this page was typed by hand; regenerate with <code>clojure -M:dev:render-html</code>.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [store runs] :as demo} (run-demo!)
        holds (hold-facts store)]
    ;; Build-time invariant, not a convention: a console that shows no HARD
    ;; hold is not evidence that the governor is load-bearing. Refuse to write.
    (when (empty? holds)
      (throw (ex-info
              (str "refusing to write " out
                   ": the scenario produced ZERO governor holds, so this page would "
                   "not demonstrate that the governor blocks anything. Ledger size: "
                   (count (store/ledger store)) ".")
              {:out out
               :holds 0
               :ledger-size (count (store/ledger store))
               :runs (count runs)})))
    (io/make-parents out)
    (spit out (render demo))
    (println "wrote" out
             "(" (count runs) "requests,"
             (count (approved-runs runs)) "governor-approved,"
             (count holds) "HARD holds,"
             (count (escalated-facts store)) "escalated,"
             (count (store/coordination-log store)) "auto-committed )")))
