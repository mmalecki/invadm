(ns invadm.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
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

(defn compare-invoices-by-issue-date [a b]
  (compare (c/to-long (get a "issue-date"))
           (c/to-long (get b "issue-date"))))

(defn format-amount [amount currency]
  (format "%.2f %s" (float amount) currency))

(defn read-json [file]
  (json/read-str (slurp file)))

(defn write-json [file value]
  (with-open [w (io/writer file)]
    (.write w (json/write-str value))))

(defn id-to-filename [id]
  (str id ".json"))

(defn sum-payments [invoice]
  (sum (map #(get % "amount") (get invoice "payments"))))

(defn sum-key-by-currency [invoices amount-key]
  (reduce (fn [totals invoice]
            (let [invoice-currency (get invoice "currency")]
              (assoc totals
                     invoice-currency (+ (or (get totals invoice-currency) 0)
                                         (get invoice amount-key)))))
          {}
          invoices))

(defn sum-amount-by-currency [invoices]
  (sum-key-by-currency invoices "amount"))

(defn sum-paid-by-currency [invoices]
  (sum-key-by-currency invoices "paid"))

(defn add-convenience-fields [invoice]
  (assoc invoice
         "paid" (sum-payments invoice)
         "due-date" (t/plus (get invoice "issue-date") (t/days (get invoice "net")))))

(defn remove-convenience-fields [invoice]
  (dissoc invoice "paid" "due-date"))

(defn serialize-payment [payment]
  (assoc payment
        "date" (unparse-date (get payment "date"))))

(defn serialize-invoice [invoice]
  (assoc invoice
         "issue-date" (unparse-date (get invoice "issue-date"))
         "payments" (map serialize-payment (get invoice "payments"))))

(defn parse-invoice [json]
  (add-convenience-fields (assoc json
                                 "issue-date" (parse-date (get json "issue-date")))))

(defn unparse-invoice [invoice]
  (serialize-invoice (remove-convenience-fields invoice)))

(defn read-invoice [id]
  (parse-invoice (read-json (id-to-filename id))))

(defn write-invoice [id invoice]
  (write-json (id-to-filename id) (unparse-invoice invoice)))

(defn get-invoice-filenames []
  (filter #(.endsWith % ".json") (map #(.getName %) (file-seq (io/file (cwd))))))

(defn read-all-invoices []
  (map parse-invoice (map read-json (get-invoice-filenames))))

(defn pretty-print-invoice [invoice]
  {"ID" (get invoice "id")
   "From" (get invoice "from")
   "To" (get invoice "to")
   "Issue date" (unparse-date (get invoice "issue-date"))
   "Due date" (unparse-date (get invoice "due-date"))
   "Amount" (format-amount (get invoice "amount") (get invoice "currency"))
   "Paid" (format-amount (sum-payments invoice)
                         (get invoice "currency"))})

(defn print-by-currency [sum]
  (doseq [keyval sum]
    (println (str "  " (format-amount (val keyval) (key keyval))))))

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
    :parse-fn #(Float/parseFloat %)]])

(defn parse-today-default [value]
  (cond
    (nil? value) (t/now)
    (string? value) (parse-date value)))

(defn usage [options-summary]
  (->> ["invadm - an invoice manager"
        ""
        "Usage: invadm [options] action"
        ""
        "  invadm create -c CURRENCY --from FROM --to TO -a AMOUNT -n NET [-i ISSUE_DATE] [-f FILENAME] ID"
        "    Create an invoice."
        ""
        "  invadm list {-c CURRENCY, --from FROM, --to TO, -f FILENAME}"
        "    List invoices, filtered according to arguments."
        ""
        "  invadm data {-c CURRENCY, --from FROM, --to TO, -f FILENAME}"
        "    Dump all the data in a JSON array, filtered according to arguments."
        ""
        "  invadm record-payment [-a AMOUNT] [-p PAID_ON] ID"
        "    Record a payment of AMOUNT for invoice ID, paid on PAID_ON if given."
        ""
        "All dates should be formatted like YYYY-MM-DD."]
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
                 {"id" (get arguments 1)
                  "to" (:to options)
                  "from" (:from options)
                  "amount" (:amount options)
                  "currency" (:currency options)
                  "issue-date" (:issue-date options)
                  "filename" (:filename options)
                  "net" (:net options)
                  "payments" []}))

(defn data [options]
  (println (json/write-str (map serialize-invoice
                                (map remove-convenience-fields ;; TODO: not have to do that
                                     (sort compare-invoices-by-issue-date
                                           (filter (options-to-filter options)
                                                   (read-all-invoices))))))))

(defn list_ [options]
  (let [invoices (sort compare-invoices-by-issue-date
                       (filter (options-to-filter options)
                               (read-all-invoices)))]
    (pprint/print-table (map pretty-print-invoice invoices))
    (println "\nTotal:")
    (print-by-currency (sum-amount-by-currency invoices))
    (println "\nPaid:")
    (print-by-currency (sum-paid-by-currency invoices))))


(defn record-payment [options arguments]
  (let [id (get arguments 1)
        amount (:amount options)
        invoice (read-invoice id)]
    (write-invoice id (assoc invoice
                             "payments" (conj (get invoice "payments")
                                              {"date" (:paid-on options)
                                               "amount" (or (:amount options)
                                                           (get invoice "amount"))})))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options)]
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
