# Refactor: `in-any-order` — from Permutations to Bipartite Matching

> **Relevant file:** `src/cljc/matcher_combinators/core.cljc`

---

## What is `in-any-order`?

`in-any-order` is a matcher that verifies whether two sequences contain the **same elements regardless of order**.

Each position in the `expected` vector can be a **literal value** (compared by equality) or any **matcher** — including Clojure functions like `odd?`, `string?`, or composite matchers like `m/equals` and `m/embeds`. Any Clojure function is automatically treated as a predicate matcher (via `parser.cljc`).

```clojure
;; Literal values — equality comparison, order doesn't matter
[3 1 2] => (match (m/in-any-order [1 2 3]))

;; Clojure functions are automatic matchers — they match any element they accept
[3 "hello" 4] => (match (m/in-any-order [string? odd? even?]))
;;               ^ the algorithm finds: string?→"hello", odd?→3, even?→4

;; Mix of literals and matchers in the same list
[3 "hello" 42] => (match (m/in-any-order [42 odd? string?]))
;;                  42 matches 42, odd? matches 3, string? matches "hello"

;; Composite matchers also work
[{:id 2 :name "Bob"} {:id 1 :name "Alice"}]
=> (match (m/in-any-order [{:id 1 :name string?}
                           {:id pos-int? :name "Bob"}]))

;; Failure: no valid pairing — 4 doesn't satisfy string?, odd?, or 99
[3 "hello" 4] =not=> (match (m/in-any-order [string? odd? 99]))
```

Beyond deciding whether there is a match or not, the matcher must generate a **useful error report** on failure — showing which element(s) didn't match and with the fewest possible differences.

---

## The Core Problem

To check whether two sequences match in any order, we need to answer:

> "Is there any way to pair each matcher with an element of the actual sequence such that every pair is valid?"

This is the **matching problem**: given a set of matchers and a set of elements, find a 1-to-1 assignment where every matcher passes.

---

## Before: Exhaustive Permutation Search

### Core idea

The original implementation solved the problem in a simple, direct way: **try every possible ordering of the matchers and see which one works**.

In code:

```clojure
;; BEFORE — match-all-permutations in master
(defn- match-all-permutations [expected elements subset?]
  (let [[matchers elements] (normalize-inputs-length expected elements)
        matcher-perms       (combo/permutations matchers)   ; generates ALL permutations
        find-best-match     (matched-or-best-matchers elements subset?)
        result              (reduce find-best-match
                                    {:matched   []
                                     :weight    Integer/MAX_VALUE
                                     :elements  elements
                                     :unmatched matchers}
                                    matcher-perms)]
    ...))
```

For each permutation of matchers, `matches-in-any-order?` attempted a greedy left-to-right match against the elements:

```clojure
;; BEFORE — greedy attempt for a specific permutation
(defn- matches-in-any-order? [unmatched elements subset? matching]
  (if (empty? unmatched)
    ;; ran out of matchers — check if it matched
    {:matched? true, ...}
    (let [[matcher & rest] unmatched
          ;; grab the FIRST element that works
          matching-elem (find-first #(indicates-match? (match matcher %)) elements)]
      (if (nil? matching-elem)
        {:matched? false, ...}
        ;; found a pair, continue with the rest
        (recur rest (remove matching-elem elements) subset? ...)))))
```

The best permutation (the one with the most matches and lowest total error weight) was chosen via `better-mismatch?`:

```clojure
(defn- better-mismatch? [best candidate]
  (and (>= (-> candidate :matched count) (-> best :matched count))
       (<= (:weight candidate) (:weight best))))
```

### External dependency

The algorithm relied on `clojure.math.combinatorics` to generate permutations:

```clojure
;; deps.edn — BEFORE
:deps {org.clojure/clojure              {:mvn/version "1.8.0"}
       org.clojure/math.combinatorics   {:mvn/version "0.2.0"}}  ; <-- production dependency
```

### Why this works... but doesn't scale

For a list of N elements, there are **N! (N factorial)** possible permutations:

| N (elements) | N! permutations | Estimated time |
|:---:|---:|:---:|
| 5  | 120 | instant |
| 7  | 5,040 | instant |
| 10 | 3,628,800 | slow (~seconds) |
| 13 | 6,227,020,800 | very slow (~hours) |
| 15 | 1,307,674,368,000 | **hangs** |

For lists with more than ~10 elements, the test simply hangs. This is an **O(N! × N) scalability problem**.

### Functions involved (BEFORE)

