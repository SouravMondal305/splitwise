package org.example.Repository;

import org.example.Expense.Expense;
import org.example.Expense.ExpenseType;
import org.example.Split.Split;
import org.example.User.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ExpenseRepository - Data Access Object for Expense entity
 * Handles all database operations for Expenses and Expense_Splits tables
 * 
 * Supports two types of expenses:
 * 1. GROUP expenses: groupId NOT NULL
 * 2. DIRECT expenses: groupId IS NULL (peer-to-peer)
 * 
 * Flow: Expense object → Repository → Database (SAVE)
 *       Database → Repository → Expense object (FETCH)
 */
public class ExpenseRepository {
    
    /**
     * Save a group expense with all its splits to database
     * 
     * Flow:
     * 1. Insert expense record in EXPENSES table (group_id NOT NULL)
     * 2. Insert each split in EXPENSE_SPLITS table
     * 3. Transaction ensures atomicity (all or nothing)
     */
    public void saveGroupExpense(Expense expense, String groupId) {
        String expenseSql = "INSERT INTO expenses (expense_id, group_id, paid_by_user_id, " +
                           "description, amount, expense_type, split_type) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String splitSql = "INSERT INTO expense_splits (expense_id, user_id, amount_owed) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseConfig.getConnection()) {
            // Start transaction
            conn.setAutoCommit(false);
            
            // Step 1: Insert expense
            try (PreparedStatement expenseStmt = conn.prepareStatement(expenseSql)) {
                expenseStmt.setString(1, expense.getExpenseId());
                expenseStmt.setString(2, groupId);  // GROUP expense
                expenseStmt.setString(3, expense.getPaidByUser().getUserId());
                expenseStmt.setString(4, expense.getDescription());
                expenseStmt.setDouble(5, expense.getExpenseAmount());
                expenseStmt.setString(6, ExpenseType.GROUP.toString());
                expenseStmt.setString(7, expense.getSplitType().toString());
                
                expenseStmt.executeUpdate();
                // Group expense saved silently
            }
            
            // Step 2: Insert splits
            try (PreparedStatement splitStmt = conn.prepareStatement(splitSql)) {
                for (Split split : expense.getSplitDetails()) {
                    splitStmt.setString(1, expense.getExpenseId());
                    splitStmt.setString(2, split.getUser().getUserId());
                    splitStmt.setDouble(3, split.getAmountOwe());
                    splitStmt.addBatch();
                }
                splitStmt.executeBatch();
                // Splits saved silently
            }
            
            // Commit transaction
            conn.commit();
            conn.setAutoCommit(true);
            
        } catch (SQLException e) {
            System.err.println("❌ Error saving group expense: " + e.getMessage());
            throw new RuntimeException("Failed to save group expense", e);
        }
    }
    
    /**
     * Save a direct peer-to-peer expense
     * 
     * Flow:
     * 1. Insert expense with groupId = NULL
     * 2. Insert splits (typically 2 people)
     * 3. Transaction ensures atomicity
     */
    public void saveDirectExpense(Expense expense) {
        String expenseSql = "INSERT INTO expenses (expense_id, group_id, paid_by_user_id, " +
                           "description, amount, expense_type, split_type) VALUES (?, NULL, ?, ?, ?, ?, ?)";
        String splitSql = "INSERT INTO expense_splits (expense_id, user_id, amount_owed) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseConfig.getConnection()) {
            // Start transaction
            conn.setAutoCommit(false);
            
            // Step 1: Insert direct expense (groupId = NULL)
            try (PreparedStatement expenseStmt = conn.prepareStatement(expenseSql)) {
                expenseStmt.setString(1, expense.getExpenseId());
                // NULL for group_id (handled by ? NULL)
                expenseStmt.setString(2, expense.getPaidByUser().getUserId());
                expenseStmt.setString(3, expense.getDescription());
                expenseStmt.setDouble(4, expense.getExpenseAmount());
                expenseStmt.setString(5, ExpenseType.DIRECT.toString());
                expenseStmt.setString(6, expense.getSplitType().toString());
                
                expenseStmt.executeUpdate();
                // Direct expense saved silently
            }
            
            // Step 2: Insert splits
            try (PreparedStatement splitStmt = conn.prepareStatement(splitSql)) {
                for (Split split : expense.getSplitDetails()) {
                    splitStmt.setString(1, expense.getExpenseId());
                    splitStmt.setString(2, split.getUser().getUserId());
                    splitStmt.setDouble(3, split.getAmountOwe());
                    splitStmt.addBatch();
                }
                splitStmt.executeBatch();
                // Direct splits saved silently
            }
            
            // Commit transaction
            conn.commit();
            conn.setAutoCommit(true);
            
        } catch (SQLException e) {
            System.err.println("❌ Error saving direct expense: " + e.getMessage());
            throw new RuntimeException("Failed to save direct expense", e);
        }
    }
    
    /**
     * Fetch all expenses for a group from database
     * 
     * Flow:
     * 1. Query EXPENSES table for group_id
     * 2. For each expense, query EXPENSE_SPLITS
     * 3. Reconstruct Expense objects with splits
     */
    public List<Expense> findByGroupId(String groupId, UserRepository userRepo) {
        String expenseSql = "SELECT * FROM expenses WHERE group_id = ? ORDER BY created_at DESC";
        String splitSql = "SELECT * FROM expense_splits WHERE expense_id = ?";
        List<Expense> expenses = new ArrayList<>();
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement expenseStmt = conn.prepareStatement(expenseSql)) {
            
            expenseStmt.setString(1, groupId);
            ResultSet expenseRs = expenseStmt.executeQuery();
            
            while (expenseRs.next()) {
                String expenseId = expenseRs.getString("expense_id");
                
                // Fetch splits for this expense
                List<Split> splits = new ArrayList<>();
                try (PreparedStatement splitStmt = conn.prepareStatement(splitSql)) {
                    splitStmt.setString(1, expenseId);
                    ResultSet splitRs = splitStmt.executeQuery();
                    
                    while (splitRs.next()) {
                        String userId = splitRs.getString("user_id");
                        double amountOwe = splitRs.getDouble("amount_owed");
                        
                        User user = userRepo.findById(userId);
                        if (user != null) {
                            Split split = new Split(user, amountOwe);
                            splits.add(split);
                        }
                    }
                }
                
                // Reconstruct Expense object
                User paidByUser = userRepo.findById(expenseRs.getString("paid_by_user_id"));
                if (paidByUser != null) {
                    Expense expense = new Expense(
                        expenseId,
                        expenseRs.getDouble("amount"),
                        expenseRs.getString("description"),
                        paidByUser,
                        groupId,
                        ExpenseType.GROUP,
                        org.example.Expense.ExpenseSplitType.valueOf(expenseRs.getString("split_type")),
                        splits
                    );
                    expenses.add(expense);
                }
            }
            
            System.out.println("✅ Fetched " + expenses.size() + " expenses for group: " + groupId);
            
        } catch (SQLException e) {
            System.err.println("❌ Error fetching expenses: " + e.getMessage());
        }
        
        return expenses;
    }
    
    /**
     * Fetch direct expenses between two users
     * 
     * Query all DIRECT expenses (group_id IS NULL) involving both users
     */
    public List<Expense> findDirectExpensesBetweenUsers(String userId1, String userId2, UserRepository userRepo) {
        String expenseSql = "SELECT * FROM expenses WHERE group_id IS NULL AND expense_type = 'DIRECT' " +
                           "AND (paid_by_user_id = ? OR paid_by_user_id = ?) ORDER BY created_at DESC";
        String splitSql = "SELECT * FROM expense_splits WHERE expense_id = ?";
        List<Expense> expenses = new ArrayList<>();
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement expenseStmt = conn.prepareStatement(expenseSql)) {
            
            expenseStmt.setString(1, userId1);
            expenseStmt.setString(2, userId2);
            ResultSet expenseRs = expenseStmt.executeQuery();
            
            while (expenseRs.next()) {
                String expenseId = expenseRs.getString("expense_id");
                
                // Fetch splits
                List<Split> splits = new ArrayList<>();
                try (PreparedStatement splitStmt = conn.prepareStatement(splitSql)) {
                    splitStmt.setString(1, expenseId);
                    ResultSet splitRs = splitStmt.executeQuery();
                    
                    while (splitRs.next()) {
                        String userId = splitRs.getString("user_id");
                        double amountOwe = splitRs.getDouble("amount_owed");
                        User user = userRepo.findById(userId);
                        if (user != null) {
                            splits.add(new Split(user, amountOwe));
                        }
                    }
                }
                
                // Reconstruct Expense
                User paidByUser = userRepo.findById(expenseRs.getString("paid_by_user_id"));
                if (paidByUser != null) {
                    Expense expense = new Expense(
                        expenseId,
                        expenseRs.getDouble("amount"),
                        expenseRs.getString("description"),
                        paidByUser,
                        null,  // Direct expense
                        ExpenseType.DIRECT,
                        org.example.Expense.ExpenseSplitType.valueOf(expenseRs.getString("split_type")),
                        splits
                    );
                    expenses.add(expense);
                }
            }
            
            System.out.println("✅ Fetched " + expenses.size() + " direct expenses between: " + userId1 + " & " + userId2);
            
        } catch (SQLException e) {
            System.err.println("❌ Error fetching direct expenses: " + e.getMessage());
        }
        
        return expenses;
    }
    
    /**
     * Fetch all direct expenses for a user
     */
    public List<Expense> findAllDirectExpensesForUser(String userId, UserRepository userRepo) {
        String expenseSql = "SELECT * FROM expenses WHERE group_id IS NULL AND expense_type = 'DIRECT' " +
                           "AND (paid_by_user_id = ? OR expense_id IN (" +
                           "  SELECT expense_id FROM expense_splits WHERE user_id = ?)) ORDER BY created_at DESC";
        String splitSql = "SELECT * FROM expense_splits WHERE expense_id = ?";
        List<Expense> expenses = new ArrayList<>();
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement expenseStmt = conn.prepareStatement(expenseSql)) {
            
            expenseStmt.setString(1, userId);
            expenseStmt.setString(2, userId);
            ResultSet expenseRs = expenseStmt.executeQuery();
            
            while (expenseRs.next()) {
                String expenseId = expenseRs.getString("expense_id");
                
                // Fetch splits
                List<Split> splits = new ArrayList<>();
                try (PreparedStatement splitStmt = conn.prepareStatement(splitSql)) {
                    splitStmt.setString(1, expenseId);
                    ResultSet splitRs = splitStmt.executeQuery();
                    
                    while (splitRs.next()) {
                        String splitUserId = splitRs.getString("user_id");
                        double amountOwe = splitRs.getDouble("amount_owed");
                        User user = userRepo.findById(splitUserId);
                        if (user != null) {
                            splits.add(new Split(user, amountOwe));
                        }
                    }
                }
                
                // Reconstruct Expense
                User paidByUser = userRepo.findById(expenseRs.getString("paid_by_user_id"));
                if (paidByUser != null) {
                    Expense expense = new Expense(
                        expenseId,
                        expenseRs.getDouble("amount"),
                        expenseRs.getString("description"),
                        paidByUser,
                        null,
                        ExpenseType.DIRECT,
                        org.example.Expense.ExpenseSplitType.valueOf(expenseRs.getString("split_type")),
                        splits
                    );
                    expenses.add(expense);
                }
            }
            
            System.out.println("✅ Fetched " + expenses.size() + " direct expenses for user: " + userId);
            
        } catch (SQLException e) {
            System.err.println("❌ Error fetching user's direct expenses: " + e.getMessage());
        }
        
        return expenses;
    }
    
    /**
     * Find expense by ID
     */
    public Expense findById(String expenseId, UserRepository userRepo) {
        String sql = "SELECT * FROM expenses WHERE expense_id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, expenseId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                User paidByUser = userRepo.findById(rs.getString("paid_by_user_id"));
                ExpenseType expenseType = ExpenseType.valueOf(rs.getString("expense_type"));
                String groupId = rs.getString("group_id");
                
                // Fetch splits
                List<Split> splits = new ArrayList<>();
                String splitSql = "SELECT * FROM expense_splits WHERE expense_id = ?";
                try (PreparedStatement splitStmt = conn.prepareStatement(splitSql)) {
                    splitStmt.setString(1, expenseId);
                    ResultSet splitRs = splitStmt.executeQuery();
                    while (splitRs.next()) {
                        User user = userRepo.findById(splitRs.getString("user_id"));
                        if (user != null) {
                            splits.add(new Split(user, splitRs.getDouble("amount_owed")));
                        }
                    }
                }
                
                if (paidByUser != null) {
                    Expense expense = new Expense(
                        expenseId,
                        rs.getDouble("amount"),
                        rs.getString("description"),
                        paidByUser,
                        groupId,
                        expenseType,
                        org.example.Expense.ExpenseSplitType.valueOf(rs.getString("split_type")),
                        splits
                    );
                    System.out.println("✅ Expense fetched: " + expenseId);
                    return expense;
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error fetching expense: " + e.getMessage());
        }
        
        return null;
    }
}
