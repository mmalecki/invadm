(ns invadm.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.pprint :as pprint]
            [clojure.string :as string])
  (:gen-class))

(defn cwd []
  (System/getProperty "user.dir"))

(defn sum [v]
  (reduce + 0 v))

(def date-formatter
  (f/formatter "yyyy-MM-dd"))

(defn unparse-date [date]
  (f/unparse date-formatter date))

(defn parse-date [date-string]
  (f/parse date-formatter date-string))

(defn format-amount [amount currency]
  (format "%.2f %s" (float amount) currency))

(defn read-json [file]
  (json/read-str (slurp file)))

(defn write-json [file value]
  (with-open [w (io/writer file)]
    (.write w (json/write-str value))))

(defn id-to-filename [id]
  (str id ".json"))

(defn read-invoice [id]
  (read-json (id-to-filename id)))

(defn write-invoice [id value]
  (write-json (id-to-filename id) value))

(defn get-invoice-filenames []
  (filter #(.endsWith % ".json") (map #(.getName %) (file-seq (io/file (cwd))))))

(defn read-all-invoices []
  (map read-json (get-invoice-filenames)))

(defn sum-payments [invoice]
  (sum (map #(get % "amount") (get invoice "payments"))))

(defn pretty-print-invoice [invoice]
  {"ID" (get invoice "id")
   "From" (get invoice "from")
   "To" (get invoice "to")
   "Issue date" (get invoice "issue-date")
   "Due date" (get invoice "due-date")
   "Amount" (format-amount (get invoice "amount") (get invoice "currency"))
   "Paid" (format-amount (sum-payments invoice)
                         (get invoice "currency"))})

(defn add-convenience-fields [invoice]
  (assoc invoice "paid" (sum-payments invoice)))

(defn options-to-filter [options]
  (fn [invoice]
    (every? #(= (get options %) (get invoice (name %))) (keys options))))

(def cli-options
  ;; TODO: add clever currency default
  [["-c" "--currency CURRENCY" "Invoice currency"]
   [nil "--from FROM" "From"]
   [nil "--to TO" "To"]
   ["-f" "--filename FILENAME" "Attached file"]
   ["-i" "--issue-date ISSUE_DATE" "Issue date"]
   ["-p" "--paid-on PAID_ON" "Paid on"]
   ["-n" "--net NET" "Net"
    :parse-fn #(Integer/parseInt %)]
   ["-a" "--amount AMOUNT" "Total amount"
    :parse-fn #(Integer/parseInt %)]])

(defn parse-today-default [value]
  (cond
    (nil? value) (unparse-date (t/now))
    (string? value) value))

(defn usage [options-summary]
  (->> ["invadm - an invoice manager"
        ""
        "Usage: invadm [options] action"
        ""
        "  invadm create -c CURRENCY --from FROM --to TO -a AMOUNT -n NET [-i ISSUE_DATE] [-f FILENAME] ID"
        "    Creates an invoice."
        ""
        "  invadm list {-c CURRENCY, --from FROM, --to TO, -f FILENAME}"
        "    Lists invoices."
        ""
        "  invadm data {-c CURRENCY, --from FROM, --to TO, -f FILENAME}"
        "    Dump all the data in a JSON array."
        ""
        "  invadm record-payment [-a AMOUNT] [-p PAID_ON] ID"
        "    Record a payment of AMOUNT for invoice ID."]
       (string/join \newline)))

(defn error-msg [errors]
  (str (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn create [options arguments]
  (cond
    (not (:currency options)) (exit 1 (error-msg ["-c CURRENCY is required"]))
    (not (:from options)) (exit 1 (error-msg ["--from FROM is required"]))
    (not (:to options)) (exit 1 (error-msg ["--to TO is required"]))
    (not (:amount options)) (exit 1 (error-msg ["-a AMOUNT is required"]))
    (not (:net options)) (exit 1 (error-msg ["-n NET is required"]))
    (not (get arguments 1)) (exit 1 (error-msg ["invoice id is required"])))
  (write-invoice (get arguments 1)
                 (assoc options
                        "id" (get arguments 1)
                        "payments" [])))

(defn data [options]
  (println (json/write-str (map add-convenience-fields
                                (filter (options-to-filter options)
                                        (read-all-invoices))))))

(defn list_ [options]
  (pprint/print-table (map pretty-print-invoice
                           (filter (options-to-filter options)
                                   (read-all-invoices)))))

(defn record-payment [options arguments]
  (let [id (get arguments 1)
        amount (:amount options)
        invoice (read-invoice id)]
    (write-invoice id (assoc invoice
                             "payments" (conj (get invoice "payments")
                                              {:date (:paid-on options)
                                               :amount (or (:amount options)
                                                           (get invoice "amount"))})))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Execute program with options
    (case (first arguments)
      "create" (create (assoc options
                              :issue-date (parse-today-default (:issue-date options)))
                       arguments)
      "data" (data options)
      "list" (list_ options)
      "record-payment" (record-payment (assoc options
                                              :paid-on (parse-today-default (:paid-on options)))
                                       arguments)
      (exit 1 (usage summary)))))
