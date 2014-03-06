(ns com.lemondronor.turboshrimp.navdata-test
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.test :refer :all]
            [com.lemonodor.xio :as xio]
            [criterium.core :as criterium]
            [gloss.io]
            [midje.sweet :refer :all]
            [com.lemondronor.turboshrimp.navdata :refer :all]
            [com.lemondronor.turboshrimp :refer :all])
  (:import (java.net InetAddress DatagramSocket)
           (java.nio ByteOrder)))

;; matrix 33 is 9 floats
;; vector 31 is 3 floats
(def b-matrix33  (vec (repeat (* 9 4) 0 )))
(def b-vector31  (vec (repeat (* 3 4) 0 )))
(* 12 4)

(def b-header [-120 119 102 85])
(def b-state [-48 4 -128 15])
(def b-seqnum [102 3 0 0])
(def b-vision [0 0 0 0])
(def b-demo-option-id [0 0])
(def b-demo-option-size [-108 0])
(def b-demo-control-state [0 0 2 0])
(def b-demo-battery [100 0 0 0])
(def b-demo-pitch [0 96 -122 -60])
(def b-demo-roll [0 -128 53 -59])
(def b-demo-yaw [0 0 87 -61])
(def b-demo-altitude [0 0 0 0])
(def b-demo-velocity-x [0 0 0 0])
(def b-demo-velocity-y [0 0 0 0])
(def b-demo-velocity-z [0 0 0 0])
(def b-demo-num-frames [0 0 0 0])
(def b-demo-detect-camera-rot b-matrix33)
(def b-demo-detect-camera-trans b-vector31)
(def b-demo-detect-tag-index [0 0 0 0])
(def b-demo-detect-camera-type [4 0 0 0])
(def b-demo-drone-camera-rot b-matrix33)
(def b-demo-drone-camera-trans b-vector31)
(def b-demo-option (flatten (conj b-demo-option-id b-demo-option-size
                                  b-demo-control-state b-demo-battery
                                  b-demo-pitch b-demo-roll b-demo-yaw
                                  b-demo-altitude b-demo-velocity-x
                                  b-demo-velocity-y b-demo-velocity-z
                                  b-demo-num-frames
                                  b-demo-detect-camera-rot b-demo-detect-camera-trans
                                  b-demo-detect-tag-index
                                  b-demo-detect-camera-type b-demo-drone-camera-rot
                                  b-demo-drone-camera-trans)))
(def b-vision-detect-option-id [16 0])
(def b-vision-detect-option-size [72 1])
(def b-vision-detect-num-tags-detected [2 0 0 0])
(def b-vision-detect-type [1 0 0 0 2 0 0 0 3 0 0 0 4 0 0 0])
(def b-vision-detect-xc [1 0 0 0 2 0 0 0 3 0 0 0 4 0 0 0])
(def b-vision-detect-yc [1 0 0 0 2 0 0 0 3 0 0 0 4 0 0 0])
(def b-vision-detect-width [1 0 0 0 2 0 0 0 3 0 0 0 4 0 0 0])
(def b-vision-detect-height [1 0 0 0 2 0 0 0 3 0 0 0 4 0 0 0])
(def b-vision-detect-dist [1 0 0 0 2 0 0 0 3 0 0 0 4 0 0 0])
(def b-vision-detect-orient-angle [0 96 -122 -60 0 96 -122 -60 0 96 -122 -60 0 96 -122 -60])
(def b-vision-detect-rotation (flatten (conj b-matrix33  b-matrix33  b-matrix33  b-matrix33)))
(def b-vision-detect-translation (flatten (conj b-vector31 b-vector31 b-vector31 b-vector31)))
(def b-vision-detect-camera-source [1 0 0 0 2 0 0 0 2 0 0 0 2 0 0 0])
(def b-vision-detect-option (flatten (conj b-vision-detect-option-id b-vision-detect-option-size
                                    b-vision-detect-num-tags-detected
                                    b-vision-detect-type b-vision-detect-xc b-vision-detect-yc
                                    b-vision-detect-width b-vision-detect-height b-vision-detect-dist
                                    b-vision-detect-orient-angle b-vision-detect-rotation b-vision-detect-translation
                                    b-vision-detect-camera-source)))

