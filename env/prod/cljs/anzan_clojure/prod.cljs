(ns anzan-clojure.prod
  (:require [anzan-clojure.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
