(ns fudo-clojure.testing
  (:require  [clojure.test :as t]))

(defmacro is-true [target]
  `(clojure.test/is (= true ~target)))
(defmacro is-false [target]
  `(clojure.test/is (= false ~target)))
(defmacro is-success [target]
  `(is-true (fudo-clojure.result/success? ~target)))
(defmacro is-failure [target]
  `(is-true (fudo-clojure.result/failure? ~target)))