(def b-options (flatten (conj b-demo-option b-vision-detect-option)))
(def header (map byte [-120 119 102 85]))
(def nav-input  (byte-array (map byte (flatten (conj b-header b-state b-seqnum b-vision b-demo-option b-vision-detect-option)))))
(def host (InetAddress/getByName "192.168.1.1"))
(def port 5554)
(def socket (DatagramSocket. ))
(def packet (new-datagram-packet (byte-array 2048) host port))


(deftest navdata-tests
  (facts "about new-datagram-packet"
    (fact "getPort/getAddress/getData"
      (let [data (byte-array (map byte [1 0 0 0]))
            ndp (new-datagram-packet data host port)]
        (.getPort ndp) => port
        (.getAddress ndp) => host
        (.getData ndp) => data)))

  (facts "about parse-control-state"
    (fact "parse-control-state"
      (let [bb (doto (gloss.io/to-byte-buffer b-demo-control-state)
                            (.order ByteOrder/LITTLE_ENDIAN))
            control-state (.getInt bb)]
        (parse-control-state control-state) => :landed)))

  (facts "about parse-demo-option"
    (fact "parse-demo-option"
      (let [bb (doto (gloss.io/to-byte-buffer
                      ;; Skip past the option ID and option size.
                      (drop 4 b-demo-option))
                 (.order ByteOrder/LITTLE_ENDIAN))
            option (parse-demo-option bb)]
        (:control-state option) => :landed
        (:battery-percentage option) => 100
        option => (contains {:theta (float -1.075) })
        option => (contains {:phi (float -2.904) })
        option => (contains {:psi (float -0.215) })
        option => (contains {:altitude (float 0.0) })
        option => (contains {:velocity {:x (float 0.0)
                                        :y (float 0.0)
                                        :z (float 0.0)}})
        option => (contains {:detect-camera-type :roundel-under-drone }))))


  (facts "about parse-navdata"
    (fact "parse-navdata"
      (fact "hand-crafted input"
        (let [navdata (parse-navdata nav-input)]
          navdata => (contains {:header 0x55667788})
          navdata => (contains {:seq-num 870})
          navdata => (contains {:vision-flag false})
          (fact "state"
            (let [state (:state navdata)]
              state => (contains {:battery :ok})
              state => (contains {:flying :landed})))
          (fact "demo"
            (let [demo (:demo navdata)]
              (:control-state demo) => :landed
              (:battery-percentage demo) => 100
              demo => (contains {:theta (float -1.075) })
              demo => (contains {:phi (float -2.904) })
              demo => (contains {:psi (float -0.215) })
              demo => (contains {:altitude (float 0.0) })
              demo => (contains {:velocity
                                 {:x (float 0.0)
                                  :y (float 0.0)
                                  :z (float 0.0)}})))))
      (fact "Reading specimen navdata"
        (let [navdata-bytes (xio/binary-slurp (io/resource "navdata.bin"))]
          ;;(println "Benchmarking parse-navdata")
          ;;(criterium/bench (parse-navdata navdata-bytes))
          (let [navdata (parse-navdata navdata-bytes)]
            (fact "navdata"
              (:header navdata) => 0x55667788
              (:seq-num navdata) => 300711
              (:vision-flag navdata) => true)
            (fact "state"
              (let [state (:state navdata)]
                state => (contains {:flying :landed})
                state => (contains {:video :off})
                state => (contains {:vision :off})
                state => (contains {:altitude-control :on})
                state => (contains {:command-ack :received})
                state => (contains {:camera :ready})
                state => (contains {:travelling :off})
                state => (contains {:usb :not-ready})
                state => (contains {:demo :off})
                state => (contains {:bootstrap :off})
                state => (contains {:motors :ok})
                state => (contains {:communication :ok})
                state => (contains {:software :ok})
                state => (contains {:bootstrap :off})
                state => (contains {:battery :ok})
                state => (contains {:emergency-landing :off})
                state => (contains {:timer :not-elapsed})
                state => (contains {:magneto :ok})
                state => (contains {:angles :ok})
                state => (contains {:wind :ok})
                state => (contains {:ultrasound :ok})
                state => (contains {:cutout :ok})
                state => (contains {:pic-version :ok})
                state => (contains {:atcodec-thread :on})
                state => (contains {:navdata-thread :on})
                state => (contains {:video-thread :on})
                state => (contains {:acquisition-thread :on})
                state => (contains {:ctrl-watchdog :ok})
                state => (contains {:adc-watchdog :ok})
                state => (contains {:com-watchdog :problem})
                state => (contains {:emergency-landing :off})))
            (fact "time"
              (:time navdata) => 362.979125)
            (fact "raw-measures"
              (let [raw-meas (:raw-measures navdata)]
                (:accelerometers raw-meas) => {:x 2040
                                               :y 2036
                                               :z 2528}
                (:gyroscopes raw-meas) => {:x -23
                                           :y 15
                                           :z 0}
                (:gyroscopes-110 raw-meas) => {:x 0
                                               :y 0}
                (:battery-millivolts raw-meas) => 11686
                (:us-echo raw-meas) => {:start 0
                                        :end 0
                                        :association 3758
                                        :distance 0}
                (:us-curve raw-meas) => {:time 21423
                                         :value 0
                                         :ref 120}
                (:echo raw-meas) => {:flag-ini 1
                                     :num 1
                                     :sum 3539193}
                (:alt-temp-raw raw-meas) => 243
                (:gradient raw-meas) => 41))
            (fact "phys measures"
              (let [phys-meas (:phys-measures navdata)]
                (:temperature phys-meas) => {:accelerometer 45.309303283691406
                                             :gyroscope 55738}
                (:accelerometers phys-meas) => {:x 80.2970962524414
                                                :y -33.318603515625
                                                :z -942.5283203125}
                (:gyroscopes phys-meas) => {:x -0.11236488074064255
                                            :y 0.06872134655714035
                                            :z 0.06200997903943062}
                (:alim3v3 phys-meas) => 0
                (:vref-epson phys-meas) => 0
                (:vref-idg phys-meas) => 0))
            (fact "wifi"
              (let [wifi (:wifi navdata)]
                (:link-quality wifi) => 1.0))
            (fact "demo"
              (let [demo (:demo navdata)]
                (:control-state demo) => :landed
                (:battery-percentage demo) => 50
                (:theta demo) => (float 2.974)
                (:phi demo) => (float 0.55)
                (:psi demo) => (float 1.933)
                (:altitude demo) => 0.0
                (:velocity demo) => {:x 0.0585307739675045
                                     :y -0.8817979097366333
                                     :z 0.0}))
            (fact "gps"
              (let [gps (:gps navdata)]
                (:latitude gps) => 34.0903478
                ;;(:longitude gps) => 0
                (:elevation gps) => 130.39
                (:lat0 gps) => 34.090359093568644
                (:lon0 gps) => -118.276604
                (:lat-fuse gps) => 34.09035909403431
                (:lon-fuse gps) => -118.276604
                (:pdop gps) => 0.0
                (:speed gps) => 0.4399999976158142
                (:last-frame-timestamp gps) => 1816.647945
                (:degree gps) => 170.16000366210938
                (:degree-mag gps) => 0.0
                (:channels gps) => [{:sat 22 :cn0 36}
                                    {:sat 15 :cn0 17}
                                    {:sat 11 :cn0 227}
                                    {:sat 11 :cn0 227}
                                    {:sat 18 :cn0 27}
                                    {:sat 29 :cn0 16}
                                    {:sat 21 :cn0 22}
                                    {:sat 16 :cn0 0}
                                    {:sat 27 :cn0 0}
                                    {:sat 30 :cn0 0}
                                    {:sat 12 :cn0 227}
                                    {:sat 12 :cn0 227}]
                (:gps-plugged gps) => 1
                (:gps-time gps) => 0.0
                (:week gps) => 0
                (:gps-fix gps) => 0
                (:num-satellites gps) => 0))
            (fact "vision-detect"
              (let [vd (:vision-detect navdata)]
                (:num-detected vd) => 0)))))))

  (facts "about stream-navdata"
    (fact "stream-navdata"
      (stream-navdata nil socket packet) => anything
      (provided
        (receive-navdata anything anything) => 1
        (get-nav-data :default) => (:nav-data (:default @drones))
        (get-navdata-bytes anything) => nav-input
        (get-ip-from-packet anything) => "192.168.1.1")
      (against-background
        (before :facts (do
                         (reset! drones {:default {:nav-data (atom {})
                                                   :host (InetAddress/getByName "192.168.1.1")
                                                   :current-belief (atom "None")
                                                   :current-goal (atom "None")
                                                   :current-goal-list (atom [])}})
                         (reset! stop-navstream true))))))


  (facts "about parse-nav-state"
    (fact "parse-nav-state"
      (let [ state 260048080
            result (parse-nav-state state)
            {:keys [ flying video vision control altitude-control
                    user-feedback command-ack camera travelling
                    usb demo bootstrap motors communication
                    software battery emergency-landing timer
                    magneto angles wind ultrasound cutout
                    pic-version atcodec-thread navdata-thread
                    video-thread acquisition-thread ctrl-watchdog
                    adc-watchdog com-watchdog emergency]} result]
        flying => :landed
        video => :off
        vision => :off
        control => :euler-angles
        altitude-control => :on
        user-feedback => :off
        command-ack => :received
        camera => :ready
        travelling => :off
        usb => :not-ready
        demo => :on
        bootstrap => :off
        motors => :ok
        communication => :ok
        software => :ok
        battery => :ok
        emergency-landing => :off
        timer => :not-elapsed
        magneto => :ok
        angles => :ok
        wind => :ok
        ultrasound => :ok
        cutout => :ok
        pic-version => :ok
        atcodec-thread => :on
        navdata-thread => :on
        video-thread => :on
        acquisition-thread => :on
        ctrl-watchdog => :ok
        adc-watchdog => :ok
        com-watchdog => :ok
        emergency => :ok)))

  (facts "about which-option-type"
    (fact "which-option-type"
      (which-option-type 0) => :demo
      (which-option-type 16) => :vision-detect
      (which-option-type 2342342) => :unknown))

  (facts "about parse-tag-detect"
    (fact "parse-tag-detect"
      (parse-tag-detect 131072) => :vertical-hsync))

  ;; (facts "about parse-vision-detect-tag"
  ;;   (fact "about parse-vision-detect-tag with the first vision-detect"
  ;;     (let [tag (parse-vision-detect-tag (map byte b-vision-detect-option) 0 0)]
  ;;       tag => (contains {:target-type :horizontal})
  ;;       tag => (contains {:target-xc 1})
  ;;       tag => (contains {:target-yc 1})
  ;;       tag => (contains {:target-width 1})
  ;;       tag => (contains {:target-height 1})
  ;;       tag => (contains {:target-dist 1})
  ;;       tag => (contains {:target-orient-angle -1075.0})
  ;;       tag => (contains {:target-camera-source :vertical})))
  ;;   (fact "about parse-vision-detect-tag with the second vision-detect"
  ;;     (let [tag (parse-vision-detect-tag (map byte b-vision-detect-option) 0 1)]
  ;;       tag => (contains {:target-type :horizontal})
  ;;       tag => (contains {:target-xc 2})
  ;;       tag => (contains {:target-yc 2})
  ;;       tag => (contains {:target-width 2})
  ;;       tag => (contains {:target-height 2})
  ;;       tag => (contains {:target-dist 2})
  ;;       tag => (contains {:target-orient-angle -1075.0})
  ;;       tag => (contains {:target-camera-source :vertical-hsync})))
  ;;   (fact "about parse-vision-detect-tag with the third vision-detect"
  ;;     (let [tag (parse-vision-detect-tag (map byte b-vision-detect-option) 0 2)]
  ;;       tag => (contains {:target-type :horizontal})
  ;;       tag => (contains {:target-xc 3})
  ;;       tag => (contains {:target-yc 3})
  ;;       tag => (contains {:target-width 3})
  ;;       tag => (contains {:target-height 3})
  ;;       tag => (contains {:target-dist 3})
  ;;       tag => (contains {:target-orient-angle -1075.0})
  ;;       tag => (contains {:target-camera-source :vertical-hsync})))
  ;;   (fact "about parse-vision-detect-tag with the fourth vision-detect"
  ;;     (let [tag (parse-vision-detect-tag (map byte b-vision-detect-option) 0 3)]
  ;;       tag => (contains {:target-type :horizontal})
  ;;       tag => (contains {:target-xc 4})
  ;;       tag => (contains {:target-yc 4})
  ;;       tag => (contains {:target-width 4})
  ;;       tag => (contains {:target-height 4})
  ;;       tag => (contains {:target-dist 4})
  ;;       tag => (contains {:target-orient-angle -1075.0})
  ;;       tag => (contains {:target-camera-source :vertical-hsync}))))

  (facts "about parse-vision-detect-option"
    (fact "parse-vision-detect-option"
      (let [bb (doto (gloss.io/to-byte-buffer
                      ;; Skip past the option ID and option size.
                      (drop 4 b-vision-detect-option))
                 (.order ByteOrder/LITTLE_ENDIAN))
            option (parse-vision-detect-option bb)]
        option => (contains {:num-detected 2})
        option => (contains
                   {:type [:vertical-deprecated
                           :horizontal-drone-shell
                           :none-disabled
                           :roundel-under-drone]})
        option => (contains {:xc [1 2 3 4]})
        option => (contains {:yc [1 2 3 4]})
        option => (contains {:width [1 2 3 4]})
        option => (contains {:height [1 2 3 4]})
        option => (contains {:dist [1 2 3 4]})
        option => (contains {:orientation-angle [-1075.0 -1075.0 -1075.0 -1075.0]})
        option => (contains
                   {:rotation
                    [{:m11 0.0, :m12 0.0, :m13 0.0,
                      :m21 0.0, :m22 0.0, :m23 0.0,
                      :m31 0.0, :m32 0.0, :m33 0.0}
                     {:m11 0.0, :m12 0.0, :m13 0.0,
                      :m21 0.0, :m22 0.0, :m23 0.0,
                      :m31 0.0, :m32 0.0, :m33 0.0}
                     {:m11 0.0, :m12 0.0, :m13 0.0,
                      :m21 0.0, :m22 0.0, :m23 0.0,
                      :m31 0.0, :m32 0.0, :m33 0.0}
                     {:m11 0.0, :m12 0.0, :m13 0.0,
                      :m21 0.0, :m22 0.0, :m23 0.0,
                      :m31 0.0, :m32 0.0, :m33 0.0}]})
        option => (contains
                   {:translation
                    [{:x 0.0, :y 0.0, :z 0.0}
                     {:x 0.0, :y 0.0, :z 0.0}
                     {:x 0.0, :y 0.0, :z 0.0}
                     {:x 0.0, :y 0.0, :z 0.0}]})
        option => (contains
                   {:camera-source
                    [:vertical :vertical-hsync :vertical-hsync :vertical-hsync]})


        ;;(count vision-detects) => 2
        ;;(first vision-detects) => (contains {:vision-detect-type :horizontal})
        )))

  ;; (facts "about parse-options"
  ;;   (fact "about parse-options with demo"
  ;;     (let [option (parse-options b-demo-option 0 {})]
  ;;       option => (contains {:control-state :landed})))
  ;;   (fact "about parse option with targets"
  ;;     (let [option (parse-options b-target-option 0 {})]
  ;;       option => (contains {:targets-num 2})))
  ;;   (fact "about parse-options with demo and targets"
  ;;     (let [options (parse-options nav-input 16 {})]
  ;;       options => (contains {:control-state :landed})
  ;;       options => (contains {:targets-num 2}))))
  )