# lean-cps

A lightweight Clojure/ClojureScript library for continuation-passing style (CPS) transformations derived from [await-cps](https://github.com/mszajna/await-cps). The CPS transform rewrites code similar to manual callback implementations by invoking the callback (continuation) only where necessary (at an interceptor). This approach leaves the remaining code synchronous and therefore retains readability compared to the transformations in [core.async](https://github.com/clojure/core.async) or [cloroutine](https://github.com/leonoel/cloroutine). 

The transformed code is also generally faster, both in synchronous sections and when dispatching into callbacks. It does not provide any processing or scheduling framework, instead using safe trampolining execution. This avoids scheduling overhead and only hits a threadpool dispatcher or JS event loop if the effect handler explicitly schedules it.

## Features

- **Async/Await**: Write asynchronous code that looks synchronous
- **Async Sequences**: Lazy async sequences with transducer support
- **Custom Coroutines**: Build your own control flow primitives with interceptors
- **Cross-platform**: Works seamlessly with both Clojure and ClojureScript
- **Lightweight**: Minimal dependencies and overhead
- **Fast**: No scheduling overhead, synchronous code stays synchronous
- **Safe**: Automatic trampolining prevents stack overflow

## Installation

### deps.edn
```clojure
{is.simm/lean-cps {:git/url "https://github.com/simm-is/lean-cps" :git/sha "LATEST"}} ; Check github for latest commit
```

## Usage

### Basic Async/Await

```clojure
(require '[is.simm.lean-cps.async :refer [async await run]])

;; Define an async function
(def fetch-user
  (async
    (let [user-data (await (http-get "/api/user"))
          profile (await (http-get (str "/api/profile/" (:id user-data))))]
      (merge user-data profile))))

;; Run the async function with callbacks
(run fetch-user
  (fn [result] (println "Success:" result))
  (fn [error] (println "Error:" error)))
```

### Error Handling

Errors propagate naturally through the async chain:

```clojure
(def safe-operation
  (async
    (try
      (let [result (await risky-operation)]
        (process result))
      (catch Exception e
        (println "Caught error:" (ex-message e))
        :fallback-value))))

;; Errors can also be handled in the error callback
(run safe-operation
  (fn [result] (println "Got result:" result))
  (fn [error] 
    (log-error error)
    (handle-recovery error)))
```

### Async Control Flow

The library provides async versions of common control flow constructs:

```clojure
(require '[is.simm.lean-cps.async :refer [doseq-async dotimes-async]])

;; Process items sequentially with async operations
(async
  (doseq-async [item (await fetch-items)]
    (println "Processing:" item)
    (await (process-item item))))

;; Async iterations
(async
  (dotimes-async [i 5]
    (println "Iteration" i)
    (await (delay-ms 1000))))
```

### Async Sequences

Work with lazy, asynchronous data streams using transducers:

```clojure
(require '[is.simm.lean-cps.sequence :as seq])

;; Define an async sequence
(defrecord AsyncRange [start end]
  seq/IAsyncSeq
  (-afirst [_] 
    (async 
      (when (< start end)
        start)))
  (-arest [_]
    (async
      (when (< start end)
        (->AsyncRange (inc start) end)))))

;; Use transducers with async sequences
(async
  (let [async-seq (->AsyncRange 0 100)]
    ;; Eagerly process with transduce
    (let [sum (await (seq/transduce (map inc) + 0 async-seq))]
      (println "Sum:" sum))
    
    ;; Lazily transform with sequence
    (let [transformed (seq/sequence 
                        (comp (filter even?) 
                              (map #(* % 2))
                              (take 5)) 
                        async-seq)
          result (await (seq/into [] transformed))]
      (println "Transformed:" result))))
```

### Integration with Promises (ClojureScript)

```clojure
;; Convert JS Promise to CPS
(defn promise->cps [promise]
  (fn [resolve reject]
    (.then promise resolve reject)))

(async
  (let [response (await (promise->cps (js/fetch "/api/data")))
        data (await (promise->cps (.json response)))]
    (process-data data)))
```

### Node.js Callback Integration

```clojure
;; Convert Node.js-style callbacks
(defn node-callback->cps [f & args]
  (fn [resolve reject]
    (apply f (concat args 
                    [(fn [err result]
                       (if err
                         (reject err)
                         (resolve result)))]))))

(async
  (let [file-content (await (node-callback->cps fs/readFile "file.txt" "utf8"))]
    (println "File contents:" file-content)))
```

## Advanced Usage

### Creating Custom Interceptors

Build your own control flow primitives:

```clojure
(require '[is.simm.lean-cps :refer [cps run]])

;; Define a custom yield operation
(defn yield-handler [env r e]
  (fn [args]
    `(schedule-microtask 
       (fn [] (~r ~(first args))))))