| Function | What it did |
|---|---|
| `matched-successfully?` | Checked whether the matching was complete |
| `residual-matching-weight` | Calculated the total weight of remaining mismatches |
| `matches-in-any-order?` | Attempted a greedy match for a specific permutation |
| `better-mismatch?` | Compared which permutation produced the best error report |
| `matched-or-best-matchers` | Reduction function that iterated over all permutations |

---

## After: Bipartite Matching (Kuhn's Algorithm) + Restricted Permutations

### Core idea

The new implementation **separates the two problems**:

1. **Deciding whether there is a match** → Kuhn's algorithm (O(N² × M)) — much faster than N!
2. **Generating the error report** → Permutations, but only over a small subset

### What is Bipartite Matching?

Imagine two groups: matchers on one side, elements on the other. Draw an edge between matcher `m` and element `e` if `m` accepts `e`. The problem is: **is there a way to connect each matcher to exactly one element (and vice-versa) using only those edges?**

```
Matchers          Elements
    m0 ─── ✓ ──── e0   (m0 accepts e0)
    m0 ─── ✓ ──── e1   (m0 also accepts e1)
    m1 ─── ✓ ──── e1   (m1 accepts e1)
    m2 ─── ✓ ──── e2   (m2 only accepts e2)
```

A **matching** is any set of (matcher, element) pairs where each matcher and each element appear at most once — no repetitions on either side. The **maximum matching** is the matching with the greatest number of pairs possible given the compatibility graph.

In the example above, there are several valid ways to pair without repetition:
- `{m0→e0, m1→e1, m2→e2}` — 3 pairs ← **maximum matching** (every matcher paired)
- `{m1→e1, m2→e2}` — 2 pairs (m0 left unpaired)
- `{m0→e0, m2→e2}` — 2 pairs (m1 left unpaired)
- `{m0→e0, m1→e1}` — 2 pairs (m2 left unpaired)

Kuhn's algorithm always finds the maximum matching. When that maximum has as many pairs as there are matchers, it means **every matcher was paired** — there is a complete match.

### Step 1 — Pre-compute the result matrix

Before running the algorithm, all (matcher × element) pairs are computed and stored:

```clojure
;; AFTER
(defn- build-match-matrix [matchers elements]
  (mapv (fn [m] (mapv #(match m %) elements)) matchers))
```

Result: a matrix `matrix[i][j]` that holds the result of `(match matcher_i element_j)`. This avoids recomputing the same pair multiple times.

### Step 2 — Kuhn's algorithm (augmenting paths)

Kuhn's algorithm finds the largest possible matching using the concept of an **augmenting path**: if an element is already assigned to a matcher, it tries to reassign that matcher to another free element, freeing up the original.

```clojure
;; AFTER
(defn- try-augment [matcher-idx matrix match-to visited]
  (let [num-elems (count (get matrix 0 []))]
    (loop [elem-idx 0
           match-to match-to
           visited  visited]
      (cond
        ;; Exhausted all elements — no path found
        (>= elem-idx num-elems)
        [false match-to visited]

        ;; elem-idx was already visited in this attempt, or matcher-idx doesn't accept elem-idx
        (or (contains? visited elem-idx)
            (not (indicates-match? (get-in matrix [matcher-idx elem-idx]))))
        (recur (inc elem-idx) match-to visited)

        ;; elem-idx is a candidate — try to assign
        :else
        (let [visited+elem                     (conj visited elem-idx)
              current-owner                    (get match-to elem-idx -1) ; -1 = free
              [path-found? match-to' visited'] (if (neg? current-owner)
                                                 [true match-to visited+elem]           ; elem-idx is free!
                                                 (try-augment current-owner matrix match-to visited+elem))] ; try to push current-owner elsewhere
          (if path-found?
            [true (assoc match-to' elem-idx matcher-idx) visited'] ; success: assign matcher-idx to elem-idx
            (recur (inc elem-idx) match-to visited')))))))

(defn- max-bipartite-matching [matrix]
  (reduce (fn [match-to matcher-idx]
            (let [[_ match-to'] (try-augment matcher-idx matrix match-to #{})]
              match-to'))
          {}
          (range (count matrix))))
```

**Step-by-step example** with `[42 "world" 7] => (m/in-any-order [int? string? 42])`:

The matchers are `[int?, string?, 42]` and the elements are `[42, "world", 7]`.
Note that `int?` and `string?` are Clojure functions — automatically treated as predicate matchers.

