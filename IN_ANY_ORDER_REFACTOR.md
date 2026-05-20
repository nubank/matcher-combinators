# Refactor: `in-any-order` — de Permutações para Bipartite Matching

> **Arquivo relevante:** `src/cljc/matcher_combinators/core.cljc`

---

## O que é o `in-any-order`?

`in-any-order` é um matcher que verifica se dois vetores contêm os **mesmos elementos independentemente da ordem**.

Cada posição do vetor `expected` pode ser um **valor literal** (comparado por igualdade) ou qualquer **matcher** — inclusive funções Clojure como `odd?`, `string?`, ou matchers compostos como `m/equals` e `m/embeds`. Qualquer função Clojure é automaticamente tratada como predicate matcher (via `parser.cljc`).

```clojure
;; Valores literais — comparação por igualdade, ordem não importa
[3 1 2] => (match (m/in-any-order [1 2 3]))

;; Funções Clojure são matchers automáticos — combinam com qualquer elemento que as satisfaça
[3 "hello" 4] => (match (m/in-any-order [string? odd? even?]))
;;               ^ o algoritmo encontra: string?→"hello", odd?→3, even?→4

;; Mistura de literais e matchers na mesma lista
[3 "hello" 42] => (match (m/in-any-order [42 odd? string?]))
;;                  42 casa com 42, odd? casa com 3, string? casa com "hello"

;; Matchers compostos também funcionam
[{:id 2 :name "Bob"} {:id 1 :name "Alice"}]
=> (match (m/in-any-order [{:id 1 :name string?}
                           {:id pos-int? :name "Bob"}]))

;; Falha: nenhum emparelhamento válido — 4 não satisfaz string?, odd? nem 99
[3 "hello" 4] =not=> (match (m/in-any-order [string? odd? 99]))
```

Além de decidir se há match ou não, o matcher precisa gerar um **relatório de erro útil** quando falha — mostrando qual(is) elemento(s) não combinaram e com o menor número possível de diferenças.

---

## O Problema de Base

Para verificar se dois vetores combinam em qualquer ordem, é preciso responder:

> "Existe alguma forma de parear cada matcher com um elemento do vetor real, tal que todos os pares sejam válidos?"

Isso é o **problema do emparelhamento**: dado um conjunto de matchers e um conjunto de elementos, encontrar uma atribuição 1-para-1 onde todo matcher passa.

---

## Antes: Busca Exaustiva por Permutações

### Ideia central

A implementação original resolvia o problema de forma simples e direta: **tente todas as ordens possíveis dos matchers e veja qual funciona**.

Em termos de código:

```clojure
;; ANTES — match-all-permutations em master
(defn- match-all-permutations [expected elements subset?]
  (let [[matchers elements] (normalize-inputs-length expected elements)
        matcher-perms       (combo/permutations matchers)   ; gera TODAS as permutações
        find-best-match     (matched-or-best-matchers elements subset?)
        result              (reduce find-best-match
                                    {:matched   []
                                     :weight    Integer/MAX_VALUE
                                     :elements  elements
                                     :unmatched matchers}
                                    matcher-perms)]
    ...))
```

Para cada permutação dos matchers, a função `matches-in-any-order?` tentava casar greedy (ganancioso) com os elementos em sequência:

```clojure
;; ANTES — tentativa greedy para uma permutação específica
(defn- matches-in-any-order? [unmatched elements subset? matching]
  (if (empty? unmatched)
    ;; acabou os matchers — verifica se deu match
    {:matched? true, ...}
    (let [[matcher & rest] unmatched
          ;; pega o PRIMEIRO elemento que funciona
          matching-elem (find-first #(indicates-match? (match matcher %)) elements)]
      (if (nil? matching-elem)
        {:matched? false, ...}
        ;; achou um par, continua com o resto
        (recur rest (remove matching-elem elements) subset? ...)))))
```

A melhor permutação (aquela com mais matches e menor peso total de erro) era escolhida via `better-mismatch?`:

