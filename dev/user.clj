(ns user
  (:require
   reloaded.repl
   [environ.core :refer [env]]
   [cairborne.core :refer [main-system]]))

(reloaded.repl/set-init! main-system)

(defn start-system []
  (reloaded.repl/resume))

(defn stop-system []
  (reloaded.repl/suspend))