```
Matchers             Elements
  m0=int?            e0=42
  m1=string?         e1="world"
  m2=42              e2=7

Compatibility matrix (✓ = accepts, ✗ = rejects):

                   e0=42   e1="world"   e2=7
m0=int?:             ✓          ✗         ✓      ← int? accepts 42 and 7
m1=string?:          ✗          ✓         ✗      ← string? only accepts "world"
m2=42:               ✓          ✗         ✗      ← literal 42 only accepts equal value

Running Kuhn (matcher-idx = matcher index):

 matcher-idx=0 (int?):
   elem-idx=0 (42): int? accepts 42, e0 is free → assign m0→e0
   match-to: {e0→m0}

 matcher-idx=1 (string?):
   elem-idx=0 (42): string? rejects 42, skip
   elem-idx=1 ("world"): string? accepts, e1 is free → assign m1→e1
   match-to: {e0→m0, e1→m1}

 matcher-idx=2 (42):
   elem-idx=0 (42): literal 42 accepts, but e0 is taken by m0 (current-owner=m0)
     → try to move m0 elsewhere (augmenting path):
       elem-idx=0 already in visited, skip
       elem-idx=1 ("world"): int? rejects, skip
       elem-idx=2 (7): int? accepts 7, e2 is free → move m0 to e2 ✓
   → e0 is now free for m2
   match-to: {e0→m2, e1→m1, e2→m0}

count(match-to) = 3 = count(matchers) → MATCH ✓

Final pairing: int?→7,  string?→"world",  42→42
```

**Step-by-step example — mismatch** with `[1 2 2] => (m/in-any-order [1 int? odd?])`:

The matchers are `[1, int?, odd?]` and the elements are `[1, 2, 2]`.
`odd?` can't match either `2` because both are even — so a complete match is impossible.

```
Matchers             Elements
  m0=1               e0=1
  m1=int?            e1=2
  m2=odd?            e2=2

Compatibility matrix (✓ = accepts, ✗ = rejects):

              e0=1   e1=2   e2=2
m0=1:          ✓      ✗      ✗      ← literal 1 only accepts 1
m1=int?:       ✓      ✓      ✓      ← int? accepts all three
m2=odd?:       ✓      ✗      ✗      ← odd? only accepts 1; both 2s are even

Running Kuhn:

 i=0 (m0=1):
   j=0 (1): matches, e0 is free → assign m0→e0
   match-to: {e0→m0}

 i=1 (m1=int?):
   j=0 (1): matches, but e0 is taken by m0
     → try to move m0 elsewhere (augmenting path):
       j=0 already visited, skip
       j=1 (2): match(1, 2) = ✗, skip
       j=2 (2): match(1, 2) = ✗, skip → no path found for m0
   j=1 (2): int? matches, e1 is free → assign m1→e1
   match-to: {e0→m0, e1→m1}

 i=2 (m2=odd?):
   j=0 (1): matches, but e0 is taken by m0
     → try to move m0 elsewhere: j=1 ✗, j=2 ✗ → no path
   j=1 (2): odd? rejects 2, skip
   j=2 (2): odd? rejects 2, skip → no path found for m2

count(match-to) = 2 ≠ 3 = count(matchers) → MISMATCH

Mismatch path:
  matched:   m0→e0, m1→e1
  unmatched: m2=odd? (no element accepted it)
             e2=2   (no matcher was assigned to it)

  min-cost-assign pairs m2 with e2 (only option):
    match(odd?, 2) = Mismatch(odd?, 2), weight=1

Final diff: [1, 2, Mismatch(odd?, 2)]
            ↑   ↑   ↑
           m0  m1  m2 — only odd? failed
```

---

### Step 3 — Mismatch path: permutations only where needed

When bipartite matching fails to find a complete matching, we need to generate the best error report. Two concepts come into play here:

#### The `unexpected-matcher`

Before running bipartite matching, the two sequences must be the same length. `normalize-inputs-length` handles this:

```clojure
(defn- normalize-inputs-length [matchers actuals]
  (let [matchers-count (count matchers)
        actuals-count  (count actuals)]
    (if (< actuals-count matchers-count)
      ;; actual is shorter: pad actual with ::missing
      [matchers
       (take matchers-count (concat actuals (repeat ::missing)))]
      ;; actual is longer: pad matchers with unexpected-matcher
      [(take actuals-count (concat matchers (repeat unexpected-matcher)))
       actuals])))
```

When `actual` has **more** elements than `expected`, the function pads the difference with instances of a special sentinel:

