(ns fudo-clojure.common
  (:require [clojure.spec.alpha :as s]))

(defn ensure-conform [spec obj]
  (if (s/valid? spec obj)
    obj
    (throw (RuntimeException. (s/explain-str spec obj)))))
