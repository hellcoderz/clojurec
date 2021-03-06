;;; -*- clojure -*-

(ns cljc.core.List)

(def EMPTY nil)

(ns cljc.core)

(declare print)
(declare apply)

(def
  ^{:doc "Each runtime environment provides a diffenent way to print output.
  Whatever function *print-fn* is bound to will be passed any
  Strings which should be printed."}
  *print-fn*
  (fn [s]
    (c* "fputs (string_get_utf8 (~{}), stdout)" s)
    nil))

(defn ^boolean not
  "Returns true if x is logical false, false otherwise."
  [x] (if x false true))

(defn make-array [size]
  (c* "make_array (integer_get (~{}))" size))

(defn aget
  "Returns the value at the index."
  ([array i]
     (c* "array_get (~{}, integer_get (~{}))" array i))
  ([array i & idxs]
     (apply aget (aget array i) idxs)))

(defn aset
  "Sets the value at the index."
  [array i val]
  (c* "array_set (~{}, integer_get (~{}), ~{})" array i val)
  nil)

(defn alength
  "Returns the length of the array. Works on arrays of all types."
  [array]
  (c* "make_integer (array_length (~{}))" array))

(comment
(defprotocol IFn
  (-invoke [& args]))
)

(defprotocol ICounted
  (-count [coll] "constant time count"))

(defprotocol IIndexed
  (-nth [coll n] [coll n not-found]))

(defprotocol ASeq)

(defprotocol ISeq
  (-first [coll])
  (-rest [coll]))

(defprotocol INext
  (-next [coll]))

(defprotocol ILookup
  (-lookup [o k] [o k not-found]))

(defprotocol IEquiv
  (-equiv [o other]))

(defprotocol ISeqable
  (-seq [o]))

(defprotocol ISequential
  "Marker interface indicating a persistent collection of sequential items")

(defprotocol ICollection
  (-conj [coll o]))

(defprotocol IReversible
  (-rseq [coll]))

(defprotocol ISet
  (-disjoin [coll v]))

(defprotocol IPrintable
  (-pr-seq [o opts]))

(declare pr-sequential pr-seq list cons inc equiv-sequential)

(deftype Cons [first rest]
  ASeq
  ISeq
  (-first [coll] first)
  (-rest [coll] (if (nil? rest) () rest))

  INext
  (-next [coll] (if (nil? rest) nil (-seq rest)))

  ISequential
  IEquiv
  (-equiv [coll other] (equiv-sequential coll other))

  ISeqable
  (-seq [coll] coll)

  ICollection
  (-conj [coll o] (Cons o coll))

  IPrintable
  (-pr-seq [coll opts] (pr-sequential pr-seq "(" " " ")" opts coll)))

(deftype EmptyList []
  ISeq
  (-first [coll] nil)
  (-rest [coll] ())

  INext
  (-next [coll] nil)

  ISeqable
  (-seq [coll] nil)

  ICollection
  (-conj [coll o] (Cons o nil))

  ISequential
  IEquiv
  (-equiv [coll other] (equiv-sequential coll other))

  ICounted
  (-count [_] 0)

  IPrintable
  (-pr-seq [coll opts] (list "()")))

(set! cljc.core.List/EMPTY (cljc.core/EmptyList))

;;;;;;;;;;;;;;;;;;; fundamentals ;;;;;;;;;;;;;;;
(defn ^boolean identical?
  "Tests if 2 arguments are the same object"
  [x y]
  (cljc.core/identical? x y))

(declare first next)

(defn ^boolean =
  "Equality. Returns true if x equals y, false if not. Compares
  numbers and collections in a type-independent manner.  Clojure's immutable data
  structures define -equiv (and thus =) as a value, not an identity,
  comparison."
  ([x] true)
  ([x y] (or (identical? x y)
             (and (satisfies? IEquiv x)
                  (-equiv x y))))
  ([x y & more]
     (if (= x y)
       (if (next more)
         (recur y (first more) (next more))
         (= y (first more)))
       false)))

(declare reduce)

(defn conj
  "conj[oin]. Returns a new collection with the xs
  'added'. (conj nil item) returns (item).  The 'addition' may
  happen at different 'places' depending on the concrete type."
  ([coll x]
     (-conj coll x))
  ([coll x & xs]
     (if xs
       (recur (conj coll x) (first xs) (next xs))
       (conj coll x))))

(defn ^boolean reversible? [coll]
  (satisfies? IReversible coll))

(defn rseq [coll]
  (-rseq coll))

(defn reverse
  "Returns a seq of the items in coll in reverse order. Not lazy."
  [coll]
  (if (reversible? coll)
    (rseq coll)
    (reduce conj () coll)))

(defn list
  ([] ())
  ([x] (conj () x))
  ([x y] (conj (list y) x))
  ([x y z] (conj (list y z) x))
  ([x y z & items]
     (conj (conj (conj (reduce conj () (reverse items))
                       z) y) x)))

(extend-type Nil
  IEquiv
  (-equiv [_ o] (nil? o))

  ICounted
  (-count [_] 0)

  ICollection
  (-conj [coll o] (list o))

  IIndexed
  (-nth
    ([_ n] nil)
    ([_ n not-found] not-found))

  ILookup
  (-lookup
    ([o k] nil)
    ([o k not-found] not-found))

  ISet
  (-disjoin [_ v] nil)

  IPrintable
  (-pr-seq [o opts] (list "nil")))

(extend-type Integer
  IEquiv
  (-equiv [i o]
    (and (has-type? o Integer)
         (c* "make_boolean (integer_get (~{}) == integer_get (~{}))" i o)))

  IPrintable
  (-pr-seq [i opts] (list (c* "make_string_copy_free (g_strdup_printf (\"%ld\", integer_get (~{})))" i))))

(extend-type Boolean
  IPrintable
  (-pr-seq [o opts] (list (if o "true" "false"))))

(deftype IndexedSeq [a i]
  ISeqable
  (-seq [this] this)

  ASeq
  ISeq
  (-first [_] (-nth a i))
  (-rest [_] (if (< (inc i) (-count a))
               (IndexedSeq a (inc i))
               (list)))

  INext
  (-next [_] (if (< (inc i) (-count a))
               (IndexedSeq a (inc i))
               nil))

  ICounted
  (-count [_] (- (-count a) i))

  ISequential
  IEquiv
  (-equiv [coll other] (equiv-sequential coll other))

  ICollection
  (-conj [coll o] (cons o coll))

  IPrintable
  (-pr-seq [coll opts] (pr-sequential pr-seq "(" " " ")" opts coll)))


(defn prim-seq
  ([prim]
     (prim-seq prim 0))
  ([prim i]
     (when-not (zero? (-count prim))
       (IndexedSeq prim i))))

(defn array-seq
  ([array]
     (prim-seq array 0))
  ([array i]
     (prim-seq array i)))

(extend-type Array
  ISeqable
  (-seq [array] (array-seq array 0))

  ICounted
  (-count [a] (alength a))

  IIndexed
  (-nth
    ([coll n]
       (aget coll n))
    ([coll n not-found]
       (if (and (<= 0 n) (< n (alength coll)))
         (aget coll n)
         not-found)))

  IPrintable
  (-pr-seq [a opts]
    (pr-sequential pr-seq "[" " " "]" opts a)))

(extend-type Character
  IEquiv
  (-equiv [c o]
    (and (has-type? o Character)
         (c* "make_boolean (character_get (~{}) == character_get (~{}))" c o)))

  IPrintable
  (-pr-seq [c opts]
    (list "\\" (c* "make_string_from_unichar (character_get (~{}))" c))))

(extend-type String
  IEquiv
  (-equiv [s o]
    (and (has-type? o String)
         ;; FIXME: normalize first
         (c* "make_boolean (strcmp (string_get_utf8 (~{}), string_get_utf8 (~{})) == 0)" s o)))

  ISeqable
  (-seq [string] (prim-seq string 0))

  ICounted
  ;; FIXME: cache the count!
  (-count [s] (c* "make_integer (g_utf8_strlen (string_get_utf8 (~{}), -1))" s))

  IIndexed
  (-nth
    ([coll n]
       (c* "make_character (g_utf8_get_char (g_utf8_offset_to_pointer (string_get_utf8 (~{}), integer_get (~{}))))"
           coll n))
    ([coll n not-found]
       (if (and (<= 0 n) (< n (count coll)))
         (-nth coll n)
         not-found)))

  IPrintable
  (-pr-seq [s opts]
    (list s)))

(defn seq
  "Returns a seq on the collection. If the collection is
  empty, returns nil.  (seq nil) returns nil. seq also works on
  Strings."
  [coll]
  (if-not (nil? coll)
    (if (satisfies? ASeq coll)
      coll
      (-seq coll))))

(defn first
  "Returns the first item in the collection. Calls seq on its
  argument. If coll is nil, returns nil."
  [coll]
  (when-not (nil? coll)
    (if (satisfies? ISeq coll)
      (-first coll)
      (let [s (seq coll)]
        (when-not (nil? s)
          (-first s))))))

(defn ^seq rest
  "Returns a possibly empty seq of the items after the first. Calls seq on its
  argument."
  [coll]
  (if-not (nil? coll)
    (if (satisfies? ISeq coll)
      (-rest coll)
      (let [s (seq coll)]
        (if-not (nil? s)
          (-rest s)
          ())))
    ()))

(defn ^seq next
  "Returns a seq of the items after the first. Calls seq on its
  argument.  If there are no more items, returns nil"
  [coll]
  (when-not (nil? coll)
    (if (satisfies? INext coll)
      (-next coll)
      (seq (rest coll)))))

(defn +
  "Returns the sum of nums. (+) returns 0."
  ([] 0)
  ([x] x)
  ([x y] (cljc.core/+ x y))
  ([x y & more] (reduce + (cljc.core/+ x y) more)))

(defn -
  "If no ys are supplied, returns the negation of x, else subtracts
  the ys from x and returns the result."
  ([x] (cljc.core/- x))
  ([x y] (cljc.core/- x y))
  ([x y & more] (reduce - (cljc.core/- x y) more)))

(defn *
  "Returns the product of nums. (*) returns 1."
  ([] 1)
  ([x] x)
  ([x y] (cljc.core/* x y))
  ([x y & more] (reduce * (cljc.core/* x y) more)))

(defn ^boolean <
  "Returns non-nil if nums are in monotonically increasing order,
  otherwise false."
  ([x] true)
  ([x y] (cljc.core/< x y))
  ([x y & more]
     (if (cljc.core/< x y)
       (if (next more)
         (recur y (first more) (next more))
         (cljc.core/< y (first more)))
       false)))

(defn ^boolean <=
  "Returns non-nil if nums are in monotonically non-decreasing order,
  otherwise false."
  ([x] true)
  ([x y] (cljc.core/<= x y))
  ([x y & more]
   (if (cljc.core/<= x y)
     (if (next more)
       (recur y (first more) (next more))
       (cljc.core/<= y (first more)))
     false)))

(defn ^boolean >
  "Returns non-nil if nums are in monotonically decreasing order,
  otherwise false."
  ([x] true)
  ([x y] (cljc.core/> x y))
  ([x y & more]
   (if (cljc.core/> x y)
     (if (next more)
       (recur y (first more) (next more))
       (cljc.core/> y (first more)))
     false)))

(defn ^boolean >=
  "Returns non-nil if nums are in monotonically non-increasing order,
  otherwise false."
  ([x] true)
  ([x y] (cljc.core/>= x y))
  ([x y & more]
   (if (cljc.core/>= x y)
     (if (next more)
       (recur y (first more) (next more))
       (cljc.core/>= y (first more)))
     false)))

(defn inc
  "Returns a number one greater than num."
  [x] (cljc.core/+ x 1))

(defn dec
  "Returns a number one less than num."
  [x] (- x 1))

(defn ^boolean ==
  "Returns non-nil if nums all have the equivalent
  value, otherwise false. Behavior on non nums is
  undefined."
  ([x] true)
  ([x y] (-equiv x y))
  ([x y & more]
   (if (== x y)
     (if (next more)
       (recur y (first more) (next more))
       (== y (first more)))
     false)))

;;;;;;;;;;;;;;;; preds ;;;;;;;;;;;;;;;;;;

(def ^:private lookup-sentinel (c* "alloc_value (PTABLE_NAME (cljc_DOT_core_SLASH_Nil), sizeof (value_t))"))

(defn ^boolean boolean [x]
  (if x true false))

(defn ^boolean contains?
  "Returns true if key is present in the given collection, otherwise
  returns false.  Note that for numerically indexed collections like
  vectors and arrays, this tests if the numeric key is within the
  range of indexes. 'contains?' operates constant or logarithmic time;
  it will not perform a linear search for a value.  See also 'some'."
  [coll v]
  (if (identical? (-lookup coll v lookup-sentinel) lookup-sentinel)
    false
    true))

(defn ^boolean empty?
  "Returns true if coll has no items - same as (not (seq coll)).
  Please use the idiom (seq x) rather than (not (empty? x))"
  [coll] (not (seq coll)))

(defn ^boolean set?
  "Returns true if x satisfies ISet"
  [x]
  (if (nil? x)
    false
    (satisfies? ISet x)))

(defn ^boolean sequential?
  "Returns true if coll satisfies ISequential"
  [x] (satisfies? ISequential x))

(defn ^boolean counted?
  "Returns true if coll implements count in constant time"
  [x] (satisfies? ICounted x))

(defn- accumulating-seq-count [coll]
  (loop [s (seq coll) acc 0]
    (if (counted? s) ; assumes nil is counted, which it currently is
      (+ acc (-count s))
      (recur (next s) (inc acc)))))

(defn count
  "Returns the number of items in the collection. (count nil) returns
  0.  Also works on strings, arrays, and Maps"
  [coll]
  (if (counted? coll)
    (-count coll)
    (accumulating-seq-count coll)))

(defn cons
  "Returns a new seq where x is the first element and seq is the rest."
  [x coll]
  (if (or (nil? coll)
          (satisfies? ISeq coll))
    (Cons x coll)
    (Cons x (seq coll))))

(defn get
  "Returns the value mapped to key, not-found or nil if key not present."
  ([o k]
     (-lookup o k))
  ([o k not-found]
     (-lookup o k not-found)))

(defn disj
  "disj[oin]. Returns a new set of the same (hashed/sorted) type, that
  does not contain key(s)."
  ([coll] coll)
  ([coll k]
     (-disjoin coll k))
  ([coll k & ks]
     (let [ret (disj coll k)]
       (if ks
         (recur ret (first ks) (next ks))
         ret))))

(defn- equiv-sequential
  "Assumes x is sequential. Returns true if x equals y, otherwise
  returns false."
  [x y]
  (boolean
   (when (sequential? y)
     (loop [xs (seq x) ys (seq y)]
       (cond (nil? xs) (nil? ys)
             (nil? ys) false
             (= (first xs) (first ys)) (recur (next xs) (next ys))
             true false)))))

(defn ^boolean every?
  "Returns true if (pred x) is logical true for every x in coll, else
  false."
  [pred coll]
  (cond
   (nil? (seq coll)) true
   (pred (first coll)) (recur pred (next coll))
   true false))

(defn identity [x] x)

(defn filter
  "Returns a lazy sequence of the items in coll for which
  (pred item) returns true. pred must be free of side-effects."
  ([pred coll]
     (loop [rev ()
            coll (seq coll)]
       (if coll
         (let [f (first coll) r (next coll)]
           (if (pred f)
             (recur (cons f rev) r)
             (recur rev r)))
         (reverse rev)))))

(defn flatten-tail
  [coll]
  (if-let [n (next coll)]
    (cons (first coll) (flatten-tail n))
    (first coll)))

; simple reduce based on seqs, used as default
(defn- seq-reduce
  ([f coll]
    (if-let [s (seq coll)]
      (reduce f (first s) (next s))
      (f)))
  ([f val coll]
     (loop [val val, coll (seq coll)]
       (if coll
         (let [nval (f val (first coll))]
           (recur nval (next coll)))
         val))))

(defn reduce
  "f should be a function of 2 arguments. If val is not supplied,
  returns the result of applying f to the first 2 items in coll, then
  applying f to that result and the 3rd item, etc. If coll contains no
  items, f must accept no arguments as well, and reduce returns the
  result of calling f with no arguments.  If coll has only 1 item, it
  is returned and f is not called.  If val is supplied, returns the
  result of applying f to val and the first item in coll, then
  applying f to that result and the 2nd item, etc. If coll contains no
  items, returns val and f is not called."
  ([f coll]
     (seq-reduce f coll))
  ([f val coll]
     (seq-reduce f val coll)))

(defn concat
  "Returns a lazy seq representing the concatenation of the elements in the supplied colls."
  ([] nil)
  ([x] (list x))
  ([x y]
     (let [s (seq x)]
       (if s
         (cons (first s) (concat (rest s) y))
         y)))
  ([x y & zs]
     (let [cat (fn cat [xys zs]
                 (let [xys (seq xys)]
                   (if xys
                     (cons (first xys) (cat (rest xys) zs))
                     (when zs
                       (cat (first zs) (next zs))))))]
       (cat (concat x y) zs))))

(defn map
  "Returns a lazy sequence consisting of the result of applying f to the
  set of first items of each coll, followed by applying f to the set
  of second items in each coll, until any one of the colls is
  exhausted.  Any remaining items in other colls are ignored. Function
  f should accept number-of-colls arguments."
  ([f coll]
     (when-let [s (seq coll)]
       (cons (f (first s)) (map f (rest s)))))
  ([f c1 c2]
     (let [s1 (seq c1) s2 (seq c2)]
       (when (and s1 s2)
         (cons (f (first s1) (first s2))
               (map f (rest s1) (rest s2))))))
  ([f c1 c2 c3]
     (let [s1 (seq c1) s2 (seq c2) s3 (seq c3)]
       (when (and  s1 s2 s3)
         (cons (f (first s1) (first s2) (first s3))
               (map f (rest s1) (rest s2) (rest s3))))))
  ([f c1 c2 c3 & colls]
   (let [step (fn step [cs]
                (let [ss (map seq cs)]
                  (when (every? identity ss)
                    (cons (map first ss) (step (map rest ss))))))]
     (map #(apply f %) (step (conj colls c3 c2 c1))))))

(defn interpose
  "Returns a lazy seq of the elements of coll separated by sep"
  [sep coll]
  (if (seq coll)
    (if-let [n (next coll)]
      (cons (first coll) (cons sep (interpose sep n)))
      (list (first coll)))
    ()))

(defn- flatten1
  "Take a collection of collections, and return a lazy seq
  of items from the inner collection"
  [colls]
  (let [cat (fn cat [coll colls]
              (if-let [coll (seq coll)]
                (cons (first coll) (cat (rest coll) colls))
                (when (seq colls)
                  (cat (first colls) (rest colls)))))]
    (cat nil colls)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Sets ;;;;;;;;;;;;;;;;

(defn- member? [coll o]
  (not (every? #(not (= % o)) coll)))

(defn- reverse-rember [coll o]
  (loop [rev ()
         coll (seq coll)]
    (if coll
      (let [f (first coll)]
        (if (= o f)
          (recur rev (next coll))
          (recur (cons f rev) (next coll))))
      rev)))

(deftype ListSet [elems]
  ICollection
  (-conj [coll o]
    (if (member? elems o)
      coll
      (ListSet (conj elems o))))

  IEquiv
  (-equiv [coll other]
    (and
     (set? other)
     (== (count coll) (count other))
     (every? #(contains? coll %)
             other)))

  ISeqable
  (-seq [_] elems)

  ICounted
  (-count [coll] (count (seq coll)))

  ILookup
  (-lookup [coll v]
    (-lookup coll v nil))
  (-lookup [coll v not-found]
    (if (member? elems v)
      v
      not-found))

  ISet
  (-disjoin [coll v]
    (if (member? elems v)
      (ListSet (reverse-rember elems v))
      coll))

  IFn
  (-invoke [coll k]
    (-lookup coll k))
  (-invoke [coll k not-found]
    (-lookup coll k not-found))

  IPrintable
  (-pr-seq [coll opts] (pr-sequential pr-seq "#{" " " "}" opts elems)))

(defn set
  "Returns a set of the distinct elements of coll."
  [coll]
  (loop [in (seq coll)
         out (ListSet ())]
    (if (seq in)
      (recur (next in) (conj out (first in)))
      out)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Strings ;;;;;;;;;;;;;;;;

(declare split-string-seq-next-fn)

(deftype SplitStringSeq [string len char first offset]
  ASeq
  ISeq
  (-first [coll] first)
  (-rest [coll]
    (or (split-string-seq-next-fn string len char offset) ()))

  INext
  (-next [coll]
    (split-string-seq-next-fn offset))

  ISequential
  IEquiv
  (-equiv [coll other] (equiv-sequential coll other))

  ISeqable
  (-seq [coll] coll)

  ICollection
  (-conj [coll o] (Cons o coll))

  IPrintable
  (-pr-seq [coll opts]
    (pr-sequential pr-seq "(" " " ")" opts coll)))

(defn- split-string-seq-next-fn [string len char offset]
  (when-not (== offset len)
    (let [next-offset (c* "make_integer (strchr_offset (string_get_utf8 (~{}) + integer_get (~{}), character_get (~{})))"
			  string offset char)]
      (if (>= next-offset 0)
	(SplitStringSeq string len char
			(c* "make_string_copy_free (g_strndup (string_get_utf8 (~{}) + integer_get (~{}), integer_get (~{})))"
			    string offset next-offset)
			(c* "make_integer (g_utf8_next_char (string_get_utf8 (~{}) + integer_get (~{})) - string_get_utf8 (~{}))"
			    string (+ offset next-offset) string))
	(SplitStringSeq string len char
			(c* "make_string_copy_free (g_strdup (string_get_utf8 (~{}) + integer_get (~{})))" string offset)
			len)))))

(defn split-string-seq [string char]
  (split-string-seq-next-fn string
			    (c* "make_integer (strlen (string_get_utf8 (~{})))" string)
			    char
			    0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Printing ;;;;;;;;;;;;;;;;

(defn pr-sequential [print-one begin sep end opts coll]
  (concat (list begin)
          (flatten1
            (interpose (list sep) (map #(print-one % opts) coll)))
          (list end)))

(defn string-print [x]
  (*print-fn* x)
  nil)

(defn str [x]
  "?")

(defn- pr-seq [obj opts]
  (if (satisfies? IPrintable obj)
    (-pr-seq obj opts)
    (list "#<" (str obj) ">")))

(defn pr-with-opts
  "Prints a sequence of objects using string-print, observing all
  the options given in opts"
  [objs opts]
  (loop [objs (seq objs)
         need-sep false]
    (when objs
      (when need-sep
        (string-print " "))
      (doseq [string (pr-seq (first objs) opts)]
        (string-print string))
      (recur (next objs) true))))

(defn- pr-opts []
  nil)

(defn pr
  "Prints the object(s) using string-print.  Prints the
  object(s), separated by spaces if there is more than one.
  By default, pr and prn print in a way that objects can be
  read by the reader"
  [& objs]
  (pr-with-opts objs (pr-opts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; I/O ;;;;;;;;;;;;;;;;

(defn slurp [filename]
  (c* "make_string_copy_free (slurp_file (string_get_utf8 (~{})))" filename))