```clojure
(def ^:private unexpected-matcher
  (reify Matcher
    (-match [_this actual]
      {::result/type   :mismatch
       ::result/value  (model/->Unexpected actual)
       ::result/weight 1})   ; always weight=1, regardless of element
    ...))
```

**Key property:** `unexpected-matcher` always returns `weight=1` for any element. This means the order in which we pair `unexpected-matcher`s with leftover elements **doesn't matter** — any order is equally optimal.

**Why does bipartite matching still run even when sizes differ?**

When `actual` has more elements than `expected`, a complete match is guaranteed to be impossible — `unexpected-matcher` never produces a valid match, so `count(match-to)` will never reach `n`. Even so, the algorithm runs because it needs to discover **which** original matchers did match, in order to generate the most informative error report.

Example: `(m/in-any-order [1 2])` against `[1 2 3]`. After normalization, `n = 3`.

```
              e0=1  e1=2  e2=3
m0=1:          ✓     ✗     ✗
m1=2:          ✗     ✓     ✗
m2=unexpected: ✗     ✗     ✗   ← never matches
```

The matching finds `{e0→m0, e1→m1}` — count = 2 ≠ 3, mismatch. But without running the matching, we wouldn't know that `1` and `2` matched correctly and only `3` is unexpected. The diff would be much less informative.

#### The `pass-through-matcher` (subset path only)

When `embeds` fails and `actual` has more elements than `expected`, elements not assigned to any matcher are appended to the diff using a second sentinel:

```clojure
(def ^:private pass-through-matcher
  (reify Matcher
    (-match [_this actual]
      {::result/type   :match
       ::result/value  (model/->Extra actual)
       ::result/weight 0})   ; weight=0, annotated as Extra
    ...))
```

Unlike `unexpected-matcher` (which marks an element as wrong with weight=1), `pass-through-matcher` treats the element as **present-but-unchecked** — weight=0, annotated with `model/->Extra` so it appears as `(extra <value>)` in the diff. This distinguishes it visually from elements that genuinely passed a matcher, without incorrectly flagging it as `Unexpected`.

The old algorithm (permutations) used `unexpected-matcher` for extras in `embeds` too, which was semantically wrong: it marked valid extra elements as errors with weight=1, inflating the mismatch cost. The `pass-through-matcher` fixes that while preserving the visual indication that those elements were not verified.

```clojure
;; (m/embeds [(m/equals 1) (m/equals 5)]) against [1 2 3]

;; BEFORE (permutation algorithm): extra wrongly marked as Unexpected (weight=1)
;; [1, Mismatch(5 ≠ 2), Unexpected(3)]

;; AFTER (bipartite + pass-through): extra annotated as Extra (weight=0)
;; [1, Mismatch(5 ≠ 2), Extra(3)]
;;  ↑        ↑            ↑
;; match  mismatch   present but not checked (not an error)
```

#### `min-cost-assign`: optimal assignment for unmatched pairs

```clojure
(defn- min-cost-assign [unmatched-mi available-ej matrix matchers]
  (let [groups      (group-by #(identical? (nth matchers %) unexpected-matcher) unmatched-mi)
        regular-mi  (vec (get groups false []))  ; real unmatched matchers
        extra-mi    (vec (get groups true []))   ; unexpected-matchers
        ejs         (vec available-ej)
        k           (min (count regular-mi) (count ejs))
        regular-ejs (subvec ejs 0 k)
        extra-ejs   (subvec ejs k)]
    (if (zero? k)
      ;; Only unexpected — pair linearly (any order is optimal)
      (mapv vector extra-mi extra-ejs)
      ;; Regular matchers — try all permutations and pick the lowest-cost one
      (let [cost (fn [pairs]
                   (reduce (fn [acc [mi ej]]
                             (+ acc (::result/weight (get-in matrix [mi ej]))))
                           0 pairs))]
        (into (->> (perms-of regular-ejs)
                   (map (fn [perm] (mapv vector regular-mi perm)))
                   (reduce (fn [best a] (if (< (cost a) (cost best)) a best))))
              (mapv vector extra-mi extra-ejs))))))
```

The `identical?` separation (reference comparison against the singleton) is critical: without it, cases with many `unexpected-matcher`s would generate enormous factorials.

### Why doesn't this blow up?

In real mismatch cases, the number of **unmatched regular matchers** (`k`) is almost always small (1-3). Only those go through `perms-of`. The `unexpected-matcher`s — which can be numerous — are paired in O(N) without permutations.

```
Before: N! where N = all matchers (including unexpected)
After:  k! where k = unmatched regular matchers (k << N)
```

