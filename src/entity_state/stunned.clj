(ns entity-state.stunned
  (:require [api.graphics :as g]
            [api.context :refer [stopped? ->counter]]
            [api.entity :as entity]
            [api.entity-state :as state]))

(defrecord Stunned [eid counter]
  state/PlayerState
  (player-enter [_] [[:tx.context.cursor/set :cursors/denied]])
  (pause-game? [_] false)
  (manual-tick [_ _entity* context])
  (clicked-inventory-cell [_ entity* cell])
  (clicked-skillmenu-skill [_ entity* skill])

  state/State
  (enter [_ _ctx])
  (exit  [_ _ctx])
  (tick [_ ctx]
    (when (stopped? ctx counter)
      [[:tx/event eid :effect-wears-off]]))

  (render-below [_ entity* g _ctx]
    (g/draw-circle g (:position entity*) 0.5 [1 1 1 0.6]))
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))

(defn ->build [ctx eid duration]
  (->Stunned eid (->counter ctx duration)))
