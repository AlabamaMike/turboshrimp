(ns com.lemondronor.turboshrimp.navdata
  (:require [gloss.core :as gloss]
            gloss.io
            [hexdump.core :as hexdump])
  (:import (java.net DatagramPacket DatagramSocket InetAddress)
           (java.nio ByteBuffer ByteOrder)
           (java.util Arrays)))

(set! *warn-on-reflection* true)

(def stop-navstream (atom false))
(def log-data (atom [:seq-num :pstate :com-watchdog :communication
                     :control-state :roll :pitch :yaw :altitude]))
(defn end-navstream [] (reset! stop-navstream true))
(defn reset-navstream [] (reset! stop-navstream false))
(defn set-log-data [data] (reset! log-data data))


(def state-masks
  [ {:name :flying             :mask 0  :values [:landed :flying]}
    {:name :video              :mask 1  :values [:off :on]}
    {:name :vision             :mask 2  :values [:off :on]}
    {:name :control            :mask 3  :values [:euler-angles :angular-speed]}
    {:name :altitude-control   :mask 4  :values [:off :on]}
    {:name :user-feedback      :mask 5  :values [:off :on]}
    {:name :command-ack        :mask 6  :values [:none :received]}
    {:name :camera             :mask 7  :values [:not-ready :ready]}
    {:name :travelling         :mask 8  :values [:off :on]}
    {:name :usb                :mask 9  :values [:not-ready :ready]}
    {:name :demo               :mask 10 :values [:off :on]}
    {:name :bootstrap          :mask 11 :values [:off :on]}
    {:name :motors             :mask 12 :values [:ok :motor-problem]}
    {:name :communication      :mask 13 :values [:ok :communication-lost]}
    {:name :software           :mask 14 :values [:ok :software-fault]}
    {:name :battery            :mask 15 :values [:ok :too-low]}
    {:name :emergency-landing  :mask 16 :values [:off :on]}
    {:name :timer              :mask 17 :values [:not-elapsed :elapsed]}
    {:name :magneto            :mask 18 :values [:ok :needs-calibration]}
    {:name :angles             :mask 19 :values [:ok :out-of-range]}
    {:name :wind               :mask 20 :values [:ok :too-much]}
    {:name :ultrasound         :mask 21 :values [:ok :deaf]}
    {:name :cutout             :mask 22 :values [:ok :detected]}
    {:name :pic-version        :mask 23 :values [:bad-version :ok]}
    {:name :atcodec-thread     :mask 24 :values [:off :on]}
    {:name :navdata-thread     :mask 25 :values [:off :on]}
    {:name :video-thread       :mask 26 :values [:off :on]}
    {:name :acquisition-thread :mask 27 :values [:off :on]}
    {:name :ctrl-watchdog      :mask 28 :values [:ok :delay]}
    {:name :adc-watchdog       :mask 29 :values [:ok :delay]}
    {:name :com-watchdog       :mask 30 :values [:ok :problem]}
    {:name :emergency          :mask 31 :values [:ok :detected]}
    ])

(def control-states
  {0 :default, 1 :init, 2 :landed, 3 :flying, 4 :hovering, 5 :test,
   6 :trans-takeoff, 7 :trans-gotofix, 8 :trans-landing, 9 :trans-looping})

(def detection-types
  {0 :horizontal-deprecated,
   1 :vertical-deprecated,
   2 :horizontal-drone-shell
   3 :none-disabled
   4 :roundel-under-drone
   5 :oriented-roundel-under-drone
   6 :oriented-roundel-front-drone
   7 :stripe-ground
   8 :roundel-front-drone
   9 :stripe
   10 :multiple
   11 :cap-orange-green-front-drone
   12 :black-white-roundel
   13 :2nd-verion-shell-tag-front-drone
   14 :tower-side-front-camera})

(def camera-sources
  {0 :horizontal
   1 :vertical
   2 :vertical-hsync})

(defn parse-tag-detect [n]
  (when n
    (camera-sources (bit-shift-right n 16))))

(def detect-tag-types
  {0 :none
   6 :shell_tag_v2
   8 :black_roundel})

(defn tag-type-mask [type-num]
  (bit-shift-left 1 (dec type-num)))

(defn new-datagram-packet [^bytes data ^InetAddress host ^long port]
  (new DatagramPacket data (count data) host port))

(defn which-option-type [option]
  (case (int option)
    0 :demo
    1 :time
    2 :raw-measures
    16 :vision-detect
    26 :wifi
    27 :gps
    :unknown))

(gloss/defcodec vector3-codec
  (gloss/ordered-map
   :x :float32-le
   :y :float32-le
   :z :float32-le))

(gloss/defcodec matrix33-codec
  (gloss/ordered-map
   :m11 :float32-le
   :m12 :float32-le
   :m13 :float32-le
   :m21 :float32-le
   :m22 :float32-le
   :m23 :float32-le
   :m31 :float32-le
   :m32 :float32-le
   :m33 :float32-le))

