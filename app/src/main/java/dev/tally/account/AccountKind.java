package dev.tally.account;

public enum AccountKind {
    USER,
    /** Source of all money in the ledger; the only account allowed a negative balance. */
    TREASURY
}
