(ns is.simm.lean-cps.runtime
  (:refer-clojure :exclude [bound-fn]))

(defn ^:no-doc bound-fn
  [f]
  #?(:clj
     (let [bound-frame (clojure.lang.Var/getThreadBindingFrame)]
       (fn [& args]
         (let [call-site-frame (clojure.lang.Var/getThreadBindingFrame)]
           (clojure.lang.Var/resetThreadBindingFrame bound-frame)
           (try
             (apply f args)
             (finally
               (clojure.lang.Var/resetThreadBindingFrame call-site-frame))))))
     ;; no dynamic binding support for async code in cljs (same for core.async)
     :cljs identity))

(deftype Thunk [f])

(defn ->thunk
  "Create a thunk for trampolining"
  [f]
  (Thunk. f))

(defn smart-trampoline
  "Trampoline that only bounces on Thunk instances, allowing functions to be returned as values"
  [f & args]
  (loop [result (apply f args)]
    (if (instance? Thunk result)
      (recur ((.-f ^Thunk result)))  ; Continue trampolining thunks
      result)))         ; Stop - return any other value (including functions)

(defn run
  "Run a CPS transformed stack."
  [f resolve raise]
  (try
    (smart-trampoline f resolve raise)
    (catch #?(:clj Throwable :cljs :default) e
      (raise e))))
