# lean-cps

A lightweight Clojure/ClojureScript library for continuation-passing style (CPS) transformations derived from [await-cps](https://github.com/mszajna/await-cps). The CPS transform rewrites the code in a similar way to manual callback implementations by invoking the callback (continuation) only where necessary (at an interceptor). This approach leaves the remaining code synchronous and therefore retains readability compared to the transformations in [core.async](https://github.com/clojure/core.async) or [cloroutine](https://github.com/leonoel/cloroutine). The transformed code is also generally faster, both in sychronous sections, and when dispatching into callbacks. It does not provide any processing or scheduling framework, and instead provides a safe trampolining execution. This avoids scheduling overhead and only hits the a threadpool dispatcher or a JS event loop if the effect handler schedules it. 

## Features

- **Coroutines**: Transform regular Clojure code into continuation-passing style
- **Cross-platform**: Works with both Clojure and ClojureScript
- **Lightweight**: Minimal dependencies and overhead
- **Inversion of Control**: Automatic CPS transformation with interceptor support
- **Async/Await**: Write asynchronous code that looks synchronous

## Usage

### Basic Async/Await

```clojure
(require '[is.simm.lean-cps.async :refer [async await]])

;; Define an async function
(def my-async-fn
  (async
    (let [result (await some-async-operation)]
      (println "Got result:" result)
      result)))

;; Run the async function with callbacks
(my-async-fn 
  (fn [result] (println "Success:" result))
  (fn [error] (println "Error:" error)))
```

### Custom Coroutines

```clojure
(require '[is.simm.lean-cps :refer [cps run]])

;; Define custom interceptors
(def my-interceptors
  {`my-yield `handle-yield})

;; Create a coroutine
(def my-cps
  (cps my-interceptors
    (let [x (my-yield 42)]
      (+ x 10))))

;; Run the coroutine
(run my-cps
  (fn [result] (println "Success:" result))
  (fn [error] (println "Error:" error)))
```

### Async Control Flow

The library provides async versions of common control flow constructs:

```clojure
(require '[is.simm.lean-cps.async :refer [doseq-async dotimes-async]])

;; Async doseq
(doseq-async [item (await fetch-items)]
  (await (process-item item)))

;; Async dotimes  
(dotimes-async [i 10]
  (await (some-async-operation i)))
```

## MIT License

Copyright © 2025 Christian-weilbach
