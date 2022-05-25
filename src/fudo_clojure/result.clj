(ns fudo-clojure.result
  (:refer-clojure :exclude [get])
  (:require [clojure.spec.alpha :as s]))

(defprotocol Result
  (success? [self])
  (failure? [self])
  (bind [self f])
  (map-success [self f])
  (apply [self f])
  (unwrap [self]))

(defprotocol ResultError
  (error-message [self])
  (get-exception [self])
  (exception? [self]))

(defn- is-not [spec]
  (fn [o] (not (s/valid? spec o))))

(s/def ::result (partial satisfies? Result))
(s/def ::failure (every-pred (partial satisfies? Result)
                             (partial satisfies? ResultError)))
(s/def ::success (s/and ::result (is-not ::failure)))

(s/def ::exception (partial instance? java.lang.Exception))

(s/fdef success?
  :args (s/cat :self ::result)
  :ret  boolean?)

(s/fdef failure?
  :args (s/cat :self ::result)
  :ret  boolean?)

(s/fdef bind
  :args (s/cat :self ::result :f (s/fspec :args any? :ret ::result))
  :ret  ::result)

(s/fdef map-success
  :args (s/cat :self ::result :f (s/fspec :args any? :ret any?))
  :ret  ::result)

(s/fdef message
  :args (s/cat :self ::failure)
  :ret  string?)

(s/fdef exception?
  :args (s/cat :self ::failure)
  :ret  boolean?)

(s/fdef get-exception
  :args (s/cat :self ::failure)
  :ret  ::exception)

(defrecord ExceptionFailure [e]
  Result
  (failure? [_] true)
  (success? [_] false)
  (map-success [self _] self)
  (bind [self _] self)
  (unwrap [_] (throw e))
  (send [_ _] nil)

  ResultError
  (error-message [_] (.getMessage e))
  (get-exception [_] e)
  (exception? [_] true))

(defn exception-failure [e]
  (->ExceptionFailure e))

(defrecord Failure [msg context]
  Result
  (failure? [_] true)
  (success? [_] false)
  (map-success [self _] self)
  (bind [self _] self)
  (unwrap [_] (throw (ex-info msg context)))
  (send [_ _] nil)

  ResultError
  (error-message [_] msg)
  (get-exception [_] nil)
  (exception? [_] false))

(defn failure
  ([msg] (failure msg {}))
  ([msg context] (->Failure msg context)))

(defmacro ^:private catching-errors [& args]
  `(try (do ~@args)
        (catch java.lang.RuntimeException e#
          (exception-failure e#))))

(defrecord Success [val]
  Result
  (success? [_] true)
  (failure? [_] false)
  (map-success [_ f] (catching-errors (->Success (f val))))
  (bind [_ f] (catching-errors (f val)))
  (unwrap [_] val)
  (send [_ f] (f val)))

(defn success [o] (->Success o))

(defmacro dispatch-result [result success-fn failure-fn]
  (let [success-arg  (first (first success-fn))
        success-body (rest success-fn)
        failure-arg  (first (first failure-fn))
        failure-body (rest failure-fn)]
    `(if (success? ~result)
       (let [~success-arg (unwrap ~result)]
         ~@success-body)
       (let [~failure-arg ~result]
         ~@failure-body))))

(defmacro let-result [bindings & body]
  (defn fold-forms [bindings inner]
    (if (empty? bindings)
      `(do ~@body)
      (let [[var val] (first bindings)]
        `(bind ~val (fn [~var] ~(fold-forms (rest bindings) body))))))
  (when (not (even? (count bindings)))
    (throw (ex-info "let-result binding requires an even number of forms")))
  (let [bindings (partition 2 bindings)]
    (fold-forms bindings body)))

;; Nah, this is wrong--inject argument
#_(defmacro result-> [result & steps]
  (defn fold-forms [fs o]
    (if (empty? fs)
      `(do ~o)
      (let [f (first fs)
            fs (rest fs)]
        `(bind ~(fold-forms fs o) ~f))))
  (fold-forms (reverse steps) result))

(defn result-of [spec]
  (fn [o]
    (s/or :failure (failure? o)
          :success (s/and (success? o)
                          (s/valid? spec (unwrap o))))))