### Functions added (AFTER)

| Function | What it does |
|---|---|
| `build-match-matrix` | Pre-computes all match(mi, ej) results into a matrix |
| `try-augment` | Tries to find an augmenting path in the bipartite graph (Kuhn) |
| `max-bipartite-matching` | Orchestrates Kuhn's algorithm over all matchers |
| `perms-of` | Generates permutations of a vector (local implementation, no longer an external dependency) |
| `min-cost-assign` | Assigns unmatched matchers to unmatched elements with minimum cost |
| `pass-through-matcher` | Sentinel for extra elements in `embeds` mismatches: returns `:match` with weight=0 and value `(model/->Extra actual)`, making extra elements visible in the diff as `(extra <value>)` — present but unchecked, not an error |

### Functions removed (AFTER)

| Function | Replaced by |
|---|---|
| `matched-successfully?` | Count of `match-to` vs `n` |
| `residual-matching-weight` | Cost computed inside `min-cost-assign` |
| `matches-in-any-order?` | Bipartite matching (`max-bipartite-matching`) |
| `better-mismatch?` | Cost function in `min-cost-assign` |
| `matched-or-best-matchers` | Logic embedded in `match-all-permutations` |

---

## Changes in `deps.edn`

```clojure
;; BEFORE: math.combinatorics was a PRODUCTION dependency
:deps {org.clojure/clojure              {:mvn/version "1.8.0"}
       org.clojure/math.combinatorics   {:mvn/version "0.2.0"}}  ; <-- production

;; AFTER: math.combinatorics moved to DEV dependency (tests only)
:deps {org.clojure/clojure {:mvn/version "1.8.0"}}

:aliases
  {:dev
   {:extra-deps {org.clojure/test.check         {:mvn/version "1.1.1"}
                 midje/midje                    {:mvn/version "1.10.9"}
                 org.clojure/math.combinatorics {:mvn/version "0.2.0"}}}} ; <-- dev/test only
```

The dependency still exists, but now **only in tests** — where `combo/permutations` is used to generate test cases, not as part of the algorithm itself.

---

## Complexity Comparison

| Scenario | Before | After |
|:---|:---:|:---:|
| Match found, N elements | O(N! × N) | O(N × M) |
| Mismatch, k regular matchers | O(N! × N) | O(N × M) matching + O(k! × k), k ≪ N |
| Mismatch with many `unexpected` | O(N! × N) | O(N × M) matching + O(k!) for k regular matchers |
| 7 elements, match | ~35,000 ops | ~75 ops (49 matrix + ~26 Kuhn) |
| 10 elements, match | ~36 million ops | ~150 ops (100 matrix + ~55 Kuhn) |
| 15 elements, 2 expected | ~1.3 trillion (hangs) | ~300 ops (225 matrix + Kuhn + O(2!) for 2 unmatched) |

---

## Known Trade-offs

### Constant overhead for small N

`build-match-matrix` always computes **all pairs** (matcher × element) before running Kuhn — even when a complete match could be confirmed with fewer operations.

```clojure
;; matrix[i][j] = result of match(matcher_i, element_j)
;; For N=3, this is always 9 operations — no shortcut
(defn- build-match-matrix [matchers elements]
  (mapv (fn [m] (mapv #(match m %) elements)) matchers))
```

**Comparison with the previous algorithm for `(in-any-order [1 2 3])` vs `[1 2 3]` (same order):**

| Algorithm | Operations in best case |
|:---|:---:|
| Before (permutations) | 3 — identity permutation is tried first and matches immediately |
| After (bipartite) | 9 — full matrix always computed before Kuhn |

**For N ≥ 7 with any ordering**, the new algorithm is already much faster. The overhead is only observable for N ≤ 5 and only when elements are already in the "right" order — an atypical scenario for `in-any-order`, which exists precisely for cases where order is not guaranteed.

**Why not implement two strategies (≤ 5 uses permutations, > 5 uses bipartite)?**

Not worth it. The gain would be imperceptible in absolute terms (nanoseconds for N ≤ 5) and would add maintenance complexity: two code paths to test, document, and evolve. The right cutoff point would also be arbitrary.

### Greedy fallback when k exceeds the permutation threshold

`min-cost-assign` exhausts all `k!` permutations of unmatched regular matchers to find the minimum-cost pairing. For large `k` this becomes prohibitively slow:

