(ns replicant.util
  (:require [clojure.main :as main]
            [clojure.core.server :as server])
  (:import [java.net ServerSocket]))

;; helpers for data-centric repls

(defn data-eval
  [form]
  (let [out-writer (java.io.StringWriter.)
        err-writer (java.io.StringWriter.)
        capture-streams (fn []
                          (.flush *out*)
                          (.flush *err*)
                          {:out (.toString out-writer)
                           :err (.toString err-writer)})]
    (binding [*out* (java.io.BufferedWriter. out-writer)
              *err* (java.io.BufferedWriter. err-writer)]
      (try
        (let [result (eval form)]
          (merge (capture-streams) {:result result}))
        (catch Throwable t
          (merge (capture-streams) {:exception (Throwable->map t)}))))))

(defn data-repl
  [& kw-opts]
  (apply main/repl
    (conj kw-opts
      :need-prompt (constantly false)
      :prompt (constantly nil)
      :eval data-eval)))

;; add kw-opts to what's currently in clojure.core.server/repl
(defn repl
  [& kw-opts]
  (apply main/repl
    (conj kw-opts
      :init server/repl-init
      :read server/repl-read)))

;; helpers to stash and use bindings from another thread

(defn user-eval
  [binding-atom form]
  (let [result (eval form)]
    (swap! binding-atom (get-thread-bindings))
    result))

(defmacro with-user-bindings
  [binding-atom & body]
  `(with-bindings ~@binding-atom body))


(comment
  ;; First start a tooling repl server - tool repl will connect to this
  ;; -Dclojure.server.datarepl="{:port 5555 :accept 'replicant.util/data-repl}"

  ;; From tool, connect as a client to 127.0.0.1:5555

  (let [bindings (atom {})                   ;; shared atom to stash user's bindings
        repl-name (gensym "repl")            ;; generate repl server name
        server ^SocketServer (server/start-server
                                {:name   repl-name
                                 :port   0   ;; pick a free port
                                 :accept 'replicant.util/repl
                                 :args   [:eval (partial user-eval bindings)]})
        repl-port (.getLocalPort server)]

    ;; From tool, connect on repl-port for user repl
    ;; The user's client is now using user-eval above and will be
    ;; saving off their bindings in the shared atom.

    ;; The tooling repl can then observe those bindings and can eval in their context
    (let [user-ns (with-user-bindings bindings *ns*)]
      ;; do whatever tool needs to do
      ))
  )