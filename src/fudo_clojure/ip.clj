(ns fudo-clojure.ip
  (:require [fudo-clojure.net :as net]
            [clojure.string :as str]
            [clojure.math :as math])
  (:import com.google.common.net.InetAddresses
           (java.net Inet4Address Inet6Address NetworkInterface InetAddress)))

(defn- is-ipv4? [ip] (instance? Inet4Address ip))
(defn- is-ipv6? [ip] (instance? Inet6Address ip))

(defn- ip->int [ip]
  (->> (InetAddresses/forString ip)
       (.getAddress)
       (reverse)
       (zipmap (range))
       (map (fn [[i n]] (.shiftLeft (biginteger n) (* i 8))))
       (apply +)))

(defn- int->ipv6 [n]
  (InetAddresses/toAddrString
   (Inet6Address/getByAddress "" (.toByteArray (biginteger n)))))

(defn- int->ipv4 [n]
  (InetAddresses/toAddrString
   (Inet4Address/getByAddress "" (.toByteArray (biginteger n)))))

(defn- ipv4->int [ip]
  (->> (str/split ip #"\.")
       (map #(Integer/parseInt %))
       (reverse)
       (zipmap (range))
       (map (fn [[i n]] (bit-shift-left n (* i 8))))
       (apply bit-or)))

(defn- network-max-ip [net]
  (let [[ip mask-str] (str/split net #"/")
        mask          (Integer/parseInt mask-str)
        total-bytes   (if (is-ipv4? (InetAddresses/forString ip)) 32 128)]
    (.or (.subtract (biginteger (math/pow 2 (- total-bytes mask)))
                    (biginteger 1))
         (biginteger (ip->int ip)))))

(defn- network-min-ip [net]
  (let [[ip mask-str] (str/split net #"/")
        mask          (Integer/parseInt mask-str)
        total-bytes   (if (is-ipv4? (InetAddresses/forString ip)) 32 128)
        shift         (- total-bytes mask)]
    (-> (ip->int ip)
        (biginteger)
        (.shiftRight shift)
        (.shiftLeft shift))))

(defn- network-v4-min-ip [net]
  (let [[ip mask-str] (str/split net #"/")
        mask          (Integer/parseInt mask-str)
        shift         (- 32 mask)]
    (-> ip
        (ipv4->int)
        (bit-shift-left shift)
        (unsigned-bit-shift-right shift)
        (int->ipv4))))

(defn- network-range [subnet]
  [(network-min-ip subnet) (network-max-ip subnet)])

(defn- ip-on-subnet? [ip subnet]
  (let [[min max] (network-range subnet)]
    (<= min (ip->int ip) max)))

(defn- ip-tailscale? [ip]
  (or (ip-on-subnet? (str ip) "100.64.0.0/10")
      (ip-on-subnet? (str ip) "fd7a:115c:a1e0::/96")))

(defn- ip-public? [ip]
  (and (not (.isLinkLocalAddress ip))
       (not (.isLoopbackAddress ip))
       (not (.isSiteLocalAddress ip))
       (not (ip-tailscale? (InetAddresses/toAddrString ip)))))

(defn- ip-sitelocal? [ip]
  (.isSiteLocalAddress ip))

(defprotocol IIPAddr
  (ipv4?      [_])
  (ipv6?      [_])
  (public?    [_])
  (private?   [_])
  (tailscale? [_])
  (sitelocal? [_])
  (on-subnet? [_ subnet]))

(defrecord IPAddr [ip]
  IIPAddr
  (ipv4?      [_]        (is-ipv4? ip))
  (ipv6?      [_]        (is-ipv6? ip))
  (public?    [_]        (ip-public? ip))
  (private?   [self]     (not (public? self)))
  (tailscale? [_]        (ip-tailscale? (InetAddresses/toAddrString ip)))
  (sitelocal? [_]        (ip-sitelocal? ip))
  (on-subnet? [_ subnet] (ip-on-subnet? ip subnet))

  Object
  (toString [_] (InetAddresses/toAddrString ip)))

(defn from-string [s]
  (->IPAddr (InetAddresses/forString s)))

(defn from-int [i]
  (->IPAddr (InetAddresses/fromInteger i)))

(defn- from-addr [addr] (->IPAddr addr))

(defn get-host-ips []
  (->> (NetworkInterface/getNetworkInterfaces)
       (enumeration-seq)
       (mapcat #(enumeration-seq (.getInetAddresses %)))
       (map from-addr)))
