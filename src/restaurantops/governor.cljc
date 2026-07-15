(ns restaurantops.governor
  "Governor with three HARD, permanent, un-overridable checks for the
  restaurant/mobile food service coordination actor.

  1. Reservation-record unverified — target must exist in store AND be
     independently :registered?/:verified?, re-derived every time.
  2. Effect not :propose — rejected outright.
  3. Scope exclusion — any proposal touching food-safety/health-inspection
     determinations, recipe/menu-content decisions, food-handling-technique
     decisions, or safety-authority overrides is permanently blocked.

     ALLOWED (closed allowlist):
     - :schedule-reservation — table/reservation scheduling logistics only
     - :coordinate-order-status-update — admin order-queue status tracking only
     - :coordinate-supply-request — non-food consumables only
     - :schedule-staff-shift-proposal — admin shift proposal only
     - :flag-safety-concern — facility/sanitation/safety escalation only"
  (:require [restaurantops.store :as store]
            [clojure.string :as str]))

;; ---------------------- hard checks ----------------------

(defn reservation-unverified-violations
  "Check 1: Reservation must be registered AND verified.
  This is re-derived from the store, never from proposal self-report.

  Exception: :flag-safety-concern doesn't require reservation verification
  (it's a facility-level concern that escalates to human review)."
  [s op-id reservation-id]
  ;; Safety concerns don't need reservation verification (facility-level)
  (if (= op-id :flag-safety-concern)
    []
    ;; All other operations require reservation verification
    (let [res (store/reservation s reservation-id)]
      (cond
        (nil? res)
        [{:check/id :reservation-unverified
          :violation "Reservation not found in store"}]

        (not (:registered? res))
        [{:check/id :reservation-unverified
          :violation "Reservation is not registered"}]

        (not (:verified? res))
        [{:check/id :reservation-unverified
          :violation "Reservation is not verified"}]

        :else
        []))))

(defn effect-not-propose-violations
  "Check 2: Effect must be :propose. Any other effect is rejected outright."
  [proposal]
  (if (not= (:effect proposal) :propose)
    [{:check/id :effect-not-propose
      :violation (str "Effect is " (:effect proposal) ", not :propose")}]
    []))

(defn scope-exclusion-violations
  "Check 3: Block proposals touching excluded territory.

  EXCLUDED (never allowed):
  - food-safety/health-inspection determinations
  - recipe/menu-content decisions
  - food-handling-technique decisions
  - safety-authority overrides

  ALLOWED (closed allowlist):
  - :schedule-reservation — table/reservation scheduling logistics
  - :coordinate-order-status-update — administrative order-queue status tracking
  - :coordinate-supply-request — non-food consumables
  - :schedule-staff-shift-proposal — administrative shift proposals
  - :flag-safety-concern — facility/sanitation/safety escalation

  Uses qualified substring scan (EN+JA) so legitimate :flag-safety-concern
  ops that mention 'safety' aren't self-blocked."
  [proposal]
  (let [forbidden-patterns
        [;; EN patterns for food-safety / health-inspection / recipe / technique territory
         #"(?i)food.*safety"
         #"(?i)health.*inspection"
         #"(?i)health.*code"
         #"(?i)health.*department"
         #"(?i)sanitation.*certification"
         #"(?i)sanitation.*inspection"
         #"(?i)health.*authority"
         #"(?i)safety.*authority"
         #"(?i)compliance.*override"
         #"(?i)menu.*decision"
         #"(?i)menu.*design"
         #"(?i)recipe.*change"
         #"(?i)food.*handling"
         #"(?i)cooking.*technique"
         #"(?i)food.*preparation"
         #"(?i)ingredient.*selection"
         #"(?i)kitchen.*protocol"
         #"(?i)food.*quality"
         #"(?i)allergen.*determination"
         #"(?i)nutritional.*standard"
         ;; JA patterns
         #"食品安全"
         #"健康.?検査"
         #"健康.?基準"
         #"衛生.?認可"
         #"衛生.?監督"
         #"保健.?監督"
         #"メニュー"
         #"レシピ"
         #"調理法"
         #"食品.?扱い"
         #"食材.?選択"
         #"厨房.?規約"
         #"食品.?品質"
         #"アレルゲン"
         #"栄養.?基準"]

        ;; Allowed operations
        allowed-ops #{:schedule-reservation
                      :coordinate-order-status-update
                      :coordinate-supply-request
                      :schedule-staff-shift-proposal
                      :flag-safety-concern}

        op-id (:operation proposal)
        proposal-str (str proposal)

        ;; Check 1: Operation must be in allowed list
        op-not-allowed (not (allowed-ops op-id))

        ;; Check 2: Content must not contain forbidden patterns
        ;; (except :flag-safety-concern which is allowed to escalate)
        content-forbidden (and (not= op-id :flag-safety-concern)
                               (some #(re-find % proposal-str) forbidden-patterns))

        ;; Combine EN+JA checks into a single explicit boolean
        in-forbidden-territory (or op-not-allowed content-forbidden)]

    (if in-forbidden-territory
      [{:check/id :scope-exclusion
        :violation "Proposal touches food-safety/health-inspection, recipe/menu-content, food-handling-technique, or safety-authority overrides"}]
      [])))

;; ---------------------- decision logic ----------------------

(defn govern
  "Apply all three HARD checks. Any violation is a permanent rejection
  with no override path."
  [s proposal]
  (let [client-violations (reservation-unverified-violations s (:operation proposal) (:reservation-id proposal))
        effect-violations (effect-not-propose-violations proposal)
        scope-violations (scope-exclusion-violations proposal)
        all-violations (concat client-violations effect-violations scope-violations)]

    {:proposal proposal
     :violations all-violations
     :passes? (empty? all-violations)
     :decision (if (empty? all-violations)
                 :APPROVE
                 :REJECT)}))