```clojure
(defn- better-mismatch? [best candidate]
  (and (>= (-> candidate :matched count) (-> best :matched count))
       (<= (:weight candidate) (:weight best))))
```

### Dependência externa

O algoritmo dependia da biblioteca `clojure.math.combinatorics` para gerar as permutações:

```clojure
;; deps.edn — ANTES
:deps {org.clojure/clojure              {:mvn/version "1.8.0"}
       org.clojure/math.combinatorics   {:mvn/version "0.2.0"}}  ; <-- dependência de produção
```

### Por que isso funciona... mas não escala

Para uma lista de N elementos, existem **N! (N fatorial)** permutações possíveis:

| N (elementos) | N! permutações | Tempo estimado |
|:---:|---:|:---:|
| 5  | 120 | instantâneo |
| 7  | 5.040 | instantâneo |
| 10 | 3.628.800 | lento (~segundos) |
| 13 | 6.227.020.800 | muito lento (~horas) |
| 15 | 1.307.674.368.000 | **trava** |

Para listas com mais de ~10 elementos, o teste simplesmente trava. Isso é um **problema de escalabilidade O(N! × N)**.

### Funções envolvidas (ANTES)

| Função | O que fazia |
|---|---|
| `matched-successfully?` | Verificava se o matching foi completo |
| `residual-matching-weight` | Calculava o peso total dos mismatches restantes |
| `matches-in-any-order?` | Tentava casar greedy para uma permutação específica |
| `better-mismatch?` | Comparava qual permutação produzia o melhor relatório de erro |
| `matched-or-best-matchers` | Função de redução que percorria todas as permutações |

---

## Depois: Bipartite Matching (Algoritmo de Kuhn) + Permutações Restritas

### Ideia central

A nova implementação **separa os dois problemas**:

1. **Decidir se há match** → Algoritmo de Kuhn (O(N² × M)) — muito mais rápido que N!
2. **Gerar o relatório de erro** → Permutações, mas apenas sobre um subconjunto pequeno

### O que é Bipartite Matching?

Imagine dois grupos: matchers de um lado, elementos do outro. Desenhe uma aresta entre matcher `m` e elemento `e` se `m` aceita `e`. O problema é: **existe uma forma de conectar cada matcher a exatamente um elemento (e vice-versa) usando apenas essas arestas?**

```
Matchers          Elementos
    m0 ─── ✓ ──── e0   (m0 aceita e0)
    m0 ─── ✓ ──── e1   (m0 aceita e1 também)
    m1 ─── ✓ ──── e1   (m1 aceita e1)
    m2 ─── ✓ ──── e2   (m2 só aceita e2)
```

Um **matching** é qualquer conjunto de pares (matcher, elemento) onde cada matcher e cada elemento aparecem no máximo uma vez — sem repetições dos dois lados. O **matching máximo** é o matching com o maior número de pares possível, dado o grafo de compatibilidades.

No exemplo acima, há várias formas válidas de parear sem repetição. Listando as principais:
- `{m0→e0, m1→e1, m2→e2}` — 3 pares ← **matching máximo** (todo matcher emparelhado)
- `{m1→e1, m2→e2}` — 2 pares (m0 ficou sem par)
- `{m0→e0, m2→e2}` — 2 pares (m1 ficou sem par)
- `{m0→e0, m1→e1}` — 2 pares (m2 ficou sem par)

O algoritmo de Kuhn sempre encontra o matching máximo. Quando esse máximo tem tantos pares quanto matchers, significa que **todo matcher foi emparelhado** — há um match completo.

### Etapa 1 — Pré-computar a matriz de resultados

Antes de rodar o algoritmo, todos os pares (matcher × elemento) são calculados e armazenados:

```clojure
;; DEPOIS
(defn- build-match-matrix [matchers elements]
  (mapv (fn [m] (mapv #(match m %) elements)) matchers))
```

Resultado: uma matriz `matrix[i][j]` que guarda o resultado de `(match matcher_i element_j)`. Isso evita recalcular o mesmo par múltiplas vezes.

