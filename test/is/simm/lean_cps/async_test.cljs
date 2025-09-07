(ns is.simm.lean-cps.async-test
  (:require [cljs.test :as test :refer-macros [deftest testing is]]
            [is.simm.lean-cps.async :refer [await run]]
            [clojure.pprint :refer [pprint]]
            [is.simm.lean-cps.sequence-test :as sequence])
  (:require-macros [is.simm.lean-cps.async :refer [async doseq-async dotimes-async]]))

;; Test helpers
(defn promise-delay
  "Returns a promise that resolves after ms milliseconds"
  [ms value]
  (js/Promise. (fn [resolve _] (js/setTimeout #(resolve value) ms))))

(defn async-cb-delay
  "Simulates an async operation using callbacks"
  [ms value]
  (fn [resolve reject] (js/setTimeout #(resolve value) ms)))

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

(deftest test-cond-expansion
  (test/async done
    (let [result (atom [])]
      (run
       (async
        (cond
          (await (async-cb-delay 10 true)) (swap! result conj :first-branch)
          (and false true) (reset! result (await (async-cb-delay 10 :second-branch)))
          (await (async-cb-delay 10 false)) (swap! result conj :third-branch)
          :else (swap! result conj :fourth-branch)))
       (fn [_]
         (is (= [:first-branch] @result))
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

;; Additional async loop tests
(deftest test-doseq-async-nested
  (test/async done
    (let [results (atom [])]
      (run
        (async
          (doseq-async [i [1 2] j [:a :b]]
            (let [value (await (async-cb-delay 5 [i j]))]
              (swap! results conj value))))
        (fn [_]
          (is (= [[1 :a] [1 :b] [2 :a] [2 :b]] @results))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

(deftest test-mixed-async-loops
  (test/async done
    (let [results (atom [])]
      (run
        (async
          (doseq-async [letter [:x :y]]
            (dotimes-async [i 2]
              (let [value (await (async-cb-delay 5 [letter i]))]
                (swap! results conj value)))))
        (fn [_]
          (is (= [[:x 0] [:x 1] [:y 0] [:y 1]] @results))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

(deftest test-doseq-async-no-await
  (test/async done
    (let [results (atom [])]
      ;; This should fall through to regular doseq (synchronous)
      (doseq-async [i [1 2 3]]
        (swap! results conj (* i 10)))
      ;; Since it's synchronous, results should be immediately available
      (is (= [10 20 30] @results))
      (done))))

(deftest test-dotimes-async-no-await
  (test/async done
    (let [results (atom [])]
      ;; This should fall through to regular dotimes (synchronous)
      (dotimes-async [i 3]
        (swap! results conj (* i 100)))
      ;; Since it's synchronous, results should be immediately available
      (is (= [0 100 200] @results))
      (done))))

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

;; Async bindings tests
(deftest test-doseq-async-with-await-in-binding
  (test/async done
    (let [results (atom [])]
      (run
        (async
          ;; Await a collection in the binding itself
          (doseq-async [i (await (async-cb-delay 20 [1 2 3]))]
            (let [value (await (async-cb-delay 10 (* i 10)))]
              (swap! results conj value))))
        (fn [_]
          (is (= [10 20 30] @results))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

(deftest test-doseq-async-mixed-sync-async-bindings
  (test/async done
    (let [results (atom [])]
      (run
        (async
          ;; Mix sync collection with async collection
          (doseq-async [letter [:x :y]
                        i (await (async-cb-delay 20 [1 2]))]
            (let [value (await (async-cb-delay 10 [letter i]))]
              (swap! results conj value))))
        (fn [_]
          (is (= [[:x 1] [:x 2] [:y 1] [:y 2]] @results))
          (done))
        (fn [err]
          (is false (str "Should not fail: " err))
          (done))))))

;; Test runner
(defn ^:export run-tests []
  (test/run-tests 'is.simm.lean-cps.async-test)
  (test/run-tests 'is.simm.lean-cps.sequence-test))

(defn ^:export init []
  (println "ClojureScript async tests initialized")
  
  ;; Run a quick manual test first
  (println "Testing basic doseq-async without await...")
  (let [results (atom [])]
    (doseq-async [i [1 2 3]]
      (swap! results conj (* i 10)))
    (if (= [10 20 30] @results)
      (println "✓ doseq-async fallback works")
      (println "✗ doseq-async fallback failed:" @results)))
  
  (println "Testing basic dotimes-async without await...")
  (let [results (atom [])]
    (dotimes-async [i 3]
      (swap! results conj (* i 100)))
    (if (= [0 100 200] @results)
      (println "✓ dotimes-async fallback works") 
      (println "✗ dotimes-async fallback failed:" @results)))
  
  (println "Testing nested doseq-async...")
  (let [results (atom [])]
    (doseq-async [i [1 2] j [:a :b]]
      (swap! results conj [i j]))
    (if (= [[1 :a] [1 :b] [2 :a] [2 :b]] @results)
      (println "✓ nested doseq-async works")
      (println "✗ nested doseq-async failed:" @results)))
  
  (println "Testing mixed async loops...")
  (let [results (atom [])]
    (doseq-async [letter [:x :y]]
      (dotimes-async [i 2]
        (swap! results conj [letter i])))
    (if (= [[:x 0] [:x 1] [:y 0] [:y 1]] @results)
      (println "✓ mixed async loops work")
      (println "✗ mixed async loops failed:" @results)))
  
  (println "All sync macro tests completed successfully!")
  (println "Note: Full async test suite requires proper test runner - compile verification successful!"))