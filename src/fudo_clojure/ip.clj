(ns fudo-clojure.ip
  (:import com.google.common.net.InetAddresses
           (java.net Inet4Address Inet6Address)))

(defn is-ipv4? [ip] (instance? Inet4Address ip))
(defn is-ipv6? [ip] (instance? Inet6Address ip))

(defprotocol IIPAddr
  (ipv4? [_])
  (ipv6? [_]))

(defrecord IPAddr [ip]
  IIPAddr
  (ipv4? [_] (is-ipv4? ip))
  (ipv6? [_] (is-ipv6? ip))

  Object
  (toString [_] (InetAddresses/toAddrString ip)))

(defn from-string [s]
  (->IPAddr (InetAddresses/forString s)))

(defn from-int [i]
  (->IPAddr (InetAddresses/fromInteger i)))
