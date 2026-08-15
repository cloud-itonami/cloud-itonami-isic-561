(ns restaurantops.yotei-intake
  "Where a telephone 予約 becomes a reservation this actor may coordinate.

  ## The gap this closes

  `restaurantops.governor`'s first hard check re-derives, from the store, that a
  reservation is `:registered?` and `:verified?` before any proposal about it may
  commit. Until now nothing ever wrote such a record except `store/demo-data` —
  the actor could coordinate a reservation only if a reservation had already been
  invented for it. Meanwhile `cloud-itonami/denwaban` answers the telephone and
  `cloud-itonami/yotei` holds the 予約, and neither reached this actor.

  ## Verified means confirmed by yotei, and nothing else

  The temptation is to accept a 予約 map and trust its `status`. That would make
  `:verified? true` a claim rather than a fact, and the governor's check — which
  exists precisely so a proposal cannot self-report its own legitimacy — would be
  re-derived from a lie. So `record` refuses anything that is not `confirmed`
  **and** carrying the signature that confirmed it. A 予約 that is merely
  proposed is not a reservation here; it is a request somebody may yet decline.

  ## No dependency on yotei

  This reads yotei's wire shape (string keys, the shape its EAVT log stores) and
  makes no decision yotei makes: it does not choose a table, permit a time or
  confirm anything. Reading two fields is not a second implementation, and a
  dependency for it would pull a Worker's toolchain into a dependency-free actor."
  (:require [clojure.string :as str]))

(def ^:private table-fragment "#table:")

(defn table-of
  "The table id out of the 予約's calendar DID.

  yotei models a table as a DID fragment (`…:calendar:torikai#table:t4`), so the
  table a 予約 is on is carried by the identity of the calendar it is on rather
  than as a field anyone could set independently."
  [calendar-did]
  (let [i (str/index-of (str calendar-did) table-fragment)]
    (when i (subs calendar-did (+ i (count table-fragment))))))

(defn- hhmm [tz-offset-min epoch-min]
  (let [m (mod (+ epoch-min tz-offset-min) 1440)
        h (quot m 60)
        mm (mod m 60)]
    (str (when (< h 10) "0") h ":" (when (< mm 10) "0") mm)))

(defn record
  "A store reservation record from a confirmed yotei 予約, or a refusal.

  `:registered?` and `:verified?` are set from what the 予約 actually is, never
  passed in. There is deliberately no argument by which a caller can assert
  either one."
  [yoyaku {:keys [tz-offset-min] :or {tz-offset-min 0}}]
  (let [state (get yoyaku "state")
        sig (get yoyaku "confirmedSig")
        table (table-of (get yoyaku "calendarDid"))]
    (cond
      (not= "confirmed" state)
      {:refused :not-confirmed :state state}

      (not (seq (str sig)))
      {:refused :no-confirming-signature}

      (nil? table)
      {:refused :not-a-table-calendar :calendar-did (get yoyaku "calendarDid")}

      :else
      {:reservation-id (get yoyaku "yoyakuId")
       :table-number table
       :registered? true
       :verified? true
       :party-size (get yoyaku "partySize")
       :time-slot (hhmm tz-offset-min (get yoyaku "startEpochMin"))
       ;; Kept so this actor's ledger can always name what made the reservation
       ;; real, and how far the consent behind it actually goes. A telephone
       ;; consent is attested, not DID-signed (denwaban G9); recording that here
       ;; is what stops it from being read as the stronger thing three systems
       ;; downstream.
       :yotei/confirmed-via (get yoyaku "confirmedVia" "member")
       :yotei/consent-kind (get yoyaku "consentKind")
       :yotei/authorized-by (get yoyaku "authorizedBy")})))

(defn admit
  "Fold a confirmed 予約 into the store, or say why not.

  Returns `{:store s :record r}` on success. The store write is the caller's
  ordinary `with-reservations`, so nothing here needs privileges the actor does
  not already have."
  [reservations yoyaku opts]
  (let [r (record yoyaku opts)]
    (if (:refused r)
      r
      {:reservations (assoc reservations (:reservation-id r) r)
       :record r})))
