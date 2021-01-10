(ns anzan-clojure.core
  (:require
   [reagent.core :as reagent]
   [reagent.dom :as rdom]
   [alandipert.storage-atom :refer [local-storage]]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [accountant.core :as accountant]))

;; (enable-console-print!)

;; -------------------------
;; Local storage

(def current-config (local-storage (atom {}) :current-config))

;; -------------------------
;; State

(def number-of-digits (reagent/atom (:number-of-digits @current-config 3)))
(def number-of-rows (reagent/atom (:number-of-rows @current-config 3)))
(def number-shown-ms (reagent/atom (:number-shown-ms @current-config 1000)))
(def timeout (reagent/atom (:timeout @current-config 1000)))
(def ask-answer (reagent/atom false))
(def user-answer (reagent/atom ""))
(def numbers (reagent/atom []))
(def current-number (reagent/atom 0))
(def current-row (reagent/atom 0))
(def current-answer (reagent/atom 0))
(def history-total-question (reagent/atom 0))
(def history-total-right-answer (reagent/atom 0))
(def last-answer-correct (reagent/atom 0))
(def running (reagent/atom false))

;; -------------------------
;; Functions

(defn get-number [nb-of-digit]
  (if (< @current-row (count @numbers))
    (nth @numbers @current-row)
    (apply str (repeatedly nb-of-digit #(inc (rand-int 9))))))

(defn hide-number []
  (reset! current-number 0))

(defn main-loop []
  (if (< @current-row (@current-config :number-of-rows))
    (do (reset! current-number (get-number (@current-config :number-of-digits)))
        (swap! current-row inc)
        (reset! current-answer (+ (int @current-answer) (int @current-number)))
        (swap! numbers conj @current-number)
        (js/setTimeout hide-number (@current-config :number-shown-ms))
        (js/setTimeout main-loop (@current-config :timeout)))
    (do (reset! current-row 0)
        (reset! last-answer-correct 0)
        (reset! running false)
        (reset! ask-answer true))))

;; -------------------------
;; Event handler

(defn retry-main-loop []
  (reset! current-answer 0)
  (reset! last-answer-correct 0)
  (reset! running true)
  (reset! ask-answer false)
  (reset! user-answer "")
  (main-loop))

(defn start-main-loop []
  (reset! current-answer 0)
  (reset! last-answer-correct 0)
  (reset! running true)
  (reset! numbers [])
  (reset! ask-answer false)
  (reset! user-answer "")
  (main-loop))

(defn validate-answer []
  (swap! history-total-question inc)
  (reset! ask-answer false)
  (if (= (int @user-answer) @current-answer)
    (do (reset! last-answer-correct 1)
        (swap! history-total-right-answer inc))
    (do (reset! last-answer-correct -1))))

(defn global-handler [e]
  (when (= "Enter" (.-code e))
    (when-not (or @running @ask-answer)
      (js/console.log e)
      (start-main-loop))))

(js/document.addEventListener "keydown" global-handler)

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -------------------------
;; Page components

(defn number-component []
  [:div
   (if @ask-answer
     [:input {:type "text"
              :auto-focus true
              :placeholder "answer"
              :value @user-answer
              :on-key-press (fn [e]
                              (when (= 13 (.-charCode e))
                                (validate-answer)))
              :on-change #(reset! user-answer (-> % .-target .-value))}]
     (if (not= @current-number 0)
       [:span.number @current-number]
       [:span.number "..."]))])

(defn history-component []
  [:div
   (when (= @last-answer-correct 1)
     [:h3.right "Correct!"])
   (when (= @last-answer-correct -1)
     [:div
      [:h3.wrong "Incorrect!"]
      [:h3 (str (apply str (interpose " + " @numbers)) " = " @current-answer)]])
   [:hr.separator]
   (when (> @history-total-question 0)
     [:h2 (str @history-total-right-answer " / " @history-total-question)])])

(defn config-component []
  [:div.config
   [:h3 "Config"]
   [:p "Number of digits: "
    [:input {:type "number"
             :value @number-of-digits
             :min 1
             :max 10
             :on-change #(do (reset! number-of-digits (int (-> % .-target .-value)))
                             (swap! current-config assoc :number-of-digits @number-of-digits))}]]
   [:p "Number of rows: "
    [:input {:type "number"
             :value @number-of-rows
             :min 2
             :max 100
             :on-change #(do (reset! number-of-rows (int (-> % .-target .-value)))
                             (swap! current-config assoc :number-of-rows @number-of-rows))}]]
   [:p "Show number for n ms: "
    [:input {:type "number"
             :value @number-shown-ms
             :min 500
             :max 5000
             :step 500
             :on-change #(do (reset! number-shown-ms (int (-> % .-target .-value)))
                             (swap! current-config assoc :number-shown-ms @number-shown-ms))}]]
   [:p "Timeout: "
    [:input {:type "number"
             :value @timeout
             :min 500
             :max 5000
             :step 500
             :on-change #(do (reset! timeout (int (-> % .-target .-value)))
                             (swap! current-config assoc :timeout @timeout))}]]])

(defn home-page []
  (fn []
    [:span.main
     [:h1.title "ANZAN"]
     [number-component]
     (when (not @running)
       [:div
        [:input {:type "button" :value (if @ask-answer "Skip" "Start") :on-click start-main-loop}]
        (when (not-empty @numbers)
          [:input {:type "button" :value "Retry" :on-click retry-main-loop}])])
     [history-component]
     [config-component]]))

;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page))

;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header]
       [page]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (rdom/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
