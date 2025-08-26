(ns is.simm.lean-cps.sequence
  (:refer-clojure :exclude [first rest sequence transduce into])
  (:require [is.simm.lean-cps.async :refer [async await]]))

(defprotocol IAsyncSeq
  (-afirst [this] "Returns async expression yielding first element")
  (-arest [this] "Returns async expression yielding rest of sequence"))

(defn first
  "Returns async expression yielding first element"
  [async-seq]
  (-afirst async-seq))

(defn rest
  "Returns async expression yielding rest of sequence"
  [async-seq]
  (-arest async-seq))

;; Transducers

(defn transduce
  "Transduce over an AsyncSeq eagerly, returning a Promise of the final result."
  [xform f init async-seq]
  (async
    (let [rf (xform f)]
      (loop [result init
             seq async-seq]
        (if seq
          (let [v (await (first seq))]
            (if (some? v)
              (let [result' (rf result v)]
                (if (reduced? result')
                  (rf (unreduced result'))
                  (recur result' (await (rest seq)))))
              (rf result)))
          (rf result))))))

(defn into
  ([to xform async-seq]
   (transduce xform conj to async-seq))
  ([to async-seq]
   (transduce identity conj to async-seq)))

(defprotocol ITransducerState
  "Protocol for managing shared transducer state"
  (-ensure-buffer! [this idx] "Ensure buffer has element at idx"))

(deftype TransducerState [xf source-ref buffer completed?]
  ITransducerState
  (-ensure-buffer! [_ idx]
    (async
      ;; First check if we already have the element
      (if (> (count @buffer) idx)
        true
        ;; Need more elements or completion already called
        (if @completed?
          false  ; No more elements available
          ;; Try to pull more from source
          (loop []
            (if (> (count @buffer) idx)
              ;; We have enough
              true
              ;; Need to pull from source
              (if-let [source @source-ref]
                (let [v (await (first source))]
                  (if v
                    ;; Feed value through transducer
                    (let [result (xf nil v)
                          ;; Advance source
                          next-source (await (rest source))
                          _ (vreset! source-ref next-source)]
                      (if (reduced? result)
                        ;; Reduced - complete and check buffer
                        (do
                          (vreset! completed? true)
                          ;; Call completion
                          (xf (unreduced result))
                          ;; Check if we have element after completion
                          (> (count @buffer) idx))
                        ;; Check if we have more source
                        (if next-source
                          ;; Continue pulling
                          (recur)
                          ;; No more source - need to call completion
                          (do
                            (vreset! completed? true)
                            (xf nil)
                            (> (count @buffer) idx)))))
                    ;; Source exhausted - call completion
                    (do
                      (vreset! completed? true)
                      (vreset! source-ref nil)
                      ;; Call completion - this may add more elements (e.g., partition-all)
                      (xf nil)
                      ;; Check if completion added the element we need
                      (> (count @buffer) idx))))
                ;; No source available
                (do
                  (vreset! completed? true)
                  false)))))))))

(deftype TransducedAsyncSeq [state idx]
  IAsyncSeq
  (-afirst [_]
    (async
      ;; Ensure buffer has element at idx
      (when (await (-ensure-buffer! state idx))
        (nth @(.-buffer state) idx))))

  (-arest [_]
    (async
      ;; Check if current element exists before returning rest
      (when (await (-ensure-buffer! state idx))
        ;; Return seq starting at next position
        (TransducedAsyncSeq. state (inc idx))))))

(defn sequence
  "Transform an AsyncSeq with a transducer, returning a new lazy AsyncSeq.
   The transducer is applied lazily as elements are consumed.

   Example:
   (async-sequence (map inc) async-seq)
   (async-sequence (filter even?) async-seq)
   (async-sequence (partition-all 3) async-seq)"
  [xform source-seq]
  (when source-seq
    (let [;; Shared state
          buffer (volatile! [])
          source-ref (volatile! source-seq)
          completed? (volatile! false)

          ;; Create the step function that collects into buffer
          step (fn
                 ([] nil)  ; Init (not used)
                 ([result]   ; Completion - just return result
                  result)
                 ([result input]  ; Step - collect input and return result
                  (vswap! buffer conj input)
                  result))

          ;; Apply transducer to step function
          xf (xform step)

          ;; Create the shared state object
          state (->TransducerState xf source-ref buffer completed?)]

      (TransducedAsyncSeq. state 0))))

