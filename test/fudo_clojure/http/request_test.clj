(ns fudo-clojure.http.request-test
  (:require [clojure.test :as t :refer [deftest is testing]]

            [fudo-clojure.result :refer [failure?
                                         error-message
                                         success?
                                         unwrap]]
            [fudo-clojure.http.request :as req]
            [fudo-clojure.testing :refer [is-true is-false]])
  (:use fudo-clojure.http.request))

(deftest test-valid-base-path?
  (let [valid-base-path? #'fudo-clojure.http.request/valid-base-path?]

    (testing "valid paths"
      (is-true (valid-base-path? "/one/two"))
      (is-true (valid-base-path? "/one"))
      (is-true (valid-base-path? (str "/test/one/" (java.util.UUID/randomUUID)))))
    (testing "invalid paths"
      (is-false (valid-base-path? (str "/test/one/"
                                       (java.util.UUID/randomUUID)
                                       "?var=something")))
      (is-false (valid-base-path? "/one/two?"))
      (is-false (valid-base-path? "/one/two?a=3"))
      (is-false (valid-base-path? "/one/two?a=3&b=4"))
      (is-false (valid-base-path? "one/two"))
      (is-false (valid-base-path? "/one/two!"))
      (is-false (valid-base-path? "/one/$two")))))

(deftest test-constructors
  (testing "as-get"
    (is (= :GET (::req/http-method (as-get {})))))
  (testing "with-path"
    (is (= "/one/two" (::req/base-request-path (with-path {} "/one/two")))))
  (testing "with-host"
    (is (= "one.two.three" (::req/host (with-host {} "one.two.three"))))))


(t/run-tests 'fudo-clojure.http.request-test)
