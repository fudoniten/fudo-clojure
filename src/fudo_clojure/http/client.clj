(ns fudo-clojure.http.client
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [clj-http.client :as clj-http]

            [fudo-clojure.common :refer [ensure-conform]]
            [fudo-clojure.result :as result :refer [catching-errors
                                                    exception-failure
                                                    map-success
                                                    success
                                                    to-string]]
            [fudo-clojure.logging :as log]
            [fudo-clojure.http.request :as req]))

(defprotocol HTTPResult
  (status [self])
  (status-message [self]))

(defprotocol HTTPFailure
  (not-found? [self])
  (forbidden? [self])
  (unauthorized? [self])
  (bad-request? [self])
  (server-error? [self]))

(defn http-success [resp]
  (reify
    result/Result
    (success?    [_]   true)
    (failure?    [_]   false)
    (map-success [_ f] (f resp))
    (bind        [_ f] (catching-errors (f resp)))
    (unwrap      [_]   (:body resp))
    (to-string   [_]   (str "#http-success[" resp "]"))

    HTTPResult
    (status [_] (Integer/parseInt (:status resp)))
    (status-message [_] (str (:status resp) " " (:reason-phrase resp)))))

(defn http-failure [e]
  (let [resp (ex-data e)]
    (reify
      result/Result
      (failure?    [_]      true)
      (success?    [_]      false)
      (map-success [self _] self)
      (bind        [self _] self)
      (unwrap      [_]      (throw e))
      (to-string   [_]      (str "#http-failure[" (.toString e) "]"))

      HTTPResult
      (status         [self] (:status resp))
      (status-message [self] (str (:status resp) " " (:reason-phrase resp)))

      HTTPFailure
      (not-found?    [_] (= 404  (status e)))
      (unauthorized? [_] (= 401  (status e)))
      (forbidden?    [_] (= 403  (status e)))
      (bad-request?  [_] (= 400  (status e)))
      (server-error? [_] (>= 500 (status e)))

      result/ResultFailure
      (error-message [self] (status-message self))
      (get-exception [_]    e))))

(def http-result?  (partial satisfies? HTTPResult))
(def http-success? (every-pred http-result? result/success?))
(def http-failure? (partial satisfies? HTTPFailure))
(def not-found-error? (every-pred http-failure? not-found?))

(defprotocol HTTPClient
  (get!    [self req])
  (post!   [self req])
  (delete! [self req]))

(def client? (partial satisfies? HTTPClient))
(s/def ::client client?)

(defn- response->json [response]
  (json/read-str (:body response) :key-fn keyword))

(defn http-json-client [& {:keys [logger authenticator]
                           :or   {authenticator (fn [_] {})}}]
  (letfn [(execute! [f req opt-fn]
            (let [final (ensure-conform ::req/request (req/finalize req authenticator))]
              (map-success
               (try (http-success (f (::req/url final) (opt-fn final)))
                    (catch clojure.lang.ExceptionInfo e
                      (http-failure e))
                    (catch java.lang.RuntimeException e
                      (exception-failure e)))
               response->json)))]
    (reify HTTPClient
      (get! [_ req]
        (execute! clj-http/get req
                  (fn [final] (assoc (select-keys final [:headers])
                                    :accept :json))))
      (post! [_ req]
        (execute! clj-http/post req
                  (fn [final] (assoc (select-keys final [:headers :body])
                                    :accept       :json
                                    :content-type :json))))
      (delete! [_ req]
        (execute! clj-http/delete req
                  (fn [final] (assoc (select-keys final [:headers])
                                    :accept :json)))))))

(defn- dispatch-nth [n f]
  (fn [& args] (f (nth args n))))

(defmulti execute-request! (dispatch-nth 1 ::req/http-method))

(defmethod execute-request! :GET    [client req] (get!    client req))
(defmethod execute-request! :POST   [client req] (post!   client req))
(defmethod execute-request! :DELETE [client req] (delete! client req))
