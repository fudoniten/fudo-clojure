(ns fudo-clojure.logging
  (:require [clojure.spec.alpha :as s]))

(defprotocol Logger
  ;; For program functionality
  (debug!  [self msg])
  (warn!   [self msg])
  (error!  [self msg])
  (fatal!  [self msg])

  ;; For business logic
  (info!   [self msg])
  (notify! [self msg])
  (alert!  [self msg]))

(def logger? (partial satisfies? Logger))

(s/def ::logger logger?)

(defn dummy-logger []
  (reify Logger
    (debug!  [_ _] nil)
    (warn!   [_ _] nil)
    (error!  [_ _] nil)
    (fatal!  [_ _] nil)

    (info!   [_ _] nil)
    (notify! [_ _] nil)
    (alert!  [_ _] nil)))

(def error-log-level [:debug :warn :error :fatal])
(def logic-log-level [:info :notify :alert])

(defn log-to-function
  ([f] (log-to-function f :error :notify))
  ([f error logic]
   (let [error-level (.indexOf error-log-level error)
         logic-level (.IndexOf logic-log-level logic)]
     (reify Logger
       (debug!  [_ msg] (when (>= error-level 0) (f msg)))
       (warn!   [_ msg] (when (>= error-level 1) (f msg)))
       (error!  [_ msg] (when (>= error-level 2) (f msg)))
       (fatal!  [_ msg] (when (>= error-level 3) (f msg)))

       (info!   [_ msg] (when (>= logic-level 0) (f msg)))
       (notify! [_ msg] (when (>= logic-level 1) (f msg)))
       (alert!  [_ msg] (when (>= logic-level 2) (f msg)))))))

(defn print-logger
  ([]            (print-logger :error :notify))
  ([error logic] (log-to-function println error logic)))
