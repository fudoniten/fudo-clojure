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

(defmulti level-to-int identity)

(defmethod level-to-int :debug  [_] 0)
(defmethod level-to-int :warn   [_] 1)
(defmethod level-to-int :error  [_] 2)
(defmethod level-to-int :fatal  [_] 3)
(defmethod level-to-int :info   [_] 0)
(defmethod level-to-int :notify [_] 1)
(defmethod level-to-int :alert  [_] 2)

(defn log-to-function
  ([f] (log-to-function f :error :notify))
  ([f error logic]
   (let [error-level (.indexOf error-log-level error)
         logic-level (.indexOf logic-log-level logic)]
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

(defn combine-logs [& loggers]
  (letfn [(log-all [method msg]
            (doseq [logger loggers] (method logger msg)))]
    (reify Logger
      (debug!  [_ msg] (log-all debug! msg))
      (warn!   [_ msg] (log-all warn!  msg))
      (error!  [_ msg] (log-all error! msg))
      (fatal!  [_ msg] (log-all fatal! msg))

      (info!   [_ msg] (log-all info!   msg))
      (notify! [_ msg] (log-all notify! msg))
      (alert!  [_ msg] (log-all alert!   msg)))))
