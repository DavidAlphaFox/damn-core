(ns context.game
  (:require [clj-commons.pretty.repl :as p]
            [gdx.app :as app]
            [gdx.graphics.camera :as camera]
            [gdx.graphics.orthographic-camera :as orthographic-camera]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [api.context :as ctx]
            [api.world.content-grid :as content-grid]
            (context.game [ecs :as ecs]
                          [mouseover-entity :as mouseover-entity]
                          [time :as time-component]
                          [effect-handler :as tx-handler]
                          [widgets :as widgets]
                          [world :as world]
                          [debug-render :as debug-render])))

(defn- init-game-context [ctx & {:keys [mode record-transactions? tiled-level]}]
  (-> ctx
      (dissoc ::tick-error)
      (merge {:context.game/game-loop-mode mode}
             (ecs/->build)
             (time-component/->build)
             (widgets/->state! ctx)
             (tx-handler/initialize! mode record-transactions?))
      (world/setup-context mode tiled-level)))

(defn start-new-game [ctx tiled-level]
  (init-game-context ctx
                     :mode :game-loop/normal
                     :record-transactions? false ; TODO top level flag ?
                     :tiled-level tiled-level))

(defn- start-replay-mode! [ctx]
  (input/set-processor! nil)
  (init-game-context ctx :mode :game-loop/replay))

(def ^:private pausing? true)

(defn- player-unpaused? []
  (or (input/key-just-pressed? input.keys/p)
      (input/key-pressed?      input.keys/space)))

(defn- update-game-paused [ctx]
  (assoc ctx ::paused? (or (::tick-error ctx)
                           (and pausing?
                                (ctx/player-state-pause-game? ctx)
                                (not (player-unpaused?))))))

(extend-type api.context.Context
  api.context/Game
  (game-paused? [ctx]
    (::paused? ctx)))

(defn- active-entities [ctx]
  (content-grid/active-entities (ctx/content-grid ctx)
                                (ctx/player-entity* ctx)))

(defn- update-world [ctx]
  (let [ctx (time-component/update-time ctx)
        active-entities (active-entities ctx)]
    (ctx/update-potential-fields! ctx active-entities)
    (try (ctx/tick-entities! ctx active-entities)
         (catch Throwable t
           (p/pretty-pst t 12)
           (assoc ctx ::tick-error t)))))

(defmulti game-loop :context.game/game-loop-mode)

(defmethod game-loop :game-loop/normal [ctx]
  (ctx/do! ctx [ctx/player-update-state
                mouseover-entity/update! ; this do always so can get debug info even when game not running
                update-game-paused
                #(if (ctx/game-paused? %)
                   %
                   (update-world %))
                ctx/remove-destroyed-entities! ; do not pause this as for example pickup item, should be destroyed.
                ]))

(defn- replay-frame! [ctx]
  (let [frame-number (:context.game/logic-frame ctx)
        txs (ctx/frame->txs ctx frame-number)]
    ;(println frame-number ". " (count txs))
    (-> ctx
        (ctx/do! txs)
        (update :context.game/logic-frame inc))))

(def ^:private replay-speed 2)

(defmethod game-loop :game-loop/replay [ctx]
  (reduce (fn [ctx _] (replay-frame! ctx))
          ctx
          (range replay-speed)))

(defn- adjust-zoom [camera by] ; DRY map editor
  (orthographic-camera/set-zoom! camera (max 0.1 (+ (orthographic-camera/zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys [context]
  (let [camera (ctx/world-camera context)]
    (when (input/key-pressed? input.keys/minus)  (adjust-zoom camera    zoom-speed))
    (when (input/key-pressed? input.keys/equals) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input [context]
  (check-zoom-keys context)
  (widgets/check-window-hotkeys context)
  (cond (and (input/key-just-pressed? input.keys/escape)
             (not (widgets/close-windows? context)))
        (ctx/change-screen context :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen/render!
        #_(input/key-just-pressed? input.keys/tab)
        #_(ctx/change-screen context :screens/minimap)

        :else
        context))

(defn- render-game! [ctx]
  (let [player-entity* (ctx/player-entity* ctx)]
    (camera/set-position! (ctx/world-camera ctx) (:position player-entity*))
    (ctx/render-map ctx)
    (ctx/render-world-view ctx
                           (fn [g]
                             (debug-render/before-entities ctx g)
                             (ctx/render-entities! ctx
                                                   g
                                                   (->> (active-entities ctx)
                                                        (map deref)
                                                        (filter :z-order)
                                                        (filter #(ctx/line-of-sight? ctx player-entity* %))))
                             (debug-render/after-entities ctx g)))))

(defn render [ctx]
  (render-game! ctx)
  (-> ctx
      game-loop
      check-key-input)) ; not sure I need this @ replay mode ??

(comment

 ; TODO @replay-mode
 ; adjust sound speed also equally ? pitch ?
 ; player message, player modals, etc. all game related state handle ....
 ; game timer is not reset  - continues as if
 ; check other atoms , try to remove atoms ...... !?
 ; replay mode no window hotkeys working
 ; buttons working
 ; can remove items from inventory ! changes cursor but does not change back ..
 ; => deactivate all input somehow (set input processor nil ?)
 ; works but ESC is separate from main input processor and on re-entry
 ; again stage is input-processor
 ; also cursor is from previous game replay
 ; => all hotkeys etc part of stage input processor make.
 ; set nil for non idle/item in hand states .
 ; for some reason he calls end of frame checks but cannot open windows with hotkeys

 (require 'app)
 (app/post-runnable
  (fn []
    (swap! app/state start-replay-mode!)))

 )
