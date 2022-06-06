(ns fudo-clojure.http.client-test
  (:use fudo-clojure.http.client)
  (:require [clojure.test :as test :refer [deftest is testing]]

            [fudo-clojure.result :refer [success? failure? success failure unwrap]]
            [fudo-clojure.testing :refer [is-true is-false is-success is-failure]]
            [fudo-clojure.http.request :as req]
            [fudo-clojure.http.client :as client]
            [clojure.test :as t]
            [clojure.data.json :as json]
            [fudo-clojure.common :as common]
            [fudo-clojure.logging :as log]))

(defn- client-returning [response]
  (-> (reify client/HTTPClient
        (get!    [_ _] response)
        (post!   [_ _] response)
        (delete! [_ _] response))
      (client/client:log-requests (log/dummy-logger))
      (client/client:wrap-results)))

(defn- client-returning-fn [f]
  (-> (reify client/HTTPClient
        (get!    [_ req] (f req))
        (post!   [_ req] (f req))
        (delete! [_ req] (f req)))
      (client/client:log-requests (log/dummy-logger))
      (client/client:wrap-results)))

(defn- client-throwing [e]
  (-> (reify client/HTTPClient
        (get!    [_ _] (throw e))
        (post!   [_ _] (throw e))
        (delete! [_ _] (throw e)))
      (client/client:log-requests (log/dummy-logger))
      (client/client:wrap-results)))

(def req
  (-> (req/base-request)
      (req/as-get)
      (req/with-path "/one/two")
      (req/with-host "some.dummy.host")))

(defn resp [& {:keys [code body]
               :or   {code 200 body nil}}]
  {:status (.toString code)
   :body   body})

(defn json-resp [& {:keys [code body]
               :or   {code 200 body nil}}]
  {:status (.toString code)
   :body   (json/write-str body)})

(deftest test-execute-request!
  (testing "success"
    (is-true (success? (execute-request! (client-returning (resp)) req)))

    (is (= "hello"
           (-> (execute-request! (client-returning (resp :body "hello"))
                                 (req/as-get req))
               unwrap)))

    (is (= "hello"
           (-> (execute-request! (client-returning (resp :body "hello"))
                                 (req/as-post req))
               unwrap)))

    (is (= "hello"
           (-> (execute-request! (client-returning (resp :body "hello"))
                                 (req/as-delete req))
               unwrap)))

    (is (= "https://test.host/one/two?"
           (-> (execute-request! (client-returning-fn (fn [req] (resp :body (::req/url req))))
                                 (-> req
                                     (req/with-host "test.host")
                                     (req/with-path "/one/two")))
               unwrap)))

    (is (= "https://test.host/one/two?test=1"
           (-> (execute-request! (client-returning-fn (fn [req] (resp :body (::req/url req))))
                                 (-> req
                                     (req/with-host "test.host")
                                     (req/with-path "/one/two")
                                     (req/with-query-params { :test 1 })))
               unwrap)))

    (is (= "https://test.host/one/two?test_one=1"
           (-> (execute-request! (client-returning-fn (fn [req] (resp :body (::req/url req))))
                                 (-> req
                                     (req/with-host "test.host")
                                     (req/with-path "/one/two")
                                     (req/with-query-params { :TEST-ONE 1 })))
               unwrap))))

  (testing "failures"
    (is-true (client/http-failure?
              (execute-request! (client-throwing (ex-info "error" { :status 404 :reason-phrase "oops" }))
                                req)))

    (is-true (client/not-found-error?
              (execute-request! (client-throwing (ex-info "error" { :status 404 :reason-phrase "oops" }))
                                req)))

    (is-false (client/not-found-error?
               (execute-request! (client-throwing (ex-info "error" { :status 405 :reason-phrase "oops" }))
                                 req)))

    (is (= "404 oops"
           (-> (execute-request! (client-throwing (ex-info "error" { :status 404 :reason-phrase "oops" }))
                                 req)
               (client/status-message))))

    (is-failure (execute-request! (client-throwing (RuntimeException. "test"))
                                  req))

    (is-false (client/http-failure?
               (execute-request! (client-throwing (RuntimeException. "test"))
                                 req)))))

(deftest test-results
  (testing "successes"
    (is-true (success? (http-success {})))
    (is-false (failure? (http-success {})))
    (is-true (http-result? (http-success {})))
    (is-false (http-result? (success {}))))
  (testing "failures"
    (is-false (success? (http-failure {})))
    (is-true  (failure? (http-failure {})))
    (is-true  (http-result? (http-failure {})))
    (is-false (http-result? (failure {}))))
  (testing "error statuses"
    (letfn [(failure-status [s] (ex-info "oops" {:status s}))]
      (is-true (client/not-found?    (http-failure (failure-status 404))))
      (is-true (client/unauthorized? (http-failure (failure-status 401))))
      (is-true (client/forbidden?    (http-failure (failure-status 403))))
      (is-true (client/bad-request?  (http-failure (failure-status 400))))
      (is-true (client/server-error? (http-failure (failure-status 500))))
      (is-true (client/server-error? (http-failure (failure-status 550))))

      (is-false (client/unauthorized? (http-failure (failure-status 404))))
      (is-false (client/not-found?    (http-failure (failure-status 401))))
      (is-false (client/bad-request?  (http-failure (failure-status 403))))
      (is-false (client/forbidden?    (http-failure (failure-status 400))))
      (is-false (client/not-found?    (http-failure (failure-status 500))))
      (is-false (client/bad-request?  (http-failure (failure-status 550))))
      (is-false (client/server-error? (http-failure (failure-status 404))))
      (is-false (client/server-error? (http-failure (failure-status 401))))
      (is-false (client/server-error? (http-failure (failure-status 403))))
      (is-false (client/server-error? (http-failure (failure-status 400)))))))