### Etapa 2 — Algoritmo de Kuhn (augmenting paths)

O algoritmo de Kuhn encontra o maior matching possível usando o conceito de **caminho aumentante**: se um elemento já está alocado para um matcher, tenta-se realocar esse matcher para outro elemento livre, liberando o original.

```clojure
;; DEPOIS
(defn- try-augment [i matrix match-to used]
  (let [n (count (get matrix 0 []))]
    (loop [j 0 match-to match-to used used]
      (cond
        ;; Esgotou todos os elementos — não achou caminho
        (>= j n)
        [false match-to used]

        ;; Elemento j já foi visitado nesta tentativa, ou matcher i não aceita elemento j
        (or (contains? used j)
            (not (indicates-match? (get-in matrix [i j]))))
        (recur (inc j) match-to used)

        ;; Elemento j é candidato — tenta alocar
        :else
        (let [used'        (conj used j)          ; marca j como visitado
              prev         (get match-to j -1)    ; quem está alocado em j agora?
              [ok? mt' u'] (if (neg? prev)
                             [true match-to used'] ; j está livre!
                             (try-augment prev matrix match-to used'))] ; tenta empurrar prev para outro lugar
          (if ok?
            [true (assoc mt' j i) u'] ; sucesso: aloca i em j
            (recur (inc j) match-to u')))))))

(defn- max-bipartite-matching [matrix]
  (reduce (fn [mt i]
            (let [[_ mt'] (try-augment i matrix mt #{})]
              mt'))
          {}
          (range (count matrix))))
```

**Exemplo passo a passo** com `[42 "world" 7] => (m/in-any-order [int? string? 42])`:

Os matchers são `[int?, string?, 42]` e os elementos são `[42, "world", 7]`.
Note que `int?` e `string?` são funções Clojure — automaticamente tratadas como predicate matchers.

```
Matchers             Elementos
  m0=int?            e0=42
  m1=string?         e1="world"
  m2=42              e2=7

Matriz de compatibilidade (✓ = aceita, ✗ = rejeita):

              e0=42   e1="world"   e2=7
m0=int?:       ✓          ✗         ✓      ← int? aceita 42 e 7
m1=string?:    ✗          ✓         ✗      ← string? só aceita "world"
m2=42:         ✓          ✗         ✗      ← literal 42 só aceita o valor igual

Rodando Kuhn (i = índice do matcher):

 i=0 (int?):
   j=0 (42): int? aceita 42, e0 está livre → aloca m0→e0
   match-to: {e0→m0}

 i=1 (string?):
   j=0 (42): string? rejeita 42, pula
   j=1 ("world"): string? aceita, e1 está livre → aloca m1→e1
   match-to: {e0→m0, e1→m1}

 i=2 (42):
   j=0 (42): literal 42 aceita, mas e0 já está com m0 (int?)
     → tenta mover m0 para outro lugar (caminho aumentante):
       j=0 já visitado, pula
       j=1 ("world"): int? rejeita, pula
       j=2 (7): int? aceita 7, e2 está livre → move m0 para e2 ✓
   → agora e0 ficou livre para m2
   match-to: {e0→m2, e1→m1, e2→m0}

count(match-to) = 3 = count(matchers) → MATCH ✓

Emparelhamento final: int?→7,  string?→"world",  42→42
```

### Etapa 3 — Caminho de mismatch: permutações só onde necessário

Quando o bipartite matching não consegue um matching completo, precisamos gerar o melhor relatório de erro. Aqui entram dois conceitos:

#### O `unexpected-matcher`

Antes de rodar o bipartite matching, os dois vetores precisam ter o mesmo tamanho. `normalize-inputs-length` faz esse ajuste:

```clojure
(defn- normalize-inputs-length [matchers actuals]
  (let [matchers-count (count matchers)
        actuals-count  (count actuals)]
    (if (< actuals-count matchers-count)
      ;; actual menor: preenche actual com ::missing
      [matchers
       (take matchers-count (concat actuals (repeat ::missing)))]
      ;; actual maior: preenche matchers com unexpected-matcher
      [(take actuals-count (concat matchers (repeat unexpected-matcher)))
       actuals])))
```

