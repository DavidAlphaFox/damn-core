(ns entity.faction
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.effect :as effect]
            [api.entity :as entity]
            [api.tx :refer [transact!]]))

(defcomponent :entity/faction (data/enum :good :evil)
  (entity/info-text [[_ faction] _ctx]
    (str "[SLATE]Faction: " (name faction) "[]")))

(extend-type api.entity.Entity
  entity/Faction
  (enemy-faction [{:keys [entity/faction]}]
    (case faction
      :evil :good
      :good :evil))

  (friendly-faction [{:keys [entity/faction]}]
    faction))

(defcomponent :effect/convert data/boolean-attr
  (effect/text [_ _effect-ctx]
    "Converts target to your side.")

  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and target
         (= (:entity/faction @target)
            (entity/enemy-faction @source))))

  (transact! [_ {:keys [effect/source effect/target]}]
    [[:tx.entity/assoc target :entity/faction (entity/friendly-faction @source)]]))
