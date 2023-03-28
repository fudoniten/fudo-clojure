(ns fudo-clojure.certs
  (:require [clojure.java.io :as io])
  (:import java.io.StringReader
           java.io.FileInputStream
           java.io.DataInputStream
           java.io.ByteArrayInputStream
           java.security.KeyStore
           java.security.Security
           java.security.cert.CertificateFactory
           org.bouncycastle.openssl.PEMReader
           org.bouncycastle.jce.provider.BouncyCastleProvider))

(Security/addProvider (BouncyCastleProvider.))

(defn make-keystore
  ([] (make-keystore (KeyStore/getDefaultType)))
  ([store-type] (doto (KeyStore/getInstance store-type)
                  (.load nil))))

(defprotocol PemSource
  (make-pem-reader [source]))

(defn- read-certificate-data [filename]
  (let [ds (-> filename
               (FileInputStream.)
               (DataInputStream.))
        data (byte-array (.available ds))]
    (.readFully ds data)
    (ByteArrayInputStream. data)))

(defn- read-certificate [filename]
  (.generateCertificate (CertificateFactory/getInstance "X.509")
                        (read-certificate-data filename)))

(extend-protocol PemSource
  java.io.Reader
  (make-pem-reader [src] (PEMReader. src))
  String
  (make-pem-reader [src] (make-pem-reader (StringReader. src)))
  Object
  (make-pem-reader [src] (make-pem-reader (io/reader src))))

(defn import-certificates
  ([certmap]
   (import-certificates (make-keystore) certmap))
  ([keystore certmap]
   (doseq [[alias filename] certmap]
     (.setCertificateEntry keystore (name alias) (read-certificate filename)))
   keystore))