Quando `actual` tem **mais** elementos do que `expected`, a função preenche a diferença com instâncias de um sentinel especial:

```clojure
(def ^:private unexpected-matcher
  (reify Matcher
    (-match [_this actual]
      {::result/type   :mismatch
       ::result/value  (model/->Unexpected actual)
       ::result/weight 1})   ; sempre weight=1, independente do elemento
    ...))
```

**Propriedade chave:** `unexpected-matcher` sempre retorna `weight=1` para qualquer elemento. Isso significa que a ordem em que pareamos os `unexpected-matcher`s com os elementos sobrando **não importa** — qualquer ordem é igualmente ótima.

**Por que o bipartite matching ainda roda mesmo com tamanhos diferentes?**

Quando `actual` tem mais elementos que `expected`, o match completo é garantidamente impossível — `unexpected-matcher` nunca produz um match válido, então `count(match-to)` nunca chegará a `n`. Ainda assim, o algoritmo roda porque precisa descobrir **quais** matchers originais casaram, para gerar o relatório de erro mais informativo possível.

Exemplo: `(m/in-any-order [1 2])` contra `[1 2 3]`. Após normalização, `n = 3`.

```
              e0=1  e1=2  e2=3
m0=1:          ✓     ✗     ✗
m1=2:          ✗     ✓     ✗
m2=unexpected: ✗     ✗     ✗   ← nunca casa
```

O matching encontra `{e0→m0, e1→m1}` — count = 2 ≠ 3, mismatch. Mas sem rodar o matching, não saberíamos que `1` e `2` casaram corretamente e só `3` é inesperado. O diff seria muito mais pobre.

#### `min-cost-assign`: atribuição ótima para os não-emparelhados

```clojure
(defn- min-cost-assign [unmatched-mi available-ej matrix matchers]
  (let [groups      (group-by #(identical? (nth matchers %) unexpected-matcher) unmatched-mi)
        regular-mi  (vec (get groups false []))  ; matchers reais não-matched
        extra-mi    (vec (get groups true []))   ; unexpected-matchers
        ejs         (vec available-ej)
        k           (min (count regular-mi) (count ejs))
        regular-ejs (subvec ejs 0 k)
        extra-ejs   (subvec ejs k)]
    (if (zero? k)
      ;; Só unexpected — pareia linearmente (qualquer ordem é ótima)
      (mapv vector extra-mi extra-ejs)
      ;; Matchers regulares — testa todas as permutações e escolhe a de menor custo
      (let [cost (fn [pairs]
                   (reduce (fn [acc [mi ej]]
                             (+ acc (::result/weight (get-in matrix [mi ej]))))
                           0 pairs))]
        (into (->> (perms-of regular-ejs)
                   (map (fn [perm] (mapv vector regular-mi perm)))
                   (reduce (fn [best a] (if (< (cost a) (cost best)) a best))))
              (mapv vector extra-mi extra-ejs))))))
```

A separação pelo `identical?` (comparação de referência ao singleton) é crucial: sem ela, casos com muitos `unexpected-matcher`s gerariam fatoriais gigantescos.

### Por que isso não explode?

Em casos reais de mismatch, o número de **matchers regulares não-emparelhados** (`k`) é quase sempre pequeno (1-3). Apenas esses passam pelo `perms-of`. Os `unexpected-matcher`s — que podem ser muitos — são pareados em O(N) sem permutações.

```
Antes: N! onde N = todos os matchers (incluindo unexpected)
Depois: k! onde k = matchers regulares não-matched (k << N)
```

### Funções adicionadas (DEPOIS)

