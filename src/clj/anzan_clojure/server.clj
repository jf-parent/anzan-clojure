(ns anzan-clojure.server
    (:require
     [anzan-clojure.handler :refer [app]]
     [config.core :refer [env]]
     [ring.adapter.jetty :refer [run-jetty]])
    (:gen-class))

(defn -main [& args]
  (let [port (or (env :port) 3111)]
    (run-jetty #'app {:port port :join? false})))
