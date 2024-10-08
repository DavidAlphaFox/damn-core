(ns core.entity.inventory
  (:require [data.grid2d :as grid2d]
            [core.utils.core :refer [find-first]]
            [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]))

(def empty-inventory
  (->> #:inventory.slot{:bag      [6 4]
                        :weapon   [1 1]
                        :shield   [1 1]
                        :helm     [1 1]
                        :chest    [1 1]
                        :leg      [1 1]
                        :glove    [1 1]
                        :boot     [1 1]
                        :cloak    [1 1]
                        :necklace [1 1]
                        :rings    [2 1]}
       (map (fn [[slot [width height]]]
              [slot (grid2d/create-grid width height (constantly nil))]))
       (into {})))

(defn cells-and-items [inventory slot]
  (for [[position item] (slot inventory)]
    [[slot position] item]))

(defn valid-slot? [[slot _] item]
  (or (= :inventory.slot/bag slot)
      (= (:item/slot item) slot)))

(defn applies-modifiers? [[slot _]]
  (not= :inventory.slot/bag slot))

(defn stackable? [item-a item-b]
  (and (:count item-a)
       (:count item-b) ; this is not required but can be asserted, all of one name should have count if others have count
       (= (:property/id item-a) (:property/id item-b))))

(defn- set-item [{:keys [entity/id] :as entity*} cell item]
  (let [inventory (:entity/inventory entity*)]
    (assert (and (nil? (get-in inventory cell))
                 (valid-slot? cell item))))
  [[:e/assoc-in id (cons :entity/inventory cell) item]
   (when (applies-modifiers? cell)
     [:tx/apply-modifiers id (:item/modifiers item)])
   (when (:entity/player? entity*)
     [:tx/set-item-image-in-widget cell item])])

(defn- remove-item [{:keys [entity/id] :as entity*} cell]
  (let [item (get-in (:entity/inventory entity*) cell)]
    (assert item)
    [[:e/assoc-in id (cons :entity/inventory cell) nil]
     (when (applies-modifiers? cell)
       [:tx/reverse-modifiers id (:item/modifiers item)])
     (when (:entity/player? entity*)
       [:tx/remove-item-from-widget cell])]))

(defcomponent :tx/set-item
  (component/do! [[_ entity cell item] _ctx]
    (set-item @entity cell item)))

(defcomponent :tx/remove-item
  (component/do! [[_ entity cell] _ctx]
    (remove-item @entity cell)))

; TODO doesnt exist, stackable, usable items with action/skillbar thingy
#_(defn remove-one-item [entity cell]
  (let [item (get-in (:entity/inventory @entity) cell)]
    (if (and (:count item)
             (> (:count item) 1))
      (do
       ; TODO this doesnt make sense with modifiers ! (triggered 2 times if available)
       ; first remove and then place, just update directly  item ...
       (remove-item! context entity cell)
       (set-item! context entity cell (update item :count dec)))
      (remove-item! context entity cell))))

; TODO no items which stack are available
(defn- stack-item [entity* cell item]
  (let [cell-item (get-in (:entity/inventory entity*) cell)]
    (assert (stackable? item cell-item))
    ; TODO this doesnt make sense with modifiers ! (triggered 2 times if available)
    ; first remove and then place, just update directly  item ...
    (concat (remove-item entity* cell)
            (set-item entity* cell (update cell-item :count + (:count item))))))

(defcomponent :tx/stack-item
  (component/do! [[_ entity cell item] _ctx]
    (stack-item @entity cell item)))

(defn- try-put-item-in [entity* slot item]
  (let [inventory (:entity/inventory entity*)
        cells-items (cells-and-items inventory slot)
        [cell _cell-item] (find-first (fn [[_cell cell-item]] (stackable? item cell-item))
                                      cells-items)]
    (if cell
      (stack-item entity* cell item)
      (when-let [[empty-cell] (find-first (fn [[_cell item]] (nil? item))
                                          cells-items)]
        (set-item entity* empty-cell item)))))

(defn- pickup-item [entity* item]
  (or
   (try-put-item-in entity* (:item/slot item)   item)
   (try-put-item-in entity* :inventory.slot/bag item)))

(defcomponent :tx/pickup-item
  (component/do! [[_ entity item] _ctx]
    (pickup-item @entity item)))

(extend-type core.entity.Entity
  entity/Inventory
  (can-pickup-item? [entity* item]
    (boolean (pickup-item entity* item))))

(defcomponent :entity/inventory
  {:data [:one-to-many :properties/items]}
  (entity/create [[_ items] eid _ctx]
    (cons [:e/assoc eid :entity/inventory empty-inventory]
          (for [item items]
            [:tx/pickup-item eid item]))))
