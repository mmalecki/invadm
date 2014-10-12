# invadm
Manage invoices from command line.

**Disclaimer**: this tool is experimental. Keep its repository in git.

## Installation
`invadm` depends on `lein` and Java being installed.

```sh
npm install -g invadm
```

## Usage
```
invadm - an invoice manager

Usage: invadm [options] action

  invadm create -c CURRENCY --from FROM --to TO -a AMOUNT -n NET [-i ISSUE_DATE] [-f FILENAME] ID
    Create an invoice.

  invadm list {-c CURRENCY, --from FROM, --to TO, -f FILENAME}
    List invoices, filtered according to arguments.

  invadm data {-c CURRENCY, --from FROM, --to TO, -f FILENAME}
    Dump all the data in a JSON array, filtered according to arguments.

  invadm record-payment [-a AMOUNT] [-p PAID_ON] ID
    Record a payment of AMOUNT for invoice ID, paid on PAID_ON if given.

All dates should be formatted like YYYY-MM-DD.
```
