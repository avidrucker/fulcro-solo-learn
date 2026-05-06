(ns learn.parser
  "Hand-rolled EQL interpreter for the loopback remote.

   Receives EQL from the Fulcro client, dispatches each query/mutation
   element to the appropriate server function, accumulates a tree response.

   This is the same conceptual slot Pathom occupies in real apps — the
   parser is the bridge between Fulcro's query language and the server's
   data store. We're hand-rolling it for two reasons:
     1) The mechanics of EQL → response are clearer when written explicitly.
     2) We can swap this for a Pathom 2 parser later as a single change to
        `init`'s :remotes map. The shape `(fn [eql] response)` matches.

   Limitations of this version: no resolver composition, no parameterized
   queries, no nested joins. Pathom adds all of these for free."
  (:require
    [learn.server :as server]))

(def ^:dynamic *debug?*
  "When true, prints the EQL each request and the response. Bind via:
     (binding [parser/*debug? true] (df/load! ...))"
  false)

(defn- handle-query-element
  "Dispatch one element of an EQL vector. Adds its result (if any)
   to the running `response` map and returns the updated response."
  [response query-element]
  (cond
    ;; Query: a join like {:all-todos [:todo/id :todo/text :todo/done?]}.
    ;; The client sent an EQL join. We answer with all todos as a tree.
    (and (map? query-element)
      (contains? query-element :all-todos))
    (assoc response :all-todos (server/all-todos @server/SERVER-DB))

    ;; Mutation: a list with a fully-qualified symbol head.
    ;; The client sends `learn.client/add-todo` because that's where the
    ;; mutation was defined. We match on that and dispatch to the server.
    ;; (Real apps decouple this — see the comment in step 4 below.)
    (and (list? query-element)
      (= 'learn.client/add-todo (first query-element)))
    (let [params              (second query-element)
          [new-state new-todo] (server/add-todo @server/SERVER-DB params)]
      (reset! server/SERVER-DB new-state)
      (assoc response 'learn.client/add-todo new-todo))

    (and (list? query-element)
      (= 'learn.client/delete-todo (first query-element)))
    (let [params (second query-element)]
      (swap! server/SERVER-DB server/delete-todo params)
      (assoc response 'learn.client/delete-todo {}))

    ;; Anything we don't recognize: ignore it and let it pass through.
    ;; A real parser would error or warn here; we keep it lax for learning.
    :else response))

(defn handler
  "Top-level EQL handler. This is the function `sync-remote` calls.

   eql: a vector of query/mutation elements
   returns: a map keyed by query keys / mutation symbols, holding the
            tree response Fulcro will normalize back into the client DB."
  [eql]
  (when *debug?*
    (println "PARSER got EQL:" (pr-str eql)))
  (let [response (reduce handle-query-element {} eql)]
    (when *debug?*
      (println "PARSER returning:" (pr-str response)))
    response))
