(ns is.simm.lean-cps.async-test
  (:require [cljs.test :as test :refer-macros [deftest testing is]]
            [is.simm.lean-cps.async :refer [await run]])
  (:require-macros [is.simm.lean-cps.async :refer [async doseq-async dotimes-async]]))

;; Test helpers
(defn promise-delay
  "Returns a promise that resolves after ms milliseconds"
  [ms value]
  (js/Promise. (fn [resolve _]
                 (js/setTimeout #(resolve value) ms))))

(defn async-cb-delay
  "Simulates an async operation using callbacks"
  [ms value]
  (fn [resolve reject]
    (js/setTimeout #(resolve value) ms)))

(defn failing-async
  "An async operation that always fails"
  [error-msg]
  (fn [resolve reject]
    (js/setTimeout #(reject (js/Error. error-msg)) 10)))

;; Basic async/await tests
(deftest test-simple-async-await
  (test/async done
    (let [result-atom (atom nil)]
      (run
        (async
          (let [result (await (async-cb-delay 10 "hello"))]
            (reset! result-atom result)))
        (fn [_]
          (is (= "hello" @result-atom))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

(deftest test-multiple-awaits
  (test/async done
    (let [results (atom [])]
      (run
        (async
          (let [a (await (async-cb-delay 10 1))
                b (await (async-cb-delay 10 2))
                c (await (async-cb-delay 10 3))]
            (reset! results [a b c])))
        (fn [_]
          (is (= [1 2 3] @results))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

(deftest test-await-in-conditional
  (test/async done
    (let [result (atom nil)]
      (run
        (async
          (if true
            (reset! result (await (async-cb-delay 10 "true-branch")))
            (reset! result (await (async-cb-delay 10 "false-branch")))))
        (fn [_]
          (is (= "true-branch" @result))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

(deftest test-await-in-let-binding
  (test/async done
    (let [result (atom nil)]
      (run
        (async
          (let [x (await (async-cb-delay 10 5))
                y (* x 2)
                z (await (async-cb-delay 10 y))]
            (reset! result [x y z])))
        (fn [_]
          (is (= [5 10 10] @result))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

;; Error handling tests
(deftest test-error-handling
  (test/async done
    (let [error-caught (atom nil)]
      (run
        (async
          (await (failing-async "Test error")))
        (fn [_]
          (is false "Should have failed")
          (done))
        (fn [err]
          (is (= "Test error" (.-message err)))
          (done))))))

(deftest test-try-catch-in-async
  (test/async done
    (let [result (atom nil)]
      (run
        (async
          (try
            (await (failing-async "Caught error"))
            (reset! result :should-not-reach)
            (catch :default e
              (reset! result (.-message e)))))
        (fn [_]
          (is (= "Caught error" @result))
          (done))
        (fn [err]
          (is false (str "Should not fail at top level: " err))
          (done))))))

;; Loop and recur tests
(deftest test-loop-with-await
  (test/async done
    (let [results (atom [])]
      (run
        (async
          (loop [i 0
                 acc []]
            (if (< i 3)
              (let [value (await (async-cb-delay 10 i))]
                (recur (inc i) (conj acc value)))
              (reset! results acc))))
        (fn [_]
          (is (= [0 1 2] @results))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

;; Nested async functions
(deftest test-nested-async-functions
  (test/async done
    (let [inner-async (async
                        (let [x (await (async-cb-delay 10 5))]
                          (* x 2)))
          result (atom nil)]
      (run
        (async
          (let [inner-result (await inner-async)]
            (reset! result inner-result)))
        (fn [_]
          (is (= 10 @result))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

;; Collection operations
(deftest test-doseq-async
  (test/async done
    (let [results (atom [])]
      (run
        (async
          (doseq-async [i [1 2 3]]
            (let [value (await (async-cb-delay 10 (* i 10)))]
              (swap! results conj value))))
        (fn [_]
          (is (= [10 20 30] @results))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

(deftest test-dotimes-async
  (test/async done
    (let [counter (atom 0)]
      (run
        (async
          (dotimes-async [i 3]
            (await (async-cb-delay 10 nil))
            (swap! counter inc)))
        (fn [_]
          (is (= 3 @counter))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

;; Complex control flow
(deftest test-complex-control-flow
  (test/async done
    (let [result (atom [])]
      (run
        (async
          (let [a (await (async-cb-delay 10 1))]
            (swap! result conj a)
            (when (= a 1)
              (let [b (await (async-cb-delay 10 2))]
                (swap! result conj b)
                (if (> b 1)
                  (swap! result conj (await (async-cb-delay 10 3)))
                  (swap! result conj :else))))))
        (fn [_]
          (is (= [1 2 3] @result))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

;; Promise interop test
(deftest test-promise-interop
  (test/async done
    (let [result (atom nil)]
      (run
        (async
          (let [value (await (fn [resolve reject]
                              (.then (promise-delay 10 "from-promise")
                                     resolve
                                     reject)))]
            (reset! result value)))
        (fn [_]
          (is (= "from-promise" @result))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

;; Test runner
(defn ^:export run-tests []
  (test/run-tests 'is.simm.lean-cps.async-test))

(defn ^:export init []
  (println "ClojureScript async tests initialized"))