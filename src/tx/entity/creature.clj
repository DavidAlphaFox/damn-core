(ns tx.entity.creature
  (:require [api.context :as ctx]
            [api.tx :refer [transact!]]
            [entity-state.player :as player-state]
            [entity-state.npc :as npc-state]))

; TODO @ properties.creature set optional/obligatory .... what is needed ???
; body
; skills
; mana
; stats (cast,attack-speed -> move to skills?)
; movement (if should move w. movement-vector ?!, otherwise still in 'moving' state ... )

; npc:
; reaction-time
; faction

; player:
; click-distance-tiles
; free-skill-points
; inventory
; item-on-cursor (added by itself)


;;;; add 'controller'
; :type controller/npc or controller/player
;;; dissoc here and assign components ....
; only npcs need reaction time ....

; TODO move to entity/state component, don'tneed to know about that here .... >
; but what about controller component stuff ?
; or entity/controller creates all of this ?
(defn- set-state [[player-or-npc initial-state]]
  ((case player-or-npc
     :state/player player-state/->state
     :state/npc npc-state/->state)
   initial-state))

; if controller = :controller/player
; -> add those fields
; :player? true ; -> api -> 'entity/player?' fn
; :free-skill-points 3
; :clickable {:type :clickable/player}
; :click-distance-tiles 1.5

; otherwise

(defmethod transact! :tx.entity/creature [[_ creature-id components] ctx]
  (assert (:entity/state components))
  (let [creature-components (:creature/entity (ctx/get-property ctx creature-id))]
    [[:tx/create
      (-> creature-components
          (update :entity/body assoc :position (:entity/position components)) ; give position separate arg?
          (merge (dissoc components :entity/position)
                 {:entity/z-order (if (:entity/flying? creature-components) ; do @ body
                                    :z-order/flying
                                    :z-order/ground)}
                 (when (= creature-id :creatures/lady-a) ; do @ ?
                   {:entity/clickable {:type :clickable/princess}}))
          (update :entity/state set-state))]])) ; do @ entity/state itself

(comment

 (set! *print-level* nil)
 (clojure.pprint/pprint
  (transact! [:tx.entity/creature :creatures/vampire
              {:entity/position [1 2]
               :entity/state [:state/npc :sleeping]}]
             (reify api.context/PropertyStore
               (get-property [_ id]
                 {:creature/entity {:entity/body {:width 5
                                                  :height 5}}}))))

 )