| k | k! permutations | measured avg (JVM, Apple M-series) |
|:--:|---:|:---:|
| 6 | 720 | ~2ms |
| 7 | 5,040 | ~14ms |
| 8 | 40,320 | ~120ms |
| 9 | 362,880 | ~2s |
| 10 | 3,628,800 | ~27s |

When `k > max-perm-k` (currently 8), `min-cost-assign` falls back to **greedy assignment** — pairs matchers with elements by index order, no permutations. 120ms on the failure path is acceptable; 2s+ is not.

**Realistic trigger:** a migration that changes a shared field (e.g., `:a`) across all records. Every matcher fails bipartite matching because no matcher accepts any element → `k = N`.

**What the user sees:**

- **Actual in same order as expected:** greedy pairs by index, which coincides with the optimal pairing. Each entry shows only the changed field — clean diff.
- **Actual in different order:** greedy may pair wrong maps together, producing a noisier diff with more apparent mismatches per entry than the true minimum. The values shown are real — no invented data — but the pairing is suboptimal.

Better a suboptimal diff than a hang.

---

### Non-global optimality of the error diff

Kuhn finds the matching with **maximum cardinality** — the greatest number of (matcher, element) pairs where all matchers pass. It does not guarantee that its choice minimizes the total diff cost when multiple maximum matchings exist.

**Example where Kuhn may make a suboptimal choice:**

```
Matchers          Elements
  m0               e0   e1
  m1               e2

  match(m0, e0) = MATCH   match(m0, e1) = MATCH
  match(m1, e0) = MATCH   match(m1, e2) = MISMATCH (weight=5)
  match(m1, e1) = MISMATCH (weight=1)
```

Two maximum matchings of size 1 are possible for m0: `{m0→e0}` or `{m0→e1}`. Depending on which Kuhn picks, the element left for `min-cost-assign` to pair with m1 will differ:

- If Kuhn picks `{m0→e0}` → m1 is left with e1 and e2 → `min-cost-assign` picks e1 (weight=1) ✓
- If Kuhn picks `{m0→e1}` → m1 is left with e0 and e2 → `min-cost-assign` picks e0 (weight=5) — noisier diff

`min-cost-assign` is optimal **given** the matching Kuhn already fixed, but cannot correct a poor choice made earlier.

**Why accept this limitation?**

The globally optimal algorithm for this problem would be the **Hungarian algorithm** (minimum-cost matching), with O(N³) complexity. For a test matcher, a slightly suboptimal diff in rare cases does not justify the additional complexity. In practice, the problematic scenario — multiple maximum matchings where choices significantly affect the diff — is uncommon.

---

## Tests That Prove Correctness

### 1. Match with the fewest possible errors

**File:** `test/clj/matcher_combinators/matchers_test.clj` — `deftest in-any-order`

```clojure
(testing "always prints the match with the fewest number of matchers that don't match"
  (is (every? one-mismatch?
              (map #(::result/value (c/match (m/in-any-order [1 2 3 4]) %))
                   (combo/permutations [1 2 3 500])))))
```

This test generates **all permutations** of `[1 2 3 500]` and verifies that, for any input ordering, the error report shows **exactly 1 wrong element** (the `500`). If the algorithm paired suboptimally, it could show 2 or more differences.

### 2. Minimum-cost assignment in the mismatch path

**File:** `test/clj/matcher_combinators/matchers_test.clj` — `deftest ordering`

```clojure
;; Weight matrix for in-any-order [{:a 1} {:a 1 :b 2}] vs [{:a 2} {:b 2}]:
;;
;;              {:a 2}  {:b 2}
;;  {:a 1}       w=1     w=1    ← :a missing
;;  {:a 1,:b 2}  w=2     w=1    ← :a wrong + :b missing  vs  only :a missing
;;
;; Optimal: {:a 1}→{:b 2}, {:a 1 :b 2}→{:a 2}  (total cost = 2)
;; Simple greedy would give: {:a 1}→{:a 2}, {:a 1 :b 2}→{:b 2}  (total cost = 3)

(is (every? one-mismatch?
            (->> [{:a 2} {:b 2}]
                 (c/match (m/in-any-order [{:a 1} {:a 1 :b 2}]))
                 ::result/value
                 (map vals))))
```

Verifies that `min-cost-assign` picks the **lowest total cost** assignment, not just the first one it finds.

### 3. Large list that used to hang

**File:** `test/clj/matcher_combinators/midje_test.clj` — `big-list`

