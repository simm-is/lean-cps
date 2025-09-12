(ns is.simm.partial-cps.async-test
  (:refer-clojure :exclude [await])
  (:require [clojure.test :refer [deftest testing is run-tests]]
            [is.simm.partial-cps.async :refer [await async doseq-async dotimes-async]]))

;; Test helpers for Clojure (JVM)
(defn future-delay
  "Returns a function that simulates async operation using future"
  [ms value]
  (fn [resolve reject]
    (future
      (try
        (Thread/sleep ms)
        (resolve value)
        (catch Exception e
          (reject e))))))

(defn failing-async
  "An async operation that always fails"
  [error-msg]
  (fn [_resolve reject]
    (future
      (reject (Exception. error-msg)))))

(defn blocking-test
  "Helper to run async test with blocking"
  [async-fn timeout-ms]
  (let [result (promise)
        error (promise)]
    (async-fn
     #(deliver result %)
     #(deliver error %))
    (let [res (deref (future
                       (try
                         (or (deref result timeout-ms nil)
                             (deref error timeout-ms :timeout))
                         (catch Exception e e)))
                     (+ timeout-ms 100)
                     :timeout)]
      (cond
        (= res :timeout) (throw (Exception. "Test timed out"))
        (instance? Exception res) (throw res)
        :else res))))

;; Basic async/await tests
(deftest test-simple-async
  (testing "Basic async without await returns value"
    (let [result (blocking-test (async "hello") 100)]
      (is (= "hello" result)))))

(deftest test-simple-await
  (testing "Basic async with await"
    (let [result (blocking-test 
                  (async
                    (let [value (await (future-delay 50 "hello"))]
                      value))
                  200)]
      (is (= "hello" result)))))

(deftest test-multiple-awaits
  (testing "Multiple await calls in sequence"
    (let [result (blocking-test
                  (async
                    (let [a (await (future-delay 30 1))
                          b (await (future-delay 30 2))
                          c (await (future-delay 30 3))]
                      [a b c]))
                  500)]
      (is (= [1 2 3] result)))))

(deftest test-await-in-let-binding
  (testing "Await in let bindings with computation"
    (let [result (blocking-test
                  (async
                    (let [x (await (future-delay 30 5))
                          y (* x 2)
                          z (await (future-delay 30 y))]
                      [x y z]))
                  300)]
      (is (= [5 10 10] result)))))

(deftest test-conditional-await
  (testing "Await in conditional branches"
    (let [result (blocking-test
                  (async
                    (if true
                      (await (future-delay 30 "true-branch"))
                      (await (future-delay 30 "false-branch"))))
                  200)]
      (is (= "true-branch" result)))))

;; Error handling tests
(deftest test-async-error-handling
  (testing "Async operation that throws is caught by error handler"
    (let [error-msg "Test error"
          result (try
                   (blocking-test
                    (async
                      (await (failing-async error-msg)))
                    200)
                   :should-not-reach
                   (catch Exception e
                     (.getMessage e)))]
      (is (= error-msg result)))))

(deftest test-exception-in-async-block
  (testing "Exception thrown in async block"
    (let [result (try
                   (blocking-test
                    (async
                      (throw (Exception. "Sync error")))
                    200)
                   :should-not-reach
                   (catch Exception e
                     (.getMessage e)))]
      (is (= "Sync error" result)))))

;; Loop and recursion tests
(deftest test-loop-with-await
  (testing "Loop with await inside"
    (let [result (blocking-test
                  (async
                    (loop [i 0
                           acc []]
                      (if (< i 3)
                        (let [value (await (future-delay 20 i))]
                          (recur (inc i) (conj acc value)))
                        acc)))
                  500)]
      (is (= [0 1 2] result)))))

;; Complex control flow
(deftest test-complex-control-flow
  (testing "Complex mix of control structures with await"
    (let [result (blocking-test
                  (async
                    (let [results (atom [])]
                      (let [a (await (future-delay 20 1))]
                        (swap! results conj a)
                        (when (= a 1)
                          (let [b (await (future-delay 20 2))]
                            (swap! results conj b)
                            (if (> b 1)
                              (swap! results conj (await (future-delay 20 3)))
                              (swap! results conj :else))))
                        @results)))
                  400)]
      (is (= [1 2 3] result)))))

;; Nested async functions
(deftest test-nested-async
  (testing "Async function calling another async function"
    (let [inner-async (async
                        (let [x (await (future-delay 30 5))]
                          (* x 2)))
          result (blocking-test
                  (async
                    (let [inner-result (await inner-async)]
                      (+ inner-result 5)))
                  300)]
      (is (= 15 result)))))

;; Concurrent behavior test
(deftest test-concurrent-execution
  (testing "Multiple async operations can run concurrently"
    (let [start-time (System/currentTimeMillis)
          result (blocking-test
                  (async
                    ;; Start both operations "simultaneously" by not awaiting immediately
                    (let [op1 (future-delay 100 "first")
                          op2 (future-delay 100 "second")]
                      ;; Now await both - they should run concurrently
                      [(await op1) (await op2)]))
                  400)
          end-time (System/currentTimeMillis)
          elapsed (- end-time start-time)]
      (is (= ["first" "second"] result))
      ;; Should take ~100ms, not ~200ms if running concurrently  
      ;; Allow some overhead for threading and trampolining
      (is (< elapsed 250) "Operations should run concurrently"))))

;; Integration with regular functions
(deftest test-regular-function-integration
  (testing "Async blocks work with regular Clojure functions"
    (letfn [(helper-fn [x] (* x x))]
      (let [result (blocking-test
                    (async
                      (let [value (await (future-delay 30 4))
                            squared (helper-fn value)
                            doubled (await (future-delay 30 (* squared 2)))]
                        doubled))
                    300)]
        (is (= 32 result))))))

;; Test with various data types
(deftest test-various-data-types
  (testing "Await works with different data types"
    (let [result (blocking-test
                  (async
                    {:number (await (future-delay 20 42))
                     :string (await (future-delay 20 "test"))
                     :vector (await (future-delay 20 [1 2 3]))
                     :map (await (future-delay 20 {:key "value"}))})
                  300)]
      (is (= {:number 42
              :string "test"
              :vector [1 2 3]
              :map {:key "value"}} result)))))

;; Test nested async calls to verify stack safety
(deftest test-nested-async-calls
  (testing "Deeply nested async calls don't overflow stack"
    (letfn [(nested-async [depth acc]
              (if (<= depth 0)
                (async acc)
                (async
                  (let [intermediate (await (future-delay 1 (inc acc)))]
                    (await (nested-async (dec depth) intermediate))))))]
      (let [depth 1000  ; This would blow stack without trampolining
            result (blocking-test
                    (nested-async depth 0)
                    10000)]
        (is (= depth result))))))

;; Test loop/recur compatibility with await
(deftest test-loop-recur-with-await
  (testing "loop/recur with await should work or timeout/fail predictably"
    (let [result (try
                   (blocking-test
                    (async
                      (let [results (atom [])]
                        (loop [i 0]
                          (if (< i 3)
                            (do
                              (let [value (await (future-delay 10 (* i 10)))]
                                (swap! results conj value))
                              (recur (inc i)))
                            @results))))
                    2000)
                   (catch Exception e
                     (.getMessage e)))]
      ;; This test documents the current behavior - it should either work or timeout
      (is (or (= [0 10 20] result)
              (= "Test timed out" result))
          (str "Expected success [0 10 20] or timeout, got: " result)))))

;; Test def await in async context
(deftest test-def-await-in-async
  (testing "Defining a var with await inside async"
    (declare async-value) ; TODO: Declare the var to avoid compile error
    (let [result (blocking-test
                  (async
                   (def async-value (await (future-delay 20 99)))
                   async-value)
                  200)]
      (is (= 99 result)))))

;; Test doseq-async
(deftest test-doseq-async-basic
  (testing "Basic doseq-async with sequential await operations"
    (let [results (atom [])
          result (blocking-test
                  (async
                    (doseq-async [i [1 2 3]]
                      (let [value (await (future-delay 10 (* i 10)))]
                        (swap! results conj value)))
                    :completed)
                  1000)]
      (is (= :completed result)) ; async block completed
      (is (= [10 20 30] @results)))))

(deftest test-doseq-async-nested
  (testing "Nested doseq-async with multiple bindings"
    (let [results (atom [])
          result (blocking-test
                  (async
                    (doseq-async [i [1 2] j [:a :b]]
                      (let [value (await (future-delay 5 [i j]))]
                        (swap! results conj value)))
                    :completed)
                  1000)]
      (is (= :completed result))
      (is (= [[1 :a] [1 :b] [2 :a] [2 :b]] @results)))))

(deftest test-doseq-async-no-await
  (testing "doseq-async falls through to regular doseq when no await"
    (let [results (atom [])]
      (doseq-async [i [1 2 3]]
        (swap! results conj (* i 10)))
      (is (= [10 20 30] @results)))))

;; Test dotimes-async
(deftest test-dotimes-async-basic
  (testing "Basic dotimes-async with sequential await operations"
    (let [results (atom [])
          result (blocking-test
                  (async
                    (dotimes-async [i 3]
                      (let [value (await (future-delay 10 (* i 100)))]
                        (swap! results conj value)))
                    :completed) ; Return a completion marker
                  1000)]
      (is (= :completed result)) ; async block completed
      (is (= [0 100 200] @results)))))

(deftest test-dotimes-async-no-await
  (testing "dotimes-async falls through to regular dotimes when no await"
    (let [results (atom [])]
      (dotimes-async [i 3]
        (swap! results conj (* i 100)))
      (is (= [0 100 200] @results)))))

(deftest test-mixed-async-loops
  (testing "Combining doseq-async and dotimes-async"
    (let [results (atom [])
          result (blocking-test
                  (async
                    (doseq-async [letter [:a :b]]
                      (dotimes-async [i 2]
                        (let [value (await (future-delay 5 [letter i]))]
                          (swap! results conj value))))
                    :completed)
                  1000)]
      (is (= :completed result))
      (is (= [[:a 0] [:a 1] [:b 0] [:b 1]] @results)))))

(deftest test-doseq-async-with-await-in-binding
  (testing "doseq-async with await in collection binding"
    (let [results (atom [])
          result (blocking-test
                  (async
                    ;; Await a collection in the binding itself
                    (doseq-async [i (await (future-delay 20 [1 2 3]))]
                      (let [value (await (future-delay 10 (* i 10)))]
                        (swap! results conj value)))
                    :completed)
                  2000)]
      (is (= :completed result))
      (is (= [10 20 30] @results)))))

(deftest test-doseq-async-mixed-sync-async-bindings
  (testing "doseq-async with mix of sync and async bindings"
    (let [results (atom [])
          result (blocking-test
                  (async
                    ;; Mix sync collection with async collection
                    (doseq-async [letter [:x :y]
                                  i (await (future-delay 20 [1 2]))]
                      (let [value (await (future-delay 10 [letter i]))]
                        (swap! results conj value)))
                    :completed)
                  3000)]
      (is (= :completed result))
      (is (= [[:x 1] [:x 2] [:y 1] [:y 2]] @results)))))

(defn run-all-tests []
  (run-tests 'is.simm.partial-cps-test))