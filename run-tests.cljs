;; Run the restaurantops actor test suite under nbb.
;;
;;   nbb --classpath src:test run-tests.cljs
;;
;; Exits 1 when any test fails. This script previously discarded
;; `run-tests`'s value, so the process exited 0 regardless -- and the value it
;; discarded was itself inverted (see restaurantops.test).
(require '[restaurantops.test :as test])

(when-not (test/run-tests)
  (js/process.exit 1))
