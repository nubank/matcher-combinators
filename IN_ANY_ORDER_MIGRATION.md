# Migração do `in-any-order`: de Permutações para Bipartite Matching

## Contexto

O matcher `in-any-order` verifica se dois sequences contêm os mesmos elementos, independentemente da ordem. O algoritmo precisa resolver dois problemas distintos:

1. **Decidir se há um match** — existe alguma associação entre matchers e elementos onde todos os matchers passam?
2. **Gerar o melhor relatório de erro** — quando não há match, qual associação produz o diff com menor número de diferenças?

---

## Antes: Busca Exaustiva por Permutações — O(N! × N)

### Como funcionava

A implementação original usava `clojure.math.combinatorics/permutations` para gerar **todas** as permutações dos matchers e, para cada permutação, tentava casar com os elementos em ordem.

```clojure
;; master
(defn- match-all-permutations [expected elements subset?]
  (let [[matchers elements] (if subset?
                              [expected elements]
                              (normalize-inputs-length expected elements))
        matcher-perms       (combo/permutations matchers)   ; <-- gera N! permutações
        find-best-match     (matched-or-best-matchers elements subset?)
        result              (reduce find-best-match
                                    {:matched   []
                                     :weight    Integer/MAX_VALUE
                                     :elements  elements
                                     :unmatched matchers}
                                    matcher-perms)]
    ...))
```

A função `matches-in-any-order?` percorria cada permutação dos matchers e tentava casar greedy com os elementos. A melhor permutação (com mais matches e menor peso total de mismatch) era selecionada.

### Problema de escalabilidade

| N (elementos) | N! permutações |
|---|---|
| 7 | 5.040 |
| 10 | 3.628.800 |
| 13 | 6.227.020.800 |
| 15 | 1.307.674.368.000 (~1,3 trilhão) |

Para listas com mais de ~10 elementos, o tempo de execução se tornava proibitivo.

### Dependência externa

O algoritmo dependia de `clojure.math.combinatorics` para gerar as permutações, o que era uma dependência exclusiva para esse uso.

---

## Depois: Bipartite Matching (Kuhn) + Permutações só para o caminho de erro

### Separação de responsabilidades

A nova implementação divide o problema em duas etapas com complexidades distintas:

#### Etapa 1 — Decidir se há match: Algoritmo de Kuhn — O(N² × M)

O algoritmo de Kuhn (também chamado de Hungarian path method ou augmenting path) encontra o **máximo matching bipartido**: o maior conjunto de pares (matcher_i, elemento_j) onde cada matcher e cada elemento aparecem no máximo uma vez, e todos os pares indicam match.

```
Matchers          Elementos
    m0 ─────────── e0
    m1 ─── ✓ ──── e1
    m2 ─────────── e2
         ╲
          ✓────── e3
```

Quando o número de pares no matching máximo é igual ao número de matchers, há um match completo.

```clojure
(defn- try-augment [i matrix match-to used]
  ;; Para o matcher i, tenta encontrar um elemento livre ou
  ;; "empurrar" um elemento já alocado para outro matcher (caminho aumentante).
  (let [n (count (get matrix 0 []))]
    (loop [j 0 match-to match-to used used]
      (cond
        (>= j n)
        [false match-to used]

        (or (contains? used j)
            (not (indicates-match? (get-in matrix [i j]))))
        (recur (inc j) match-to used)

        :else
        (let [used'        (conj used j)
              prev         (get match-to j -1)
              [ok? mt' u'] (if (neg? prev)
                             [true match-to used']
                             (try-augment prev matrix match-to used'))]
          (if ok?
            [true (assoc mt' j i) u']
            (recur (inc j) match-to u')))))))

(defn- max-bipartite-matching [matrix]
  (reduce (fn [mt i]
            (let [[_ mt'] (try-augment i matrix mt #{})]
              mt'))
          {}
          (range (count matrix))))
```

#### Etapa 2 — Gerar o melhor relatório de erro: `perms-of` restrito

Quando não há match completo, a implementação precisa decidir como associar os matchers não-matched aos elementos não-matched para gerar o diff mais informativo. Aqui `perms-of` é mantido, mas **apenas sobre os matchers regulares não-matched** — em geral um subconjunto muito pequeno (k ≤ 2-3).

