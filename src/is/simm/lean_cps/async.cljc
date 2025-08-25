(ns is.simm.lean-cps.async
  (:require [is.simm.lean-cps.runtime :as runtime])
  #?(:clj (:require [is.simm.lean-cps.ioc :refer [coroutine]])))

;; TODO maybe this should be a macro that can emit more information about its code block
(defn await
  "Awaits the asynchronous execution of continuation-passing style function
   async-cb, applying it to args and two extra callback functions: resolve and
   raise. cps-fn is expected to eventually either call resolve with the result,
   call raise with the exception or just throw in the calling thread. The
   return value of cps-fn is ignored. Effectively returns the value passed to
   resolve or throws the exception passed to raise (or thrown) but does not
   block the calling tread.

   Must be called in an asynchronous function. Note that any nested functions
   defined with fn, letfn, reify or deftype are considered outside of
   asynchronous scope."
  [async-cb]
  (throw (ex-info "await called outside of asynchronous scope" {:async-cb async-cb})))

(defn await-handler
  "Provides effect handler code for await."
  [env r e]
  (let [all-ex (if (:js-globals env) :default `Throwable)]
    (fn [args]
      `(letfn [(safe-r# [v#] (try (~r v#) (catch ~all-ex t# (~e t#))))]
         (is.simm.lean-cps.runtime/->thunk (fn [] (~(first args) safe-r# ~e)))))))

(def ^:no-doc terminators
  {`await `await-handler})

#?(:clj
   (defmacro async
     "Creates an asynchronous coroutine."
     [& body]
     `(coroutine ~terminators ~@body)))

;; reexport runtime for convenience

(def run runtime/run)

;; experimental macros

#?(:clj
   (defmacro doseq-async
     "Asynchronous doseq that properly handles await operations in bindings and body.
   Requires an async framework that supports continuation-passing style."
     [bindings & body]
     (let [binding-pairs (partition 2 bindings)]

       (letfn [(has-await? [form]
                 ;; Check for await or other async operations
                 (cond
                   (and (seq? form) (= 'await (first form))) true
                   (coll? form) (some has-await? form)
                   :else false))

               (split-async-bindings [pairs]
                 ;; Split bindings into sync and first async
                 (let [[syncs remaining] (split-with #(not (has-await? (second %))) pairs)]
                   [syncs (first remaining) (rest remaining)]))

               (expand-nested-doseq [pairs]
                 ;; Expand nested doseq structure
                 (if (seq pairs)
                   (let [[sym coll] (first pairs)
                         rest-pairs (rest pairs)]
                     `(let [coll# (seq ~coll)]
                        (loop [items# coll#]
                          (when items#
                            (let [~sym (first items#)]
                              ~(if (seq rest-pairs)
                                 (expand-nested-doseq rest-pairs)
                                 `(do ~@body))
                              (recur (next items#)))))))
                   `(do ~@body)))]

         ;; Main logic
         (cond
           ;; Check if bindings contain async operations
           (some (fn [[_ coll]] (has-await? coll)) binding-pairs)
           (let [[syncs [async-sym async-coll] others] (split-async-bindings binding-pairs)]
             (if async-coll
               ;; Handle first async binding
               (let [cont (gensym "cont")]
                 `(letfn [(~cont [async-val#]
                            (doseq-async [~@(mapcat identity syncs)
                                          ~async-sym async-val#
                                          ~@(mapcat identity others)]
                                         ~@body))]
                    ;; This would need to integrate with your specific async framework
                    (async-call ~async-coll ~cont)))
               ;; Fall through to body check
               (if (some has-await? body)
                 (expand-nested-doseq binding-pairs)
                 `(doseq ~bindings ~@body))))

           ;; No async in bindings, check body
           (some has-await? body)
           (expand-nested-doseq binding-pairs)

           ;; No async operations at all
           :else
           `(doseq ~bindings ~@body))))))

#?(:clj
   (defmacro dotimes-async
     "Asynchronous dotimes that properly handles await operations in the body.
   When the body contains await calls, converts to an explicit loop/recur pattern."
     [[sym init-form] & body]
     (letfn [(has-await? [form]
               ;; Check for await or other async operations
               (cond
                 (and (seq? form) (= 'await (first form))) true
                 (coll? form) (some has-await? form)
                 :else false))]

       (if (some has-await? body)
         ;; Body contains await - convert to loop when it contains await
         `(let [max# ~init-form]
            (loop [~sym 0]
              (when (< ~sym max#)
                ~@body
                (recur (inc ~sym)))))
         ;; No await in body, pass through unchanged
         `(dotimes [~sym ~init-form] ~@body)))))