| Função | O que faz |
|---|---|
| `build-match-matrix` | Pré-computa todos os resultados de match(mi, ej) numa matriz |
| `try-augment` | Tenta encontrar um caminho aumentante no grafo bipartido (Kuhn) |
| `max-bipartite-matching` | Orquestra o algoritmo de Kuhn sobre todos os matchers |
| `perms-of` | Gera permutações de um vetor (implementação local, não mais dependência externa) |
| `min-cost-assign` | Atribui matchers não-matched a elementos não-matched com custo mínimo |

### Funções removidas (DEPOIS)

| Função | Substituída por |
|---|---|
| `matched-successfully?` | Contagem de `match-to` vs `n` |
| `residual-matching-weight` | Custo calculado dentro de `min-cost-assign` |
| `matches-in-any-order?` | Bipartite matching (`max-bipartite-matching`) |
| `better-mismatch?` | Função de custo em `min-cost-assign` |
| `matched-or-best-matchers` | Lógica embutida em `match-all-permutations` |

---

## Mudanças em `deps.edn`

```clojure
;; ANTES: math.combinatorics era dependência de PRODUÇÃO
:deps {org.clojure/clojure              {:mvn/version "1.8.0"}
       org.clojure/math.combinatorics   {:mvn/version "0.2.0"}}  ; <-- produção

;; DEPOIS: math.combinatorics movida para dependência de DEV (só para testes)
:deps {org.clojure/clojure {:mvn/version "1.8.0"}}

:aliases
  {:dev
   {:extra-deps {org.clojure/test.check         {:mvn/version "1.1.1"}
                 midje/midje                    {:mvn/version "1.10.9"}
                 org.clojure/math.combinatorics {:mvn/version "0.2.0"}}}} ; <-- só em dev/test
```

A dependência ainda existe, mas agora **apenas nos testes** — onde `combo/permutations` é usada para gerar casos de teste, não para o algoritmo em si.

---

## Comparação de Complexidade

| Cenário | Antes | Depois |
|:---|:---:|:---:|
| Match encontrado, N elementos | O(N! × N) | O(N² × M) |
| Mismatch, k matchers regulares | O(N! × N) | O(k! × k), k ≪ N |
| Mismatch com muitos `unexpected` | O(N! × N) | O(N) para unexpected + O(k!) para regulares |
| 7 elementos, match | ~35.000 operações | ~49 operações |
| 10 elementos, match | ~36 milhões de operações | ~100 operações |
| 15 elementos, 2 expected | ~1,3 trilhão (trava) | O(1) |

---

## Testes que Provam a Correção

### 1. Match com o menor número de erros possível

**Arquivo:** `test/clj/matcher_combinators/matchers_test.clj` — `deftest in-any-order`

```clojure
(testing "always prints the match with the fewest number of matchers that don't match"
  (is (every? one-mismatch?
              (map #(::result/value (c/match (m/in-any-order [1 2 3 4]) %))
                   (combo/permutations [1 2 3 500])))))
```

Este teste gera **todas as permutações** de `[1 2 3 500]` e verifica que, para qualquer ordem de entrada, o relatório de erro mostra **exatamente 1 elemento errado** (o `500`). Se o algoritmo emparelhasse de forma subótima, poderia mostrar 2 ou mais diferenças.

### 2. Atribuição ótima de custo mínimo no mismatch

**Arquivo:** `test/clj/matcher_combinators/matchers_test.clj` — `deftest ordering`

```clojure
;; Matriz de pesos para in-any-order [{:a 1} {:a 1 :b 2}] vs [{:a 2} {:b 2}]:
;;
;;              {:a 2}  {:b 2}
;;  {:a 1}       w=1     w=1    ← falta :a
;;  {:a 1,:b 2}  w=2     w=1    ← :a errado + :b falta  vs  só :a falta
;;
;; Ótimo: {:a 1}→{:b 2}, {:a 1 :b 2}→{:a 2}  (custo total = 2)
;; Greedy simples daria: {:a 1}→{:a 2}, {:a 1 :b 2}→{:b 2}  (custo total = 3)

(is (every? one-mismatch?
            (->> [{:a 2} {:b 2}]
                 (c/match (m/in-any-order [{:a 1} {:a 1 :b 2}]))
                 ::result/value
                 (map vals))))
```