```clojure
(defn- perms-of [v]
  (if (empty? v)
    [[]]
    (for [i (range (count v))
          p (perms-of (into (subvec v 0 i) (subvec v (inc i))))]
      (into [(nth v i)] p))))

(defn- min-cost-assign [unmatched-mi available-ej matrix matchers]
  (let [groups      (group-by #(identical? (nth matchers %) unexpected-matcher) unmatched-mi)
        regular-mi  (vec (get groups false []))
        extra-mi    (vec (get groups true []))
        ejs         (vec available-ej)
        k           (min (count regular-mi) (count ejs))
        regular-ejs (subvec ejs 0 k)
        extra-ejs   (subvec ejs k)]
    (if (zero? k)
      (mapv vector extra-mi extra-ejs)
      (let [cost (fn [pairs]
                   (reduce (fn [acc [mi ej]]
                             (+ acc (::result/weight (get-in matrix [mi ej]))))
                           0 pairs))]
        (into (->> (perms-of regular-ejs)
                   (map (fn [perm] (mapv vector regular-mi perm)))
                   (reduce (fn [best a] (if (< (cost a) (cost best)) a best))))
              (mapv vector extra-mi extra-ejs))))))
```

### O papel do `unexpected-matcher` e por que ele importa

`normalize-inputs-length` equaliza o comprimento das sequências de matchers e elementos antes do matching. Quando `actual` tem **mais** elementos do que `expected`, adiciona instâncias de `unexpected-matcher` para preencher:

```clojure
(def ^:private unexpected-matcher
  (reify Matcher
    (-match [_this actual]
      {::result/type   :mismatch
       ::result/value  (model/->Unexpected actual)
       ::result/weight 1})   ; <-- sempre retorna weight=1, seja qual for o elemento
    ...))
```

**Propriedade crítica:** `unexpected-matcher` sempre retorna `weight=1` para qualquer elemento. Isso significa que a ordem em que os `unexpected-matcher`s são associados aos elementos sobrando **não afeta o custo total** — qualquer permutação deles é igualmente ótima.

Por isso, `min-cost-assign` separa os `unexpected-matcher`s dos matchers regulares usando `identical?` (comparação de referência ao singleton), aplica `perms-of` apenas sobre os matchers regulares, e pareia os `unexpected-matcher`s com os elementos restantes em ordem linear.

### O bug que teria ocorrido sem essa separação

Sem a separação, um caso como:

```clojure
;; big-list tem 5 elementos, onde [:jkl ...] contém um set de 17 elementos
big-list =not=> (match (m/embeds [[:jkl #{1 2}]]))
```

...dispararia `SetEquals` com 2 matchers esperados contra 17 elementos reais. `normalize-inputs-length` adicionaria 15 `unexpected-matcher`s. Todos os 15 nunca são matched pelo bipartite matching (pois sempre retornam mismatch). Logo `min-cost-assign` seria chamado com `k=15`, gerando `perms-of([0..14])` = **15! ≈ 1,3 trilhão de permutações**. O processo trava por horas.

---

## Fluxo completo pós-migração

```
match-all-permutations(expected, elements, subset?)
│
├─ build-match-matrix(matchers, elems)     → matrix[i][j] = resultado de match(mi, ej)
│
├─ max-bipartite-matching(matrix)          → match-to: {ej → mi} para matches válidos
│
├─ (count match-to) == N?
│   ├─ SIM  → :match, weight=0, retorna
│   └─ NÃO → caminho de mismatch:
│
│       ├─ Identificar unmatched-mi e unmatched-ej
│       ├─ min-cost-assign(unmatched-mi, unmatched-ej, matrix, matchers)
│       │   ├─ Separar unexpected-matchers (identical?) dos matchers regulares
│       │   ├─ perms-of(regular-ejs) → k! permutações (k pequeno, ≤ 2-3 tipicamente)
│       │   ├─ Escolher permutação com menor custo total
│       │   └─ Parear unexpected-matchers com elementos restantes linearmente
│       │
│       └─ match(EqualsSeq, elementos-reordenados) → gera relatório de diff
```

---

## Comparação de complexidade

| Cenário | Antes | Depois |
|---|---|---|
| Match encontrado | O(N! × N) — percorre todas as permutações | O(N² × M) — bipartite matching |
| Mismatch, k matchers regulares | O(N! × N) | O(k! × k) onde k << N |
| Mismatch com muitos `unexpected` | O(N! × N) — N incluía unexpected | O(k! × k) — unexpected separados |
| 7 elementos, match | ~35.000 operações | ~49 operações |
| 15 elementos, 2 expected | ~1,3 trilhão (trava) | O(1) para unexpected + O(2) para regulares |

---

## Mudanças no código

### Dependência removida

```clojure
;; antes
(:require [clojure.math.combinatorics :as combo] ...)

;; depois
;; removida — perms-of implementado localmente para o subconjunto pequeno
```

### Funções removidas

