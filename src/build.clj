(ns build
  (:require [clojure.tools.build.api :as b]))

(def target-dir "./target")
(def class-dir (format "%s/classes" target-dir))
(def basis (b/create-basis {:project "deps.edn"}))
(defn mk-jar-file [lib-name version]
  (format "%s/%s-%s.jar"
          target-dir (name lib-name) version))
(defn mk-uberjar-file [lib-name version]
  (format "%s/%s-%s-standalone.jar"
          target-dir (name lib-name) version))

(defn clean [_]
  (println (format "removing build target folder \"%s\"" target-dir))
  (b/delete {:path target-dir}))

(defn jar [{:keys [project version]}]
  (let [jar-file (mk-jar-file project version)]
    (clean nil)
    (b/copy-dir {:src-dirs   ["src"]
                 :target-dir target-dir})
    (b/compile-clj {:basis     basis
                    :src-dirs  ["src"]
                    :class-dir class-dir})
    (b/write-pom {:class-dir class-dir
                  :lib       project
                  :version   version
                  :basis     basis
                  :src-dirs  ["src"]})
    (b/jar {:class-dir class-dir
            :jar-file  jar-file})
    (println (format "jar file created at: %s" jar-file))))

(defn uberjar [{:keys [project version]}]
  (let [uber-file (mk-uberjar-file project version)]
    (clean nil)
    (b/copy-dir {:src-dirs   ["src"]
                 :target-dir class-dir})
    (b/compile-clj {:basis     basis
                    :src-dirs  ["src"]
                    :class-dir class-dir})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis     basis})
    (println (format "uberjar file created at: %s" uber-file))))
