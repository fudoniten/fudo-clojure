(ns fudo-clojure.ip
  (:require [fudo-clojure.net :as net])
  (:import com.google.common.net.InetAddresses
           (java.net Inet4Address Inet6Address NetworkInterface)))

(defn- ipv4-impl? [ip] (instance? Inet4Address ip))
(defn- ipv6-impl? [ip] (instance? Inet6Address ip))

(defn- is-tailscale-ip? [ip]
  (net/ip-on-subnet? "100.64.0.0/10" (str ip)))

(defn- public-impl? [ip]
  (and (not (.isLinkLocalAddress ip))
       (not (.isLoopbackAddress ip))
       (not (.isSiteLocalAddress ip))
       (not (is-tailscale-ip? (InetAddresses/toAddrString ip)))))

(defprotocol IIPAddr
  (ipv4?    [_])
  (ipv6?    [_])
  (public?  [_])
  (private? [_]))

(defrecord IPAddr [ip]
  IIPAddr
  (ipv4?    [_] (ipv4-impl? ip))
  (ipv6?    [_] (ipv6-impl? ip))
  (public?  [_] (public-impl? ip))
  (private? [_] (not (public? ip)))

  Object
  (toString [_] (InetAddresses/toAddrString ip)))

(defn from-string [s]
  (->IPAddr (InetAddresses/forString s)))

(defn from-int [i]
  (->IPAddr (InetAddresses/fromInteger i)))

(defn get-host-ips []
  (->> (NetworkInterface/getNetworkInterfaces)
       (enumeration-seq)
       (mapcat #(enumeration-seq (.getInetAddresses %)))
       (map #(IPAddr. %))))
