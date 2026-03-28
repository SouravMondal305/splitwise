package org.example.Expense;

/**
 * Enum to distinguish between GROUP and DIRECT expenses
 * - GROUP: Expense in a group context (has group_id)
 * - DIRECT: Peer-to-peer expense between individuals (group_id is NULL)
 */
public enum ExpenseType {
    GROUP,   // Expense in a group context (has group_id)
    DIRECT   // Peer-to-peer expense (group_id is NULL)
}