| Função | Motivo |
|---|---|
| `matched-successfully?` | Substituída pela contagem de `match-to` |
| `residual-matching-weight` | Substituída pelo custo calculado em `min-cost-assign` |
| `matches-in-any-order?` | Substituída pelo bipartite matching |
| `better-mismatch?` | Substituída pela função de custo em `min-cost-assign` |
| `matched-or-best-matchers` | Substituída pela lógica de `match-all-permutations` |

### Funções adicionadas

| Função | Responsabilidade |
|---|---|
| `build-match-matrix` | Pré-computa todos os resultados de match(mi, ej) |
| `try-augment` | Tenta encontrar caminho aumentante no grafo bipartido (Kuhn) |
| `max-bipartite-matching` | Orquestra o algoritmo de Kuhn sobre todos os matchers |
| `perms-of` | Gera permutações de um vetor (local, substituindo `combo/permutations`) |
| `min-cost-assign` | Associa matchers não-matched a elementos não-matched com custo mínimo |

---

## Testes que comprovam a correção

### `test/clj/matcher_combinators/matchers_test.clj`

#### `deftest in-any-order`
Verifica que o relatório de erro sempre aponta o menor número de matchers que falham:

```clojure
(testing "always prints the match with the fewest number of matchers that don't match"
  (is (every? one-mismatch?
              (map #(::result/value (c/match (m/in-any-order [1 2 3 4]) %))
                   (combo/permutations [1 2 3 500])))))
```

Para toda permutação de `[1 2 3 500]`, o diff deve mostrar exatamente **um** elemento errado (o `500`). Isso valida que o bipartite matching encontra o matching ótimo.

#### `deftest ordering`
Valida que `min-cost-assign` produz a associação de custo mínimo para matchers regulares:

```clojure
;; Matriz de pesos para in-any-order [{:a 1} {:a 1 :b 2}] vs [{:a 2} {:b 2}]:
;;
;;            {:a 2}  {:b 2}
;; {:a 1}      w=1     w=1    ← missing :a
;; {:a 1,:b2}  w=2     w=1    ← :a mismatch + :b missing  vs  :a missing
;;
;; Ótimo: m0→e1, m1→e0 (custo 2) em vez de m0→e0, m1→e1 (custo 3)

(is (every? one-mismatch?
            (->> [{:a 2} {:b 2}]
                 (c/match (m/in-any-order [{:a 1} {:a 1 :b 2}]))
                 ::result/value
                 (map vals))))
```

Um greedy simples falharia aqui por quebrar o empate de forma errada. `perms-of` sobre os matchers regulares garante a escolha ótima.

### `test/clj/matcher_combinators/midje_test.clj`

#### `fact` com `big-list` (linha 121)
O teste de mismatch que antes travava indefinidamente:

```clojure
(def big-list [[:abc #{1}]
               [:xyz #{2 3 4 5 6 7}]
               [:def #{5 6}]
               [:ghi #{9 10 8 11 1}]
               [:jkl #{9 2 3 4 12 5 10 13 6 14 15 16 17 7 8 11 1}]])

big-list =not=> (match (m/embeds [[:jkl #{1 2}]]))
```

O `[:jkl ...]` tem 17 elementos no set. Antes: `normalize-inputs-length` adicionava 15 `unexpected-matcher`s → `perms-of(15)` → travava. Agora: os 15 `unexpected-matcher`s são separados e pareados em O(N), executa em milissegundos.

#### `facts "test large-ish in-any-order matches"` (linha 132)
Testes de match com 7 a 10 elementos em ordem embaralhada:

```clojure
(fact "10 items"
  ["J" "A" "B" "C" "D" "E" "F" "G" "H" "I"]
  => (match (m/in-any-order ["A" "B" "C" "D" "E" "F" "G" "H" "I" "J"])))
```

Com o algoritmo antigo, 10! = 3,6 milhões de permutações. Com bipartite matching, termina instantaneamente.

#### `fact "Find optimal in-any-order matching just like midje"` (linha 149)
Valida paridade de comportamento com o matcher `:in-any-order` nativo do Midje:

```clojure
[1 3] => (midje/just [odd? 1] :in-any-order)
{:a [1 3]} => (match (m/equals {:a (m/in-any-order [odd? 1])}))
{:a [1 3]} => (match (m/equals {:a (m/in-any-order [1 odd?])}))
```

### `test/cljs/` (ClojureScript via shadow-cljs + Node.js)

Os mesmos caminhos de matching e mismatch são exercidos nos testes ClojureScript, garantindo que a implementação em `.cljc` funciona igualmente em ambas as plataformas.

---

## Resultado

```
clj-test  → 76 tests, 0 failures, 0 errors  ✓
midje     → 117 checks, 0 failures           ✓
test:node → 8 tests, 23 assertions, 0 errors ✓
```