```clojure
(def big-list [[:abc #{1}]
               [:xyz #{2 3 4 5 6 7}]
               [:def #{5 6}]
               [:ghi #{9 10 8 11 1}]
               [:jkl #{9 2 3 4 12 5 10 13 6 14 15 16 17 7 8 11 1}]])  ; set with 17 elements

big-list =not=> (match (m/embeds [[:jkl #{1 2}]]))
```

`:jkl` has a set of 17 elements. With the old algorithm, `normalize-inputs-length` would add 15 `unexpected-matcher`s → `perms-of(15)` → **15! ≈ 1.3 trillion permutations** → process hangs. With the new algorithm, the 15 `unexpected-matcher`s are separated and paired in O(N). Runs in milliseconds.

### 4. Matches with 7 to 10 elements

**File:** `test/clj/matcher_combinators/midje_test.clj` — `facts "test large-ish in-any-order matches"`

```clojure
(fact "10 items"
  ["J" "A" "B" "C" "D" "E" "F" "G" "H" "I"]
  => (match (m/in-any-order ["A" "B" "C" "D" "E" "F" "G" "H" "I" "J"])))
```

Before: 10! = 3,628,800 permutations. After: bipartite matching finishes in ~100 operations.

### 5. Parity with Midje's native matcher

**File:** `test/clj/matcher_combinators/midje_test.clj` — `fact "Find optimal in-any-order matching just like midje"`

```clojure
[1 3] => (midje/just [odd? 1] :in-any-order)         ; reference behavior (Midje)

{:a [1 3]} => (match (m/equals {:a (m/in-any-order [odd? 1])}))  ; must be equal
{:a [1 3]} => (match (m/equals {:a (m/in-any-order [1 odd?])}))  ; expected order doesn't matter
```

Ensures the new algorithm produces exactly the same result as Midje's native `:in-any-order` matcher.

### 6. Core unit tests (edge cases)

**File:** `test/clj/matcher_combinators/core_test.clj`

```clojure
(let [matchers [(pred-matcher odd?) (pred-matcher even?)]]
  (testing "mismatch if there are more matchers than actual elements"
    (is (match? {::result/type  :mismatch
                 ::result/value (matchers/in-any-order [(model/->Missing any?) 5])
                 ::result/weight 1}
                (#'core/match-any-order matchers [5] false)))
    (is (match? {::result/type   :mismatch
                 ::result/value  (matchers/in-any-order [5 (model/->Missing any?)])
                 ::result/weight 1}
                (#'core/match-any-order matchers [5] true)))))
```

> **On removed tests:** The refactor removed `matches-in-any-order?`, so unit tests that tested that function directly were deleted — there is no way to keep them without the function. They covered 3 groups:
>
> | Removed behavior | Remaining coverage |
> |---|---|
> | More matchers than elements (`subset=false`) | `core_test.clj:382–386` (direct `match-any-order`) + `core_test.clj:275–278` (integration) |
> | More matchers than elements (`subset=true`) | `core_test.clj:387–390` (direct `match-any-order`, kept) |
> | Subset with more elements than matchers (match) | `midje_test.clj:125` — `[5 1 4 2] => (match (m/embeds [odd? even?]))` |
> | Subset with missing element (mismatch) | `midje_test.clj:126` — `[5 1 4 2] =not=> (match (m/embeds [5 1 4 2 6]))` |
> | Identical matchers | `core_test.clj:263–267` — `in-any-order [(equals 2) (equals 2)]` vs `[2 2]` |
>
> The removals are safe: the behaviors remain covered at the integration level, which is the correct level for a refactored algorithm. The most critical regression test — `big-list` in `midje_test.clj` — proves that the scenario that used to hang (17 `unexpected-matcher`s) now executes correctly.

---

## Model changes

`model.cljc` gained one new record type:

```clojure
(defrecord Extra [actual])
```

Used exclusively by `pass-through-matcher` to annotate extra elements in `embeds` mismatches. Rendered by `printer.cljc` as `(extra <value>)` — no color, since it is not an error.

`Extra` is intentionally absent from `complete-mismatch?` and `mismatch+?` in the printer: in abbreviated output mode (`*use-abbreviation* = true`), extra elements are filtered out because they are not failures.

---

## Performance Benchmarks

### Methodology

Each scenario was measured with a `bench-ms` helper that runs the thunk 500 times and records min/avg/max in milliseconds:

```clojure
(defn- bench-ms [runs thunk]
  (let [times (mapv (fn [_]
                      (let [t0 (System/nanoTime)
                            _  (thunk)
                            t1 (System/nanoTime)]
                        (/ (- t1 t0) 1e6)))
                    (range runs))
        mn    (apply min times)
        avg   (/ (reduce + times) runs)
        mx    (apply max times)]
    [mn avg mx]))
```

