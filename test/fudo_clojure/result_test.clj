(ns fudo-clojure.result-test
  (:require [clojure.test :as t :refer [deftest testing is run-tests]])
  (:use fudo-clojure.result))

(defmacro is-true [& body]
  `(is (= true ~@body)))

(defmacro is-false [& body]
  `(is (= false ~@body)))

(defmacro is-success? [& body]
  `(is-true (success? ~@body)))

(defmacro is-failure? [& body]
  `(is-true (failure? ~@body)))

(deftest test-result
  (testing "basic creation"
    (is-true  (success? (success 3)))
    (is-false (failure? (success 3)))

    (is-false (success? (failure "oops")))
    (is-true  (failure? (failure "oops")))

    (is-false (success? (exception-failure (ex-info "oops" {}))))
    (is-true  (failure? (exception-failure (ex-info "oops" {}))))

    (is-false (exception? (failure "oops")))
    (is-true  (exception? (exception-failure (ex-info "oops" {})))))

  (testing "map-success"
    (is-success? (map-success (success 3) #(* % 5)))
    (is-failure? (map-success (failure "oops") #(* % 5)))
    (is-failure? (map-success (exception-failure (ex-info "oops" {})) #(* % 5)))

    (is (= 15 (unwrap (map-success (success 3) #(* % 5)))))
    (is (= "oops" (error-message (map-success (failure "oops") #(* % 5)))))
    (let [e (ex-info "oops" {})]
      (= e (get-exception (map-success (exception-failure e) #(* % 5))))
      (is-failure? (map-success (success 3) (fn [_] (throw e))))
      (= e (get-exception (map-success (exception-failure e) (fn [_] (throw e)))))))

  (testing "bind"
    (is-success? (bind (success 3) #(success (* % 5))))
    (is (= 15 (unwrap (bind (success 3) #(success (* % 5))))))

    (is-failure? (bind (success 3) (fn [_] (failure "oops"))))
    (is (= "oops" (error-message (bind (success 3) (fn [_] (failure "oops"))))))

    (is-failure? (bind (failure "oops") (fn [_] (success "hi"))))

    (let [e (ex-info "oops" {})]
      (is-failure? (bind (success 3) (fn [_] (throw e))))
      (is (= e (get-exception (bind (exception-failure e) (fn [_] (success "hi"))))))
      (is (not (= e (get-exception
                     (bind (exception-failure (ex-info "blah" {})) (fn [_] (throw e)))))))))

  (testing "unwrap"
    (is (= 3 (unwrap (success 3))))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"blah"
                          (unwrap (failure "blah"))))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"oops"
                          (unwrap (exception-failure (ex-info "oops" {})))))))

(deftest test-dispatch-results
  (is (= 15
         (dispatch-result (success 3)
                          ([n] (* n 5))
                          ([e] (error-message e)))))
  (is (= "oops"
         (dispatch-result (failure "oops")
                          ([n] (* n 5))
                          ([e] (error-message e)))))
  (is (= "oops"
         (dispatch-result (exception-failure (ex-info "oops" {}))
                          ([n] (* n 5))
                          ([e] (error-message e))))))

(deftest test-let-result
  (is (= (success 15) (let-result [x (success 3)] (success (* x 5)))))
  (is-failure? (let-result [x (failure "oops")] (success (* x 5))))
  (is (= "oops" (error-message (let-result [x (failure "oops")] (success (* x 5))))))
  (is (= (success 15)
         (let-result [x (success 5)
                      y (success 3)]
           (success (* x y)))))
  (is (= (success 30)
         (let-result [x (success 5)
                      y (success 3)
                      z (success 2)]
           (success (* x y z)))))
  (is-failure? (let-result [x (success 5)
                            y (failure "oops")]
                 (success (* x y))))
  (is-failure? (let-result [x (failure "oops")
                            y (success 5)]
                 (success (* x y))))
  (is-failure? (let-result [x (success 5)
                            y (success 3)
                            z (failure "oops")]
                 (success (* x y z)))))

#_(run-tests 'fudo-clojure.result-test)
