(ns fudo-clojure.common
  (:require [clojure.spec.alpha :as s]
            [clojure.pprint :refer [pprint]]))

(defn to-uuid [s] (java.util.UUID/fromString s))

(defn pthru [o]
  (println "****")
  (pprint o)
  (println "****")
  o)

(defn ensure-conform [spec obj]
  (if (s/valid? spec obj)
    obj
    (throw (RuntimeException. (s/explain-str spec obj)))))

(defmacro *-> [& fs]
  (let [init (gensym)]
    `(fn [~init] (-> ~init ~@fs))))

(defn current-epoch-timestamp []
  (.getEpochSecond (java.time.Instant/now)))

(defn instant-to-epoch-timestamp [instant]
  (.getEpochSecond instant))

(defn parse-epoch-timestamp [epoch-second]
  (java.time.Instant/ofEpochSecond epoch-second))

(defn parse-timestamp [timestamp]
  (java.time.Instant/parse timestamp))

(defn string->bytes [s] (.getBytes s))
(defn bytes->string [bs]
  (String. bs (java.nio.charset.Charset/forName "UTF-8")))


(defn base64-encode [to-encode]
  (.encode (java.util.Base64/getEncoder)
           to-encode))

(defn base64-encode-string [to-encode]
  (.encodeToString (java.util.Base64/getEncoder)
           to-encode))

(defn base64-decode [to-decode]
  (.decode (java.util.Base64/getDecoder)
           to-decode))
(s/fdef base64-decode
  :args (s/cat :to-decode bytes?)
  :ret  bytes?)

(defmacro is-valid? [spec target]
  `(clojure.test/is (= true (clojure.spec.alpha/valid? ~spec ~target))))
(defmacro is-invalid? [spec target]
  `(clojure.test/is (= false (clojure.spec.alpha/valid? ~spec ~target))))

(defn sample [coll]
  (nth coll (rand-int (count coll))))

(defn find-first [f coll]
  (first (filter f coll)))
