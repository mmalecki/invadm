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
   ["-a" "--amount AMOUNT" "Total amount"]])

(defn usage [options-summary]
  (->> ["invadm - an invoice manager"
        ""
        "Usage: invadm [options] action"
        ""
        "  invadm create -c CURRENCY -r CLIENT -a AMOUNT [-f FILENAME] ID"
        "    Creates an invoice."
        ""
        "  invadm list {-c CURRENCY, -r CLIENT, -f FILENAME}"
        "    Lists invoices."
        ""
        "  invadm data"
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
  (write-json (id-to-filename (get arguments 1)) options))

(defn cwd []
  (System/getProperty "user.dir"))

(defn is-json? [filename]
  (.endsWith filename ".json"))

(defn get-io-file-name [io-file]
  (.getName io-file))

(defn get-invoice-filenames []
  (filter is-json? (map get-io-file-name (file-seq (io/file (cwd))))))

(defn read-all-invoices []
  (map read-json (get-invoice-filenames)))

(defn data []
  (println (json/write-str (read-all-invoices))))

(defn options-to-filter [options]
  (fn [invoice]
    (every? (fn[key_](= (get options key_) (get invoice (name key_)))) (keys options))))

(defn list_ [options]
  (println (json/write-str (filter (options-to-filter options) (read-all-invoices)))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Execute program with options
    (case (first arguments)
      "create" (create options arguments)
      "data" (data)
      "list" (list_ options)
      (exit 1 (usage summary)))))
