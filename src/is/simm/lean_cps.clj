(ns is.simm.lean-cps
  "This is the public API to create custom coroutines and run them."
  (:require [is.simm.lean-cps.runtime :as runtime]
            [is.simm.lean-cps.ioc :as ioc]))

;; TODO decide on public API

(def coroutine ioc/coroutine)

(def run runtime/run)