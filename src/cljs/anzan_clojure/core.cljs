(ns anzan-clojure.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [accountant.core :as accountant]))

(enable-console-print!)

;; -------------------------
;; State

(def number-of-digits (atom 3))
(def number-of-rows (atom 3))
(def number-shown-ms (atom 1000))
(def timeout (atom 1000))
(def ask-answer (atom false))
(def user-answer (atom ""))
(def numbers (atom []))
(def current-number (atom 0))
(def current-config (atom {}))
(def current-row (atom 0))
(def current-answer (atom 0))
(def history-total-question (atom 0))
(def history-total-right-answer (atom 0))
(def last-answer-correct (atom 0))

;; -------------------------
;; Functions

(defn get-number [nb-of-digit]
  (apply str (repeatedly nb-of-digit #(inc (rand-int 8)))))

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
        (reset! ask-answer true))))

;; -------------------------
;; Event handler

(defn start-main-loop []
  (reset! current-answer 0)
  (reset! last-answer-correct 0)
  (reset! numbers [])
  (reset! ask-answer false)
  (reset! user-answer "")
  (reset! current-config {:number-of-digits @number-of-digits
                          :number-of-rows @number-of-rows
                          :timeout (+ @timeout 100)
                          :number-shown-ms @number-shown-ms})
  (main-loop))

(defn validate-answer []
  (swap! history-total-question inc)
  (reset! ask-answer false)
  (if (= (int @user-answer) @current-answer)
    (do (reset! last-answer-correct 1)
        (swap! history-total-right-answer inc))
    (do (reset! last-answer-correct -1))))

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
             :on-change #(reset! number-of-digits (int (-> % .-target .-value)))}]]
   [:p "Number of rows: "
    [:input {:type "number"
             :value @number-of-rows
             :min 2
             :max 100
             :on-change #(reset! number-of-rows (int (-> % .-target .-value)))}]]
   [:p "Show number for ms: "
    [:input {:type "number"
             :value @number-shown-ms
             :min 500
             :max 5000
             :step 500
             :on-change #(reset! number-shown-ms (int (-> % .-target .-value)))}]]
   [:p "Timeout: "
    [:input {:type "number"
             :value @timeout
             :min 500
             :max 5000
             :step 500
             :on-change #(reset! timeout (int (-> % .-target .-value)))}]]])

(defn home-page []
  (fn []
    [:span.main
     [:h1.title "ANZAN"]
     [number-component]
     [:div
      [:input {:type "button" :value (if @ask-answer "Skip" "Start") :on-click start-main-loop}]]
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
