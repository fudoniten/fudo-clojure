(ns fudo-clojure.http.request
  (:require [clojure.spec.alpha :as s]
            [clj-http.client :as clj-http]
            [camel-snake-kebab.core :refer [->snake_case_string ->camelCaseString]]))

(s/def ::http-method #{ :GET :POST :DELETE :PUT })

(let [path-regex #"^(/[^ !$?`&*\(\)+]+)+$"]
  (defn- valid-base-path? [path?]
    (and (string? path?)
         (not (nil? (re-matches path-regex path?))))))

(s/def ::base-request-path valid-base-path?)
(s/def ::request-headers (s/map-of keyword? string?))

(s/def ::param-value (s/or :str string? :num number? :keyword keyword?))
(s/def ::query-params (s/map-of keyword? ::param-value))
(s/def ::body-params (s/map-of keyword? ::param-value))
(s/def ::response-format #{:json :binary :plain-text})

(defn- update-base
  "Update, but pass in the whole map to f, not just the key value."
  [m k f & args]
  (assoc m k (apply f (cons m args))))

(def url? (partial instance? java.net.URL))

(defn- url->string [url] (.toExternalForm url))

;; NOTE: The question-mark MUST be on the path, because clj-http adds it, and
;;  Coinbase expects the path to match exactly!
(defn- build-path [path query-params]
  (str path "?" (clj-http/generate-query-string query-params)))

(defn- build-request-path [req]
  (apply build-path ((juxt ::base-request-path ::query-params) req)))

(defn- build-url [host port path query-params scheme]
  (let [full-path (build-path path query-params)]
    (java.net.URL. scheme host port full-path)))

(defn- build-request-url [req]
  (apply build-url ((juxt ::host ::port ::base-request-path ::query-params ::scheme) req)))
(s/fdef build-request-url
  :args (s/cat :req (s/keys :req [::base-request-path ::query-params ::scheme]))
  :ret  string?)

(defn- refresh-request-url [req]
  (assoc req ::url (build-request-url req)))

(defn base-request []
  {::timestamp         (java.time.Instant/now)
   ::query-params      {}
   ::base-request-path "/"
   ::port              80
   ::scheme            "https"})

(defn as-get [req]
  (assoc req ::http-method :GET))

(defn as-post [req]
  (assoc req ::http-method :POST))

(defn as-delete [req]
  (assoc req ::http-method :DELETE))

(defn as-put [req]
  (assoc req ::http-method :PUT))

(defn with-path [req path]
  (-> req
      (assoc       ::base-request-path path)
      (update-base ::request-path build-request-path)
      (refresh-request-url)))

(defn with-host [req host]
  (-> req
      (assoc ::host host)
      (refresh-request-url)))

(defn with-port [req port]
  (-> req
      (assoc ::port port)
      (refresh-request-url)))

(defn with-headers [req headers]
  (update req ::headers
          (fn [prev-headers]
            (merge prev-headers headers))))

(defn with-header [req header value]
  (update req ::headers
          (fn [headers]
            (assoc (or headers {})
                   header value))))

(defn with-body [req body]
  (assoc req ::body body))

(defn with-timestamp [req ts]
  (assoc req ::timestamp ts))

(defn with-option [req key val]
  (assoc-in req [::opts key] val))

(defn with-scheme [req scheme]
  (assoc req ::scheme scheme))

(defn with-response-format [req fmt]
  (assoc req ::response-format fmt))

(defn- stringify [v]
  (cond (keyword? v) (name v)
        (coll? v)    (map stringify v)
        :else        (str v)))

(defn- header-case [h sanitizer]
  (let [leading-chars (re-find #"^[^a-zA-Z0-9]*" (name h))
        trailing-chars (re-find #"[^a-zA-Z0-9]*$" (name h))]
    (keyword (str leading-chars (sanitizer h) trailing-chars))))

(defn sanitize-params [params sanitizer]
  (into {}
        (map (fn [[h v]] [(header-case h sanitizer) (stringify v)]))
        params))

(defn- with-query-params-impl [req params sanitizer]
  (-> req
      (update      ::query-params merge (sanitize-params params sanitizer))
      (update-base ::request-path build-request-path)
      (refresh-request-url)))

(defn with-query-params [req params]
  (with-query-params-impl req params name))

(defn with_query_params [req params]
  (with-query-params-impl req params ->snake_case_string))

(defn withQueryParams [req params]
  (with-query-params-impl req params ->camelCaseString))

#_(defn with-body-params [req params]
  (update req ::body-params merge params))

;; Basically getters
(def timestamp    ::timestamp)
(def method       (comp name ::http-method))
(def request-path ::request-path)
(def body         ::body)
(def host         ::host)

(defn with-body-params
  ([req params] (with-body-params req params ->snake_case_string))
  ([req params sanitizer]
   (-> req
       (update ::body-params merge (sanitize-params params sanitizer)))))

(defn with_body_params [req params]
  (with-body-params req params ->snake_case_string))

(defn withBodyParams [req params]
  (with-body-params req params ->camelCaseString))

(defn body-params [req]
  (::body-params req))

(defn uri [req] (-> req ::url (url->string)))

(s/def ::request
  (s/keys :req [::url ::http-method ::query-params ::base-request-path ::timestamp]
          :opt [::body-params]))