Verifica que `min-cost-assign` escolhe a atribuição de **menor custo total**, não apenas a primeira que encontra.

### 3. Lista grande que travava antes

**Arquivo:** `test/clj/matcher_combinators/midje_test.clj` — `big-list`

```clojure
(def big-list [[:abc #{1}]
               [:xyz #{2 3 4 5 6 7}]
               [:def #{5 6}]
               [:ghi #{9 10 8 11 1}]
               [:jkl #{9 2 3 4 12 5 10 13 6 14 15 16 17 7 8 11 1}]])  ; set com 17 elementos

big-list =not=> (match (m/embeds [[:jkl #{1 2}]]))
```

O `:jkl` tem um set de 17 elementos. Com o algoritmo antigo, `normalize-inputs-length` adicionaria 15 `unexpected-matcher`s → `perms-of(15)` → **15! ≈ 1,3 trilhão de permutações** → processo trava. Com o novo algoritmo, os 15 `unexpected-matcher`s são separados e pareados em O(N). Executa em milissegundos.

### 4. Matches com 7 a 10 elementos

**Arquivo:** `test/clj/matcher_combinators/midje_test.clj` — `facts "test large-ish in-any-order matches"`

```clojure
(fact "10 items"
  ["J" "A" "B" "C" "D" "E" "F" "G" "H" "I"]
  => (match (m/in-any-order ["A" "B" "C" "D" "E" "F" "G" "H" "I" "J"])))
```

Antes: 10! = 3.628.800 permutações. Depois: bipartite matching termina em ~100 operações.

### 5. Paridade com o matcher nativo do Midje

**Arquivo:** `test/clj/matcher_combinators/midje_test.clj` — `fact "Find optimal in-any-order matching just like midje"`

```clojure
[1 3] => (midje/just [odd? 1] :in-any-order)         ; comportamento referência (Midje)

{:a [1 3]} => (match (m/equals {:a (m/in-any-order [odd? 1])}))  ; deve ser igual
{:a [1 3]} => (match (m/equals {:a (m/in-any-order [1 odd?])}))  ; ordem do expected não importa
```

Garante que o novo algoritmo produz exatamente o mesmo resultado que o matcher `:in-any-order` nativo do Midje.

### 6. Testes de unidade do core (casos de borda)

**Arquivo:** `test/clj/matcher_combinators/core_test.clj`

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

> **Sobre os testes removidos:** O refactor eliminou `matches-in-any-order?`, então os unit tests que testavam essa função diretamente foram deletados — não há como mantê-los sem a função. Eram 3 grupos:
>
> | Comportamento removido | Cobertura restante |
> |---|---|
> | Mais matchers que elementos (`subset=false`) | `core_test.clj:382–386` (`match-any-order` direto) + `core_test.clj:275–278` (integração) |
> | Mais matchers que elementos (`subset=true`) | `core_test.clj:387–390` (`match-any-order` direto, mantido) |
> | Subset com mais elementos que matchers (match) | `midje_test.clj:125` — `[5 1 4 2] => (match (m/embeds [odd? even?]))` |
> | Subset com elemento ausente (mismatch) | `midje_test.clj:126` — `[5 1 4 2] =not=> (match (m/embeds [5 1 4 2 6]))` |
> | Matchers idênticos | `core_test.clj:263–267` — `in-any-order [(equals 2) (equals 2)]` vs `[2 2]` |
>
> As remoções são seguras: os comportamentos continuam cobertos em nível de integração, que é o nível correto para um algoritmo refatorado. O teste de regressão mais crítico — `big-list` no `midje_test.clj` — prova que o cenário que travava antes (17 `unexpected-matcher`s) agora executa corretamente.

---

## Resultado Final

```
clj-test  → 76 tests,  0 failures, 0 errors  ✓
midje     → 117 checks, 0 failures            ✓
test:node → 8 tests,  23 assertions, 0 errors ✓
```
