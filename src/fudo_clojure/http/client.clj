(ns fudo-clojure.http.client
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [clojure.pprint :refer [pprint]]
            [clj-http.client :as clj-http]
            [fudo-clojure.result :as result :refer [catching-errors
                                                    exception-failure
                                                    map-success
                                                    success]]
            [fudo-clojure.logging :as log]
            [fudo-clojure.http.request :as req]
            [less.awful.ssl :as ssl]))

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
  (delete! [self req])
  (put!    [self req]))

(def client? (partial satisfies? HTTPClient))
(s/def ::client client?)

(defn- response->json [response]
  (json/read-str (:body response) :key-fn keyword))

(def base-client
  (reify HTTPClient
    (get! [_ req]
      (clj-http/get (str (::req/url req))
                    (merge (select-keys req [::req/headers])
                           (::req/opts req))))
    (post! [_ req]
      (clj-http/post (str (::req/url req))
                     (merge (select-keys req [::req/headers ::req/body])
                            (::req/opts req))))
    (delete! [_ req]
      (clj-http/delete (str (::req/url req))
                       (merge (select-keys req [::req/headers])
                              (::req/opts req))))

    (put! [_ req]
      (clj-http/put (str (::req/url req))
                    (merge (select-keys req [::req/headers ::req/body])
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
      (delete! [_ req] (execute! delete! req))
      (put!    [_ req] (execute! put!    req)))))

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
      (delete! [_ req] (wrap-request delete! "DELETE" req))
      (put!    [_ req] (wrap-request put!    "PUT"    req)))))

(defn client:authenticate-requests [client authenticator]
  (reify HTTPClient
    (get!    [_ req] (get!    client (authenticator req)))
    (post!   [_ req] (post!   client (authenticator req)))
    (delete! [_ req] (delete! client (authenticator req)))
    (put!    [_ req] (put!    client (authenticator req)))))

(defn client:set-certificate-authority [client ca]
  (if (nil? ca)
    client
    (let [trust-store (ssl/trust-store ca)
          add-keystore (fn [req] (req/with-option req :trust-store trust-store))]
      (println (str "using certificate authority: " ca))
      (reify HTTPClient
        (get!    [_ req] (get!    client (add-keystore req)))
        (post!   [_ req] (post!   client (add-keystore req)))
        (delete! [_ req] (delete! client (add-keystore req)))
        (put!    [_ req] (put!    client (add-keystore req)))))))

(defn client:jsonify [client]
  (letfn [(decode-response [resp-fmt resp]
            (if (or (nil? resp-fmt) (= :json resp-fmt))
              (map-success resp response->json)
              resp))
          (prepare-request [req] (assoc req ::req/body
                                        (some-> req
                                                (req/body-params)
                                                (json/write-str))))]
    (reify HTTPClient
      (get! [_ req]
        (decode-response (::req/response-format req)
                         (get! client
                               (-> req
                                   (prepare-request)
                                   (req/with-option :accept :json)))))
      (post! [_ req]
        (decode-response (::req/response-format req)
                         (post! client
                                (-> req
                                    (prepare-request)
                                    (req/with-option :accept       :json)
                                    (req/with-option :content-type :json)))))
      (delete! [_ req]
        (decode-response (::req/response-format req)
                         (delete! client
                                  (-> req
                                      (prepare-request)
                                      (req/with-option :accept :json)))))

      (put! [_ req]
        (decode-response (::req/response-format req)
                         (put! client
                               (-> req
                                   (prepare-request)
                                   (req/with-option :accept       :json)
                                   (req/with-option :content-type :json))))))))

(defn json-client [& {:keys [logger authenticator certificate-authority]
                      :or   {logger (log/dummy-logger)}}]
  (-> base-client
      (client:log-requests logger)
      (client:set-certificate-authority certificate-authority)
      (client:wrap-results)
      (client:authenticate-requests (or authenticator identity))
      (client:jsonify)))

(defn- dispatch-nth [n f]
  (fn [& args] (f (nth args n))))

(defmulti execute-request! (dispatch-nth 1 ::req/http-method))

(defmethod execute-request! :GET    [client req] (get!    client req))
(defmethod execute-request! :POST   [client req] (post!   client req))
(defmethod execute-request! :DELETE [client req] (delete! client req))
(defmethod execute-request! :PUT    [client req] (put!    client req))
