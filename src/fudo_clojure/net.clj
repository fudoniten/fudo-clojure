(ns fudo-clojure.net
  (:import org.apache.commons.net.util.SubnetUtils))

(defn ip-on-subnet? [subnet ip]
  (-> (SubnetUtils. subnet)
      (.getInfo)
      (.isInRange ip)))
