(ns is.simm.partial-cps.runtime
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
