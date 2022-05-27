(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib-name 'org.fudo/fudo-clojure)
(def major-version "0")

(def target-dir "./target")
(def class-dir (format "%s/classes" target-dir))
(def version (format "%s.%s" major-version (b/git-count-revs nil)))
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "%s/%s-%s.jar"
                      target-dir (name lib-name) version))
(def uber-file (format "%s/%s-%s-standalone.jar"
                       target-dir (name lib-name) version))

(defn clean [_]
  (println (format "removing build target folder \"%s\"" target-dir))
  (b/delete {:path target-dir}))

(defn jar [_]
  (clean nil)
  (b/copy-dir {:src-dirs   ["src"]
               :target-dir target-dir})
  (b/compile-clj {:basis     basis
                  :src-dirs  ["src"]
                  :class-dir class-dir})
  (b/write-pom {:class-dir class-dir
                :lib       lib-name
                :version   version
                :basis     basis
                :src-dirs  ["src"]})
  (b/jar {:class-dir class-dir
          :jar-file  jar-file})
  (println (format "jar file created at: %s" jar-file)))

(defn uberjar [_]
  (clean nil)
  (b/copy-dir {:src-dirs   ["src"]
               :target-dir class-dir})
  (b/compile-clj {:basis     basis
                  :src-dirs  ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     basis})
  (println (format "uberjar file created at: %s" uber-file)))
