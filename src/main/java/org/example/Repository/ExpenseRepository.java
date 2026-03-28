package org.example.Repository;

import org.example.Expense.Expense;
import org.example.Split.Split;
import org.example.User.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ExpenseRepository - Data Access Object for Expense entity
 * Handles all database operations for Expenses and Expense_Splits tables
 * 
 * Flow: Expense object → Repository → Database (SAVE)
 *       Database → Repository → Expense object (FETCH)
 */
public class ExpenseRepository {
    
    /**
     * Save an expense with all its splits to database
     * 
     * Flow:
     * 1. Insert expense record in EXPENSES table
     * 2. Insert each split in EXPENSE_SPLITS table
     * 3. Transaction ensures atomicity (all or nothing)
     */
    public void save(Expense expense, String groupId) {
        String expenseSql = "INSERT INTO expenses (expense_id, group_id, paid_by_user_id, " +
                           "description, amount, split_type) VALUES (?, ?, ?, ?, ?, ?)";
        String splitSql = "INSERT INTO expense_splits (expense_id, user_id, amount_owed) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseConfig.getConnection()) {
            // Start transaction
            conn.setAutoCommit(false);
            
            // Step 1: Insert expense
            try (PreparedStatement expenseStmt = conn.prepareStatement(expenseSql)) {
                expenseStmt.setString(1, expense.expenseId);
                expenseStmt.setString(2, groupId);  // Add groupId for database
                expenseStmt.setString(3, expense.paidByUser.getUserId());
                expenseStmt.setString(4, expense.description);
                expenseStmt.setDouble(5, expense.expenseAmount);
                expenseStmt.setString(6, expense.splitType.toString());
                
                expenseStmt.executeUpdate();
                System.out.println("✅ Expense saved: " + expense.expenseId);
            }
            
            // Step 2: Insert splits
            try (PreparedStatement splitStmt = conn.prepareStatement(splitSql)) {
                for (Split split : expense.splitDetails) {
                    splitStmt.setString(1, expense.expenseId);
                    splitStmt.setString(2, split.getUser().getUserId());
                    splitStmt.setDouble(3, split.getAmountOwe());
                    splitStmt.addBatch();
                }
                splitStmt.executeBatch();
                System.out.println("✅ " + expense.splitDetails.size() + " splits saved");
            }
            
            // Commit transaction
            conn.commit();
            conn.setAutoCommit(true);
            
        } catch (SQLException e) {
            System.err.println("❌ Error saving expense: " + e.getMessage());
            throw new RuntimeException("Failed to save expense", e);
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
                List<Split> splits = new ArrayList<>();  // Fetch splits similarly
                
                if (paidByUser != null) {
                    Expense expense = new Expense(
                        expenseId,
                        rs.getDouble("amount"),
                        rs.getString("description"),
                        paidByUser,
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