(def vision-detect-codec
  (gloss/compile-frame
   (gloss/ordered-map
    :num-detected :uint32-le
    :type (repeat 4 :uint32-le)
    :xc (repeat 4 :uint32-le)
    :yc (repeat 4 :uint32-le)
    :width (repeat 4 :uint32-le)
    :height (repeat 4 :uint32-le)
    :dist (repeat 4 :uint32-le)
    :orientation-angle (repeat 4 :float32-le)
    :rotation (repeat 4 matrix33-codec)
    :translation (repeat 4 vector3-codec)
    :camera-source (repeat 4 :uint32-le))
   identity
   (fn [vision]
     (assoc vision
       :type (map detection-types (:type vision))
       :camera-source (map camera-sources (:camera-source vision))))))

(defn parse-vision-detect-option [bb]
  (gloss.io/decode vision-detect-codec bb))

(defn parse-control-state [v]
  (control-states (bit-shift-right v 16)))

(def demo-codec
  (gloss/compile-frame
   (gloss/ordered-map
    :control-state :uint32-le
    :battery-percentage :uint32-le
    :theta :float32-le
    :phi :float32-le
    :psi :float32-le
    :altitude :uint32-le
    :velocity vector3-codec
    :frame-index :uint32-le
    :detection (gloss/ordered-map
                :camera (gloss/ordered-map
                         :rotation matrix33-codec
                         :translation vector3-codec)
                :tag-index :uint32-le)
    :detect-camera-type :uint32-le
    :drone (gloss/ordered-map
            :camera (gloss/ordered-map
                     :rotation matrix33-codec
                     :translation vector3-codec)))
   identity
   (fn [demo]
     ;;(println "BEFORE COOKING" demo)
     (assoc demo
       :control-state (parse-control-state (:control-state demo))
       :theta (float (/ (:theta demo) 1000))
       :phi (float (/ (:phi demo) 1000))
       :psi (float (/ (:psi demo) 1000))
       :altitude (float (/ (:altitude demo) 1000))
       :detect-camera-type (detection-types (:detect-camera-type demo))))))

(defn parse-demo-option [bb]
  (gloss.io/decode demo-codec bb))


(defn >>> [x n]
  (bit-shift-right (bit-and 0xFFFFFFFF x) n))

(defn drone-time-to-seconds [time]
  (let [;; First 11 bits are seconds.
        seconds (>>> time 21)
        ;; Last 21 bits are microseconds.
        microseconds (>>> (bit-shift-left time 11) 11)]
    (+ seconds (/ microseconds 1000000.0))))


(def time-codec
  (gloss/compile-frame
   :uint32-le
   identity
   drone-time-to-seconds))

(defn parse-time-option [bb]
  (gloss.io/decode time-codec bb))

(def raw-measures-codec
  (gloss/compile-frame
   (gloss/ordered-map
    :accelerometers
    (gloss/ordered-map
     :x :uint16-le
     :y :uint16-le
     :z :uint16-le)
    :gyroscopes
     (gloss/ordered-map
      :x :int16-le
      :y :int16-le
      :z :int16-le)
     :gyroscopes-110
     (gloss/ordered-map
      :x :int16-le
      :y :int16-le)
     :battery-millivolts :uint32-le
     :us-echo
     (gloss/ordered-map
      :start :uint16-le
      :end :uint16-le
      :association :uint16-le
      :distance :uint16-le)
     :us-curve
     (gloss/ordered-map
      :time :uint16-le
      :value :uint16-le
      :ref :uint16-le)
     :echo
     (gloss/ordered-map
      :flag-ini :uint16-le
      :num :uint16-le
      :sum :uint32-le)
     :alt-temp-raw :int32-le
     :gradient :int16-le)))

(defn parse-raw-measures-option [bb]
  (gloss.io/decode raw-measures-codec bb))

(def wifi-codec
  (gloss/compile-frame
   :float32-le
   identity
   (fn [v]
     {:link-quality (- 1.0 v)})))


(defn parse-wifi-option [bb]
  (gloss.io/decode wifi-codec bb))

;; from https://github.com/paparazzi/paparazzi/blob/55e3d9d79119f81ed0b11a59487280becf13cf40/sw/airborne/boards/ardrone/at_com.h#L157

(def gps-sat-channel-codec
  (gloss/compile-frame
   (gloss/ordered-map
    :sat :ubyte
    :cn0 :unit8)))

