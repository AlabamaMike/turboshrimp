(ns com.lemondronor.turboshrimp
  "Control and telemetry library for the AR.Drone."
  (:require [clojure.tools.logging :as log]
            [com.lemondronor.turboshrimp.at :as at]
            [com.lemondronor.turboshrimp.navdata :as navdata])
  (:import (java.io IOException)
           (java.net DatagramPacket DatagramSocket InetAddress)))

(set! *warn-on-reflection* true)


;; The default drone hostname/IP address.
(def default-hostname "192.168.1.1")

;; The default port to use for AT control commands.
(def default-at-port 5556)

;; The default port to connect to for navdata.
(def navdata-port 5554)

;; The default drone communication timeout, in milliseconds.
(def socket-timeout (atom 60000))


;; This record represents a drone.  The connected?, counter,
;; keep-streaming-navdata and navdata-handler fields are atoms.

(defrecord Drone
    [name
     host
     at-port
     counter
     at-socket
     keep-streaming-navdata
     connected?
     event-handler
     navdata-handler
     navdata-socket
     nav-data])


(defn drone-ip [drone]
   (.getHostName ^InetAddress (:host drone)))

(defmethod print-method Drone [d ^java.io.Writer w]
  (.write w (str "#<Drone "
                 (drone-ip d) " "
                 (if @(:connected? d)
                   "[connected]"
                   "[not connected]")
                 ">")))


(defn make-drone
  "Creates a drone object."
  [& options]
  (let [{:keys [name hostname at-port event-handler]} options
        name (or name :default)]
    (map->Drone
     {:name name
      :connected? (atom false)
      :event-handler event-handler
      :host (InetAddress/getByName
             (or hostname default-hostname))
      :at-port (or at-port default-at-port)
      :counter (atom 0)
      :at-socket (DatagramSocket.)
      :keep-streaming-navdata (atom false)
      :navdata-handler (atom nil)
      :navdata-socket (DatagramSocket.)
      :nav-data (atom {})})))

(defn send-at-command [drone ^String data]
  (let [^InetAddress host (:host drone)
        ^Long at-port (:at-port drone)
        ^DatagramSocket at-socket (:at-socket drone)]
    (log/info "Sending command to" drone data)
    (.send at-socket
           (new DatagramPacket (.getBytes data) (.length data) host at-port))))

(defn command [drone command-key & [w x y z]]
  (let [counter (:counter drone)
        seq-num (swap! counter inc)
        data (at/build-command command-key seq-num w x y z)]
    (send-at-command drone data)))

(defn- navdata-error-handler [drone]
  (fn [agent exception]
    (log/error exception "Exception in navdata agent for" drone)
    (when-let [event-handler (:event-handler drone)]
      (event-handler :error drone exception))))

(defn send-datagram
  [^DatagramSocket socket ^InetAddress host ^long port ^"[B" data-bytes]
  (let [^DatagramPacket packet (DatagramPacket. data-bytes (count data-bytes) host port)]
    (.send socket packet)))


(defn raise-event [drone event-type & args]
  (log/debug "Event" event-type args)
  (when-let [handler (:event-handler drone)]
    (apply handler event-type args)))

(defn communication-check [drone navdata]
  (when (= :problem (-> navdata :state :com-watchdog))
    (log/info "Watchdog Reset")
    (command drone :reset-watchdog)))

(defn process-navdata [drone navdata]
  (communication-check drone navdata)
  (raise-event drone :navdata navdata))

(defn- navdata-thread-fn [drone]
  (log/info "Running navdata thread for" drone)
  (let [^DatagramSocket socket (:navdata-socket drone)
        ^DatagramPacket packet (navdata/new-datagram-packet
                                (byte-array 2048) (:host drone) navdata-port)]
    (loop []
      (if @(:keep-streaming-navdata drone)
        (do
          (try
            (do
              (navdata/receive-navdata socket packet)
              (log/debug packet)
              (process-navdata drone (navdata/parse-navdata (.getData packet))))
            (catch IOException e
              (log/error e "Whoa")
              (reset! (:keep-streaming-navdata drone) false)
              (raise-event drone :error e)))
          (recur))
        (do
          (log/info "Disconnecting thread for" drone))))))

(defn- start-navdata! [drone]
  (reset! (:keep-streaming-navdata drone) true)
  (doto (Thread.
         (fn [] (navdata-thread-fn drone))
         (str "navdata thread for drone " (:name drone)))
    (.setDaemon true)
    (.start))
  (send-datagram (:navdata-socket drone) (:host drone) navdata-port
                 (byte-array (map byte [1 0 0 0]))))


(defn connect! [drone]
  (start-navdata! drone)
  (command drone :flat-trim)
  drone)

(defn drone-do-for [drone seconds command-key & [w x y z]]
  (when (pos? seconds)
    (command drone command-key w x y z)
    (Thread/sleep 30)
    (recur drone (- seconds 0.03) command-key [w x y z])))
