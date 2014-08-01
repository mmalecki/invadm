(ns invadm.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:gen-class))

(def cli-options
  ;; TODO: add clever currency default
  [["-c" "--currency CURRENCY" "Invoice currency"]
   ["-r" "--client CLIENT" "Client"]
   ["-f" "--filename FILENAME" "Attached file"]
   ["-d" "--issue-date ISSUE_DATE" "Issue data"]
   ["-a" "--amount AMOUNT" "Total amount"
    :parse-fn #(Integer/parseInt %)]])

(defn usage [options-summary]
  (->> ["invadm - an invoice manager"
        ""
        "Usage: invadm [options] action"
        ""
        "  invadm create -c CURRENCY -r CLIENT -a AMOUNT [-d ISSUE_DATE] [-f FILENAME] ID"
        "    Creates an invoice."
        ""
        "  invadm list {-c CURRENCY, -r CLIENT, -f FILENAME}"
        "    Lists invoices."
        ""
        "  invadm data {-c CURRENCY, -r CLIENT, -f FILENAME}"
        "    Dump all the data in a JSON array."]
       (string/join \newline)))

(defn error-msg [errors]
  (str (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn read-json [file]
  (json/read-str (slurp file)))

(defn write-json [file value]
  (with-open [w (io/writer file)]
    (.write w (json/write-str value))))

(defn id-to-filename [id]
  (str id ".json"))

(defn create [options arguments]
  (cond
    (not (:currency options)) (exit 1 (error-msg ["-c CURRENCY is required"]))
    (not (:client options)) (exit 1 (error-msg ["-r CLIENT is required"]))
    (not (:amount options)) (exit 1 (error-msg ["-a AMOUNT is required"]))
    (not (get arguments 1)) (exit 1 (error-msg ["invoice id is required"])))
  (write-json (id-to-filename (get arguments 1)) (assoc options "id" (get arguments 1))))

(defn cwd []
  (System/getProperty "user.dir"))

(defn get-invoice-filenames []
  (filter #(.endsWith % ".json") (map #(.getName %) (file-seq (io/file (cwd))))))

(defn read-all-invoices []
  (map read-json (get-invoice-filenames)))

(defn options-to-filter [options]
  (fn [invoice]
    (every? #(= (get options %) (get invoice (name %))) (keys options))))

(defn pretty-print-invoice [invoice]
  (format (->> ["Invoice #%s"
                "Client: %s"
                "Amount: %d %s\n"]
               (string/join \newline))
          (get invoice "id")
          (get invoice "client")
          (get invoice "amount")
          (get invoice "currency")))

(defn data [options]
  (println (json/write-str (filter (options-to-filter options) (read-all-invoices)))))

(defn list_ [options]
  (apply println (map pretty-print-invoice (filter (options-to-filter options) (read-all-invoices)))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Execute program with options
    (case (first arguments)
      "create" (create options arguments)
      "data" (data options)
      "list" (list_ options)
      (exit 1 (usage summary)))))