(def gps-codec
  (gloss/compile-frame
   (gloss/ordered-map
    :latitude :float64-le
    :longitude :float64-le
    :elevation :float64-le
    :hdop :float64-le
    :data-available :int32-le
    :unk-0 (repeat 8 :ubyte)
    :lat0 :float64-le
    :lon0 :float64-le
    :lat-fuse :float64-le
    :lon-fuse :float64-le
    :gps-state :uint32-le
    :unk-1 (repeat 40 :ubyte)
    :vdop :float64-le
    :pdop :float64-le
    :speed :float32-le
    :last-frame-timestamp :uint32-le
    :degree :float32-le
    :degree-mag :float32-le
    :unk-2 (repeat 16 :ubyte)
    :channels (repeat 12 gps-sat-channel-codec)
    :gps-plugged :int32-le
    :unk-3 (repeat 108 :ubyte)
    :gps-time :float64-le
    :week :uint16-le
    :gps-fix :ubyte
    :num-satellites :ubyte
    :unk-4 (repeat 24 :ubyte)
    :ned-vel-c0 :float64-le
    :ned-vel-c1 :float64-le
    :ned-vel-c2 :float64-le
    :speed-accur :float32-le
    :time-accur :float32-le
    :unk-5 (repeat 72 :ubyte)
    :temperature :float32-le
    :pressure :float32-le
    :unk-6 (repeat 36 :ubyte))))

(defn parse-gps-option [bb]
  (gloss.io/decode gps-codec bb))

(defn parse-nav-state [state]
  (reduce
   #(let  [{:keys [name mask values]} %2
           bvalue (bit-and state (bit-shift-left 1 mask))]
      (conj %1 {name
                (if (= 0 bvalue) (first values) (last values))}))
   {}
   state-masks))

(def option-parsers
  {:demo parse-demo-option
   :time parse-time-option
   :raw-measures parse-raw-measures-option
   :vision-detect parse-vision-detect-option
   :gps parse-gps-option
   :wifi parse-wifi-option})

(defn parse-option [bb option-header]
  (let [option-type (which-option-type option-header)
        parser (option-parsers option-type)]
    (if (and option-type parser)
      (do
        (println "PARSING option" option-type)
        {option-type (parser bb)})
      (do
        (println "SKIPPING option" option-header)
        nil))))

(defn slice-byte-buffer [^ByteBuffer bb ^long offset ^long len]
  (let [ba ^"[B" (Arrays/copyOfRange
                  ^"[B" (.array bb)
                  offset
                  (+ offset len))]
    (ByteBuffer/wrap ba)))

(defn get-ushort [^ByteBuffer bb]
  (bit-and 0xFFFF (Integer. (int (.getShort bb)))))

(defn get-uint [^ByteBuffer bb]
  (bit-and 0xFFFFFFFF (Long. (long (.getInt bb)))))

(defn parse-options [^ByteBuffer bb options]
  (let [option-header (get-ushort bb)
        option-size (get-ushort bb)
        option (when-not (zero? option-size)
                 (let [^ByteBuffer opt-bb (slice-byte-buffer
                                           bb
                                           (.position bb)
                                           (- option-size 4))
                       opt (parse-option opt-bb option-header)]
                   ;;(when opt (println "NEW OPT" opt))
                   opt))
        new-options (merge options option)]
    (let [old-pos (.position bb)
          new-pos (+ old-pos (- option-size 4))]
      ;;(println "old-pos" old-pos "option-size" option-size "new-pos" new-pos)
      (.position bb new-pos))
    (if (or (zero? option-size) (zero? (.remaining bb)))
      (do
        ;;(println "returning options" new-options)
        new-options)
      (parse-options bb new-options))))

(def navdata-codec
  (gloss/compile-frame
   (gloss/ordered-map
    :header :uint32-le
    :state :uint32-le
    :seqnum :uint32-le
    :vision-flag :uint32-le)))

(defn parse-navdata [navdata-bytes]
  ;;(println "==================== parse-navdata")
  ;;(hexdump/hexdump (seq navdata-bytes))
  (let [^ByteBuffer bb (doto ^ByteBuffer (gloss.io/to-byte-buffer navdata-bytes)
                         (.order ByteOrder/LITTLE_ENDIAN))
        header (get-uint bb)
        state (get-uint bb)
        seqnum (get-uint bb)
        vision-flag (= (get-uint bb) 1)
        pstate (parse-nav-state state)
        ;; _ (println "header" header
        ;;            "state" state pstate
        ;;            "seqnum" seqnum
        ;;            "vision-flag" vision-flag)
        options (parse-options bb {})]
    (merge {:header header :seq-num seqnum :vision-flag vision-flag
            :state pstate}
           options)))

;;    (swap! navdata merge new-data)))

(defn send-navdata  [^DatagramSocket navdata-socket datagram-packet]
  (.send navdata-socket datagram-packet))

(defn receive-navdata  [^DatagramSocket navdata-socket datagram-packet]
  (.receive navdata-socket datagram-packet))

(defn get-navdata-bytes  [^DatagramPacket datagram-packet]
  (.getData datagram-packet))

(defn log-flight-data [navdata]
  (select-keys @navdata @log-data))