### Scenarios

Four scenarios were benchmarked, each at N = 5, 10, 15, and 50:

**Scenario 1 — `in-any-order`: complete match (happy path)**

Exercises the bipartite matching path for inputs that fully match in shuffled order.

```clojure
(deftest in-any-order-match-<size>
  (let [n <N> data (int-list n)]
    (is (= :match (::result/type (c/match (m/in-any-order (shuffled data)) data))))
    (print-row ... (bench-ms 500 #(c/match (m/in-any-order (shuffled data)) data)))))
```

**Scenario 2 — `in-any-order`: one mismatch**

Exercises bipartite matching + `min-cost-assign` when N−1 elements are correct and 1 is a sentinel that matches nothing.

```clojure
(defn- with-one-wrong [n]
  (conj (vec (range (dec n))) ::wrong))

(deftest in-any-order-mismatch-<size>
  (let [n <N> actual (with-one-wrong n) matcher (m/in-any-order (int-list n))]
    (is (= :mismatch (::result/type (c/match matcher actual))))
    (print-row ... (bench-ms 500 #(c/match matcher actual)))))
```

**Scenario 3 — `embeds`: 2 matchers against N elements (critical regression)**

The case that used to hang: old algorithm generated `(N−2)!` permutations for `unexpected-matcher`s. New algorithm handles extras in O(N).

```clojure
(deftest embeds-many-extras-<size>
  (let [n <N> actual (int-list n) matcher (m/embeds [0 1])]
    (is (= :match (::result/type (c/match matcher actual))))
    (print-row ... (bench-ms 500 #(c/match matcher actual)))))
```

**Scenario 4 — `embeds`: mismatch with many extras**

Same structure as scenario 3, but the expected element `::missing-elem` is absent from actual — exercises the mismatch path.

```clojure
(deftest embeds-mismatch-many-extras-<size>
  (let [n <N> actual (int-list n) matcher (m/embeds [0 ::missing-elem])]
    (is (= :mismatch (::result/type (c/match matcher actual))))
    (print-row ... (bench-ms 500 #(c/match matcher actual)))))
```

### Results (500 runs each, Apple M-series, JVM warm)

**`in-any-order` — complete match**

| N  | min (ms) | avg (ms) | max (ms) |
|:--:|:--------:|:--------:|:--------:|
|  5 | 0.00 | 0.00 | 0.02 |
| 10 | 0.01 | 0.01 | 0.02 |
| 15 | 0.02 | 0.04 | 0.13 |
| 50 | 0.11 | 0.14 | 1.77 |

**`in-any-order` — one mismatch**

| N  | min (ms) | avg (ms) | max (ms) |
|:--:|:--------:|:--------:|:--------:|
|  5 | 0.01 | 0.01 | 0.03 |
| 10 | 0.02 | 0.02 | 0.05 |
| 15 | 0.03 | 0.05 | 0.21 |
| 50 | 0.14 | 0.18 | 2.18 |

**`embeds` — 2 matchers, N extras (critical regression case)**

| N  | min (ms) | avg (ms) | max (ms) | note |
|:--:|:--------:|:--------:|:--------:|------|
|  5 | 0.00 | 0.00 | 0.01 | |
| 10 | 0.00 | 0.00 | 0.07 | |
| 17 | 0.00 | 0.00 | 0.01 | used to hang |
| 50 | 0.00 | 0.00 | 0.01 | |

**`embeds` — mismatch with many extras**

| N  | min (ms) | avg (ms) | max (ms) |
|:--:|:--------:|:--------:|:--------:|
| 17 | 0.01 | 0.01 | 0.14 |
| 50 | 0.02 | 0.03 | 0.11 |

### Interpretation

- `in-any-order` scales polynomially: N=50 finishes in ~0.14 ms avg. The old algorithm would have required 50! ≈ 3×10⁶⁴ iterations.
- `embeds` with many extras is essentially free regardless of N — the `unexpected-matcher` separation in `min-cost-assign` means extra elements are paired in O(N) without permutations. N=17, which previously caused the process to hang, now completes in under 0.01 ms.
- The mismatch path is slightly slower than the match path (bipartite matching + `min-cost-assign` + diff construction vs. bipartite matching alone), but remains sub-millisecond up to N=50.

---

## Final Results

```
clj-test  → 48 tests,  182 assertions, 0 failures, 0 errors  ✓
midje     → 117 checks, 0 failures                            ✓
```
