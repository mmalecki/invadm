(ns invadm.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string])
  (:gen-class))

(def cli-options
  ;; TODO: add clever currency default
  [["-c" "--currency CURRENCY" "Invoice currency"]
   ["-r" "--client CLIENT" "Client"]])

(defn usage [options-summary]
  (->> ["invadm - an invoice manager"
        ""
        "Usage: invadm [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  list    List invoices"
        "  create  Create an invoice"
        "  status  Show overview"]
       (string/join \newline)))

(defn error-msg [errors]
  (str (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn create [options]
  (cond
    (not (:currency options)) (exit 1 (error-msg ["--currency is required"]))
    (not (:client options)) (exit 1 (error-msg ["--client is required"]))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Execute program with options
    (case (first arguments)
      "create" (create options)
      (exit 1 (usage summary)))))
