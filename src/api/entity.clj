(ns api.entity
  (:require [core.component :refer [defsystem]]
            [utils.core :as utils]))

(defsystem create-component [_ components ctx])
(defsystem create           [_ entity* ctx])
(defsystem destroy          [_ entity* ctx])
(defsystem tick             [_ entity* ctx])

(def render-order (utils/define-order [:z-order/on-ground
                                       :z-order/ground
                                       :z-order/flying
                                       :z-order/effect]))
; TODO consolidate names, :z-order/debug missing,... (no its a system ...) ?

(defsystem render-below   [_ entity* g ctx])
(defsystem render-default [_ entity* g ctx])
(defsystem render-above   [_ entity* g ctx])
(defsystem render-info    [_ entity* g ctx])
(defsystem render-debug   [_ entity* g ctx])

(defrecord Entity [])

(defprotocol Body
  (position [_] "Center float coordinates.")
  (tile [_] "Center integer coordinates")
  (direction [_ other-entity*] "Returns direction vector from this entity to the other entity."))

(defprotocol State
  (state [_])
  (state-obj [_]))

(defprotocol Skills
  (has-skill? [_ skill]))

(defprotocol Faction
  (enemy-faction [_])
  (friendly-faction [_]))

(defprotocol Inventory
  (can-pickup-item? [_ item]))