(defn- json-client-returning [resp]
  (-> (reify client/HTTPClient
        (get!    [_ _] resp)
        (post!   [_ _] resp)
        (delete! [_ _] resp))
      (client/client:wrap-results)
      (client/client:jsonify)))

(defn- json-client-returning-fn [f]
  (-> (reify client/HTTPClient
        (get!    [_ req] (f req))
        (post!   [_ req] (f req))
        (delete! [_ req] (f req)))
      (client/client:wrap-results)
      (client/client:jsonify)))

(defn- json-client-throwing [e]
  (-> (reify client/HTTPClient
        (get!    [_ _] (throw e))
        (post!   [_ _] (throw e))
        (delete! [_ _] (throw e)))
      (client/client:wrap-results)
      (client/client:jsonify)))

(deftest test-json-client
  (testing "json body parsing"
    (is (= {}
           (-> (execute-request! (json-client-returning (json-resp :body {}))
                                 req)
               (unwrap))))
    (is (= {:test-value 15}
           (-> (execute-request! (json-client-returning (json-resp :body {:test-value 15}))
                                 req)
               (unwrap))))
    (is-failure (execute-request! (json-client-returning (resp :body "{")) req)))

  (testing "json body params"
    (let [body {:test_value "15"}]
      (is (= body
             (-> (execute-request! (json-client-returning-fn (fn [req] (resp :body (::req/body req))))
                                   (-> req
                                       (req/as-post)
                                       (req/with-body-params body)))
                 (unwrap)))))

    (let [body {:test_value "15" :other_value "hi there"}]
      (is (= body
             (-> (execute-request! (json-client-returning-fn (fn [req] (resp :body (::req/body req))))
                                   (-> req
                                       (req/as-post)
                                       (req/with-body-params body)))
                 (unwrap)))))))

(defn- authenticated-client-returning-fn [auther f]
  (-> (reify client/HTTPClient
        (get!    [_ req] (f req))
        (post!   [_ req] (f req))
        (delete! [_ req] (f req)))
      (client/client:wrap-results)
      (client/client:authenticate-requests auther)
      (client/client:jsonify)))

(deftest test-authenticator
  (let [output-keys (fn [fs] (fn [req] (assoc req ::output ((apply juxt fs) req))))
        forward-output (fn [req] (json-resp :body {:result (::output req)}))]
    (testing "passed-request"
      (let [ts (java.time.Instant/now)]
        (is (= (common/instant-to-epoch-timestamp ts)
               (-> (execute-request! (authenticated-client-returning-fn (output-keys [req/timestamp])
                                                                        forward-output)
                                     (-> req
                                         (req/with-timestamp ts)))
                   (unwrap)
                   :result
                   (nth 0)
                   common/parse-timestamp
                   common/instant-to-epoch-timestamp))))

      (is (= "GET"
             (-> (execute-request! (authenticated-client-returning-fn (output-keys [req/method])
                                                                      forward-output)
                                   req)
                 (unwrap)
                 :result
                 (nth 0))))

      (is (= "/one/two?"
             (-> (execute-request! (authenticated-client-returning-fn (output-keys [req/request-path])
                                                                      forward-output)
                                   (-> req
                                       (req/with-path "/one/two")))
                 (unwrap)
                 :result
                 (nth 0))))

      (is (= "/one/two?a=3&b=4"
             (-> (execute-request! (authenticated-client-returning-fn (output-keys [req/request-path])
                                                                      forward-output)
                                   (-> req
                                       (req/with-path "/one/two")
                                       (req/with-query-params {:a 3 :b 4})))
                 (unwrap)
                 :result
                 (nth 0))))

      (let [body-params { :a "3" :b "4" }]
        (is (= body-params
               (-> (execute-request! (authenticated-client-returning-fn (output-keys [req/body])
                                                                        forward-output)
                                     (-> req
                                         (req/with-body-params body-params)))
                   (unwrap)
                   :result
                   (nth 0)
                   (json/read-str :key-fn keyword)))))

      (let [body-params { :THIS-IS :here :ThatIs 4 }]
        (is (= { :this_is "here" :that_is "4" }
               (-> (execute-request! (authenticated-client-returning-fn (output-keys [req/body])
                                                                        forward-output)
                                     (-> req
                                         (req/with-body-params body-params)))
                   (unwrap)
                   :result
                   (nth 0)
                   (json/read-str :key-fn keyword))))))))


(t/run-tests 'fudo-clojure.http.client-test)