;; Create a coroutine with custom interceptor
(def my-coroutine
  (cps {`my-yield `yield-handler}
    (let [i 3]
      (println "Before yield" i)
      (my-yield i)
      (println "After yield" i))))

(run my-coroutine
  (fn [result] (println "Done"))
  (fn [error] (println "Error:" error)))
```

### Performance Characteristics

The library is designed for high performance:

- **Minimal transformation**: Only rewrites code at interceptor points
- **No scheduling overhead**: Direct callback execution unless you add scheduling
- **Safe trampolining**: Prevents stack overflow while maintaining speed
- **Synchronous fast path**: Non-async code runs at full speed

## Testing

The library integrates well with standard test frameworks:

```clojure
(require '[clojure.test :refer [deftest testing is] :as test]
         '[is.simm.lean-cps.async :refer [async await run]])

;; Clojure test
(deftest my-async-test
  (testing "async operations"
    (let [result (atom nil)]
      (run (async
             (reset! result (await some-async-op)))
           (fn [_] (is (= @result expected)))
           (fn [err] (is false "Should not fail"))))))

;; ClojureScript test
(deftest my-async-test
  (testing "async operations"
    (test/async done  ; test.async pattern
      (run (async
             (let [result (await some-async-op)]
               (is (= result expected))))
           (fn [_] (done))
           (fn [err] 
             (is false "Should not fail")
             (done))))))
```

## API Reference

### Core (`is.simm.lean-cps`)
- `(cps interceptors & body)` - Create a coroutine with custom interceptors
- `(run coroutine success-fn error-fn)` - Execute a coroutine

### Async (`is.simm.lean-cps.async`)
- `(async & body)` - Create an async function
- `(await async-op)` - Await an async operation (must be inside async)
- `(run async-fn success-fn error-fn)` - Execute an async function
- `(doseq-async bindings & body)` - Async version of doseq
- `(dotimes-async bindings & body)` - Async version of dotimes

### Sequences (`is.simm.lean-cps.sequence`)
- `IAsyncSeq` - Protocol for async sequences
  - `(-afirst this)` - Return async expression yielding first element
  - `(-arest this)` - Return async expression yielding rest
- `(first async-seq)` - Get first element
- `(rest async-seq)` - Get rest of sequence
- `(transduce xform f init async-seq)` - Eagerly transduce
- `(into to xform? async-seq)` - Pour into collection
- `(sequence xform async-seq)` - Lazy transformation

## Development

### Running Tests

```bash
# Clojure tests
clojure -X:test

# ClojureScript tests
npx shadow-cljs compile test
node target/test.js
```

### Building

```bash
# Build JAR
clojure -T:build ci

# Install locally
clojure -T:build install

# Deploy to Clojars
clojure -T:build deploy
```

### REPL Development

```bash
# Start REPL with CIDER support
clojure -M:repl

# Start REPL with MCP server
clojure -M:repl-mcp
```

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

Areas of interest:
- Additional async control flow macros
- Performance optimizations
- Documentation and examples
- Integration with more async libraries

## License

MIT License

Copyright © 2025 Christian Weilbach, 2019 Maciej Szajna