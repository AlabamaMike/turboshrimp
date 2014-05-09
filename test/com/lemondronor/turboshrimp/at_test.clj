(ns com.lemondronor.turboshrimp.at-test
  (:require [clojure.test :refer :all]
            [com.lemondronor.turboshrimp.at :as at]))


(deftest at-tests
  (testing "bit command vectors are translated into ints"
    (is (= (at/build-command-int [18 20 22 24 28]) 290717696))
    (is (= (at/build-command-int [9 18 20 22 24 28]) 290718208)))

  (testing "floats are cast to int"
    (is (= (int -1085485875)  (at/cast-float-to-int (float -0.8))))
    (is (= (int -1085485875) (at/cast-float-to-int (double -0.8)))))

  (testing "commands are build correctly"
    (are [x y] (= x y)
      (at/build-command :take-off 1) "AT*REF=1,290718208\r"
      (at/build-command :land 2) "AT*REF=2,290717696\r"
      (at/build-command :spin-right 3 0.5) "AT*PCMD=3,1,0,0,0,1056964608\r"
      (at/build-command :spin-left 3 0.8) "AT*PCMD=3,1,0,0,0,-1085485875\r"
      (at/build-command :up 3 0.5) "AT*PCMD=3,1,0,0,1056964608,0\r"
      (at/build-command :down 3 0.8) "AT*PCMD=3,1,0,0,-1085485875,0\r"
      (at/build-command :tilt-back 3 0.5) "AT*PCMD=3,1,0,1056964608,0,0\r"
      (at/build-command :tilt-front 3 0.8) "AT*PCMD=3,1,0,-1085485875,0,0\r"
      (at/build-command :tilt-right 3 0.5) "AT*PCMD=3,1,1056964608,0,0,0\r"
      (at/build-command :tilt-left 3 0.8) "AT*PCMD=3,1,-1085485875,0,0,0\r"
      (at/build-command :hover 3) "AT*PCMD=3,0,0,0,0,0\r"
      (at/build-command :fly 3 0.5 -0.8 0.5 -0.8)
      "AT*PCMD=3,1,1056964608,-1085485875,1056964608,-1085485875\r"
      (at/build-command :fly 3 0 0 0 0.5) (at/build-command :spin-right 3 0.5)
      (at/build-command :flat-trim 3) "AT*FTRIM=3,\r"
      (at/build-command :reset-watchdog 3) "AT*COMWDG=3,\r"
      (at/build-command :init-navdata 3)
      "AT*CONFIG=3,\"general:navdata_demo\",\"FALSE\"\r"
      (at/build-command :control-ack 3) "AT*CTRL=3,0\r"
      (at/build-command :init-targeting 3)
      "AT*CONFIG=3,\"detect:detect_type\",\"10\"\r"
      (at/build-command :target-shell-h 3)
      "AT*CONFIG=3,\"detect:detections_select_h\",\"32\"\r"
      (at/build-command :target-roundel-v 3)
      "AT*CONFIG=3,\"detect:detections_select_v_hsync\",\"128\"\r"
      (at/build-command :target-color-green 3)
      "AT*CONFIG=3,\"detect:enemy_colors\",\"1\"\r"
      (at/build-command :target-color-yellow 3)
      "AT*CONFIG=3,\"detect:enemy_colors\",\"2\"\r"
      (at/build-command :target-color-blue 3)
      "AT*CONFIG=3,\"detect:enemy_colors\",\"3\"\r")))
