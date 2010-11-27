(ns midje.midje-forms.translating
  (:use [midje.util thread-safe-var-nesting wrapping form-utils laziness])
  (:use midje.sweet.metaconstants)
  (:require [midje.sweet.sweet-to-semi-sweet-rewrite :as transform])
  (:use [midje.midje-forms building recognizing])
  (:use [midje.util.debugging]))

(defn canonicalize-background-forms [forms]
  (loop [expanded []
	 in-progress forms]
    (cond (empty? in-progress)
	  expanded

	  (is-arrow-form? in-progress)
	  (let [content (transform/one-fake-body-content in-progress)]
	    (recur (conj expanded (-> content transform/make-fake make-background))
		   (nthnext in-progress (count content))))

	  (seq-headed-by-setup-teardown-form? in-progress)
	  (recur (conj expanded (first in-progress))
		 (rest in-progress))
	  
	  :else
	  (throw (Error. (str "This doesn't look like part of a background:" in-progress))))))

(declare midjcoexpand)

(defn background-fake-wrapper [raw-wrappers]
  (define-metaconstants raw-wrappers)
  (let [background (canonicalize-background-forms raw-wrappers)]
    `[ (with-pushed-namespace-values :midje/background-fakes ~background ~(?form)) ]))

(defn replace-wrappers [raw-wrappers]
  (set-namespace-value :midje/wrappers (list (background-fake-wrapper raw-wrappers))))

(defmacro with-additional-wrappers [raw-wrappers form]
  `(with-pushed-namespace-values :midje/wrappers (background-fake-wrapper ~raw-wrappers)
    (midjcoexpand ~form)))

(defn gather-wrappers [form]
;  (println "Wrappers: " (second form))
  (second form)
  )

(defn without-wrapper-providers [form]
  `(do ~@(rest (rest form)))
  )

(defn my-into [empty-container contents]
  (if (vector? empty-container)
    (vec contents)
    contents))

(defn midjcoexpand [form]
;   (println "== midjcoexpanding" form)
;   (println "== with" (namespace-values-inside-out :midje/wrappers))
  (nopret (cond (already-wrapped? form)
	form

	(form-first? form "quote")
	form

	(wrappable? form)
	(multiwrap form (namespace-values-inside-out :midje/wrappers))

	(expansion-has-wrappables? form)
	(midjcoexpand (macroexpand form))

	(provides-wrappers? form)
	(do
;;	  (println "use these wrappers" (gather-wrappers form))
;;	  (println "for this form" (without-wrapper-providers form))
	  (with-additional-wrappers (gather-wrappers form)
	    (midjcoexpand (without-wrapper-providers form))))
	
	(sequential? form)
	(my-into (empty form)
		 (eagerly (map midjcoexpand form)))

	:else
	form)))
