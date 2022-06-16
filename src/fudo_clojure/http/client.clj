(ns fudo-clojure.http.client
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [clojure.pprint :refer [pprint]]
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
    (map-success [_ f] (catching-errors (success (f resp))))
    (bind        [_ f] (f resp))
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
      (not-found?    [self] (= 404  (status self)))
      (unauthorized? [self] (= 401  (status self)))
      (forbidden?    [self] (= 403  (status self)))
      (bad-request?  [self] (= 400  (status self)))
      (server-error? [self] (<= 500 (status self) 599))

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

(def base-client
  (reify HTTPClient
    (get! [_ req]
      (clj-http/get (::req/url req)
                    (merge (select-keys req [::req/headers])
                           (::req/opts req))))
    (post! [_ req]
      (clj-http/post (::req/url req)
                     (merge (select-keys req [::req/headers ::req/body])
                            (::req/opts req))))
    (delete! [_ req]
      (clj-http/delete (::req/url req)
                       (merge (select-keys req [::req/headers])
                              (::req/opts req))))))

(defn client:wrap-results [client]
  (letfn [(execute! [f req]
            (try (http-success (f client req))
                 (catch clojure.lang.ExceptionInfo e
                   (http-failure e))
                 (catch java.lang.RuntimeException e
                   (exception-failure e))))]
    (reify HTTPClient
      (get!    [_ req] (execute! get!    req))
      (post!   [_ req] (execute! post!   req))
      (delete! [_ req] (execute! delete! req)))))

(defn client:log-requests [client logger]
  (letfn [(pp-str [o] (with-out-str (pprint o)))
          (wrap-request [f method req]
            (let [id (java.util.UUID/randomUUID)]
              (log/info! logger (str method " request (" id "): " (::req/url req)))
              (log/debug! logger (pp-str req))
              (let [resp (f client req)]
                (log/info! logger (str method " response (" id "): " (::req/url req)))
                (log/debug! logger (pp-str resp))
                resp)))]
    (reify HTTPClient
      (get!    [_ req] (wrap-request get!    "GET"    req))
      (post!   [_ req] (wrap-request post!   "POST"   req))
      (delete! [_ req] (wrap-request delete! "DELETE" req)))))

(defn client:authenticate-requests [client authenticator]
  (reify HTTPClient
    (get!    [_ req] (get!    client (authenticator req)))
    (post!   [_ req] (post!   client (authenticator req)))
    (delete! [_ req] (delete! client (authenticator req)))))

(defn client:jsonify [client]
  (letfn [(decode-response [resp] (map-success resp response->json))
          (prepare-request [req] (assoc req ::req/body
                                        (some-> req
                                                (req/body-params)
                                                (json/write-str))))]
    (reify HTTPClient
      (get! [_ req]
        (decode-response (get! client
                               (assoc (prepare-request req) ::req/opts
                                      {:accept :json}))))
      (post! [_ req]
        (decode-response (post! client
                                (assoc (prepare-request req) ::req/opts
                                       {:accept       :json
                                        :content-type :json}))))
      (delete! [_ req]
        (decode-response (delete! client
                                  (assoc (prepare-request req) ::req/opts
                                         {:accept :json})))))))

(defn json-client [& {:keys [logger authenticator]
                      :or   {logger (log/dummy-logger)}}]
  (-> base-client
      (client:log-requests logger)
      (client:wrap-results)
      (client:authenticate-requests (or authenticator identity))
      (client:jsonify)))

(defn- dispatch-nth [n f]
  (fn [& args] (f (nth args n))))

(defmulti execute-request! (dispatch-nth 1 ::req/http-method))

(defmethod execute-request! :GET    [client req] (get!    client req))
(defmethod execute-request! :POST   [client req] (post!   client req))
(defmethod execute-request! :DELETE [client req] (delete! client req))
