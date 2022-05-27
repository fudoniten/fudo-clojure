(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib-name 'org.fudo/fudo-clojure)

(def target-dir "./target")
(def class-dir (format "%s/classes" target-dir))
(def basis (b/create-basis {:project "deps.edn"}))

(def timestamp (-> (java.time.LocalDateTime/now)
                   (.format java.time.format.DateTimeFormatter/BASIC_ISO_DATE)))

(defn jar-filename [version]
  (format "%s/%s-%s.jar"
          target-dir (name lib-name) version))
(defn uber-filename [version]
  (format "%s/%s-%s-standalone.jar"
          target-dir (name lib-name) version))

(defn clean [params]
  (println (format "removing build target folder \"%s\"" target-dir))
  (b/delete {:path target-dir})
  params)

(defn jar [{:keys [version] :or {version timestamp} :as params}]
  (let [jar-file (jar-filename version)]
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
  params)

(defn uberjar [{:keys [version] :or {version timestamp} :as params}]
  (let [uber-file (uber-filename version)]
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
  params)
