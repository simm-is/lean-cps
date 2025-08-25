(ns is.simm.lean-cps-test
  (:refer-clojure :exclude [await])
  (:require [clojure.test :refer [deftest testing is run-tests]]
            [is.simm.lean-cps.async :refer [await run async]]))

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
  (fn [resolve reject]
    (future
      (reject (Exception. error-msg)))))

(defn blocking-test
  "Helper to run async test with blocking"
  [async-fn timeout-ms]
  (let [result (promise)
        error (promise)]
    (run async-fn
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

;; Stack overflow prevention test
(deftest test-deep-async-recursion
  (testing "Deep async recursion doesn't blow the stack"
    (let [n 10000  ; This would definitely blow the stack without trampolining
          result (blocking-test
                  (async
                    ;; Simulate the pattern from the example: reduce with async operations
                    (let [final-async (reduce (fn [acc-async num]
                                                (async 
                                                  (let [acc (await acc-async)]
                                                    (await (future-delay 0 (+ acc num))))))
                                              (async 0)  ; Start with 0
                                              (range 1 (inc n)))]  ; Add 1 through n
                      (await final-async)))
                  5000)]  ; Reduced timeout since no delays
      ;; This should equal the sum of 1 to n = n*(n+1)/2
      (is (= (* n (+ n 1) 1/2) result)))))

;; Test similar to the provided example with reduce and async operations
(deftest test-async-reduce-pattern
  (testing "Async reduce pattern similar to the benchmark example"
    (let [nums [1 2 3 4 5]
          result (blocking-test
                  (async
                    ;; Pattern: reduce with async accumulator and async operations
                    (let [final-async (reduce (fn [acc-async num]
                                                (async 
                                                  (let [acc (await acc-async)]
                                                    ;; Simulate async operation like set/conj
                                                    (let [new-val (await (future-delay 10 (conj acc num)))]
                                                      new-val))))
                                              (async [])  ; Start with empty vector
                                              nums)]
                      ;; Await the final async result
                      (await final-async)))
                  5000)]
      (is (= [1 2 3 4 5] result)))))

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

(defn run-all-tests []
  (run-tests 'is.simm.lean-cps-test))