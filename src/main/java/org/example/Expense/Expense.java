package org.example.Expense;



import org.example.Split.Split;
import org.example.User.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Expense entity can represent two types:
 * 1. GROUP expense: Has groupId, paid in a group context
 * 2. DIRECT expense: groupId is null, direct settlement between users
 */
public class Expense {
    String expenseId;
    String description;
    public double expenseAmount;
    public User paidByUser;
    String groupId;                    // NULL for direct expenses
    ExpenseType expenseType;           // GROUP or DIRECT
    ExpenseSplitType splitType;
    public List<Split> splitDetails = new ArrayList<>();

    /**
     * Constructor for GROUP expenses (original)
     */
    public Expense(String expenseId, double expenseAmount, String description,
                   User paidByUser, ExpenseSplitType splitType, List<Split> splitDetails) {
        this(expenseId, expenseAmount, description, paidByUser, null, 
             ExpenseType.GROUP, splitType, splitDetails);
    }

    /**
     * Constructor for both GROUP and DIRECT expenses
     */
    public Expense(String expenseId, double expenseAmount, String description,
                   User paidByUser, String groupId, ExpenseType expenseType,
                   ExpenseSplitType splitType, List<Split> splitDetails) {

        this.expenseId = expenseId;
        this.expenseAmount = expenseAmount;
        this.description = description;
        this.paidByUser = paidByUser;
        this.groupId = groupId;
        this.expenseType = expenseType;
        this.splitType = splitType;
        this.splitDetails.addAll(splitDetails);
    }

    // Getters
    public String getExpenseId() {
        return expenseId;
    }

    public String getDescription() {
        return description;
    }

    public double getExpenseAmount() {
        return expenseAmount;
    }

    public User getPaidByUser() {
        return paidByUser;
    }

    public String getGroupId() {
        return groupId;
    }

    public ExpenseType getExpenseType() {
        return expenseType;
    }

    public ExpenseSplitType getSplitType() {
        return splitType;
    }

    public List<Split> getSplitDetails() {
        return splitDetails;
    }

    /**
     * Check if this is a direct peer-to-peer expense
     */
    public boolean isDirectExpense() {
        return groupId == null && expenseType == ExpenseType.DIRECT;
    }

    /**
     * Check if this is a group expense
     */
    public boolean isGroupExpense() {
        return groupId != null && expenseType == ExpenseType.GROUP;
    }

    @Override
    public String toString() {
        if (isDirectExpense()) {
            return String.format("Direct Expense: %s paid ₹%.2f to %d people",
                    paidByUser.getUserName(), expenseAmount, splitDetails.size());
        } else {
            return String.format("Group Expense (%s): %s paid ₹%.2f in group %s",
                    expenseType, paidByUser.getUserName(), expenseAmount, groupId);
        }
    }
}

