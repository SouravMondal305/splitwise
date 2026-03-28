package org.example.Controllers;

import org.example.Expense.Expense;
import org.example.Expense.ExpenseSplitType;
import org.example.Expense.ExpenseType;
import org.example.Repository.ExpenseRepository;
import org.example.Repository.UserRepository;
import org.example.Split.Split;
import org.example.User.User;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DirectExpenseService - Manages peer-to-peer expense settlements
 * 
 * Unlike GROUP expenses that involve multiple people in a group,
 * DIRECT expenses are between 2-3 individuals outside of group context.
 * 
 * Responsibilities:
 * 1. Create direct expenses between users
 * 2. Track balance between specific user pairs
 * 3. Settle payments between users
 * 4. Query balance history for specific user pairs
 */
public class DirectExpenseService {
    
    private ExpenseRepository expenseRepository;
    private UserRepository userRepository;
    
    public DirectExpenseService(ExpenseRepository expenseRepository, 
                               UserRepository userRepository) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
    }
    
    /**
     * Create a direct expense between two users
     * 
     * Example: Alice paid $100 for dinner, Bob owes $60
     * 
     * @param paidByUser User who paid
     * @param participantIds List of users sharing (typically 2-3 people)
     * @param description Expense description
     * @param amount Total expense amount
     * @param splitType How to split (EQUAL, UNEQUAL, PERCENTAGE)
     * @param splits Individual split amounts
     * @return Created Expense object
     */
    public Expense createDirectExpense(User paidByUser, 
                                       List<String> participantIds,
                                       String description, 
                                       double amount,
                                       ExpenseSplitType splitType,
                                       List<Split> splits) {
        
        // Ensure paidByUser is in participants
        boolean userIsParticipant = participantIds.stream()
            .anyMatch(id -> id.equals(paidByUser.getUserId()));
        if (!userIsParticipant) {
            participantIds = new ArrayList<>(participantIds);
            participantIds.add(paidByUser.getUserId());
        }
        
        // Validate participants (after adding payer)
        if (participantIds.size() < 2 || participantIds.size() > 3) {
            throw new IllegalArgumentException("Direct expenses must involve 2-3 people. Found: " + participantIds.size());
        }
        
        // Validate all participants exist
        for (String userId : participantIds) {
            User user = userRepository.findById(userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
        }
        
        // Validate splits
        double totalSplitAmount = splits.stream()
            .mapToDouble(Split::getAmountOwe)
            .sum();
        
        if (Math.abs(totalSplitAmount - amount) > 0.01) {
            throw new IllegalArgumentException(
                "Split amounts don't match total. Expected: " + amount + 
                ", Got: " + totalSplitAmount
            );
        }
        
        // Create expense object
        String expenseId = UUID.randomUUID().toString();
        Expense expense = new Expense(
            expenseId,
            amount,
            description,
            paidByUser,
            null,  // groupId = NULL for direct expenses
            ExpenseType.DIRECT,
            splitType,
            splits
        );
        
        // Save to database
        expenseRepository.saveDirectExpense(expense);
        
        System.out.println("✅ Direct expense created: " + expenseId);
        System.out.println("   Between: " + String.join(", ", participantIds));
        System.out.println("   Paid by: " + paidByUser.getUserName());
        System.out.println("   Amount: ₹" + amount);
        
        return expense;
    }
    
    /**
     * Get settlement balance between two users
     * 
     * Flow:
     * 1. Fetch all direct expenses between users
     * 2. Calculate who owes whom and how much
     * 3. Return settlement instruction
     * 
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return Settlement info (who pays whom)
     */
    public String getDirectBalance(String userId1, String userId2) {
        // Fetch both users
        User user1 = userRepository.findById(userId1);
        User user2 = userRepository.findById(userId2);
        
        if (user1 == null || user2 == null) {
            return "❌ One or both users not found";
        }
        
        // Get all direct expenses between them
        List<Expense> expenses = expenseRepository.findDirectExpensesBetweenUsers(userId1, userId2, userRepository);
        
        if (expenses.isEmpty()) {
            return String.format("✅ %s and %s have settled all expenses", 
                user1.getUserName(), user2.getUserName());
        }
        
        // Calculate balances for both users based on their perspective
        double user1OwesToUser2 = 0;
        double user2OwesToUser1 = 0;
        
        for (Expense expense : expenses) {
            User paidBy = expense.getPaidByUser();
            
            // Find if user1 and user2 are both in this split
            for (Split split : expense.getSplitDetails()) {
                User owesTo = split.getUser();
                double amountOwe = split.getAmountOwe();
                
                // Case 1: User1 paid, User2 owes
                if (paidBy.getUserId().equals(userId1) && owesTo.getUserId().equals(userId2)) {
                    user1OwesToUser2 -= amountOwe;  // User2 owes to User1
                }
                // Case 2: User2 paid, User1 owes
                else if (paidBy.getUserId().equals(userId2) && owesTo.getUserId().equals(userId1)) {
                    user2OwesToUser1 -= amountOwe;  // User1 owes to User2
                }
            }
        }
        
        // Net calculation
        double netBalance = user2OwesToUser1 + user1OwesToUser2;
        
        if (Math.abs(netBalance) < 0.01) {
            return String.format("✅ %s and %s are settled up", 
                user1.getUserName(), user2.getUserName());
        }
        
        if (netBalance > 0) {
            return String.format("💸 %s owes %s ₹%.2f", 
                user2.getUserName(), user1.getUserName(), netBalance);
        } else {
            return String.format("💸 %s owes %s ₹%.2f", 
                user1.getUserName(), user2.getUserName(), Math.abs(netBalance));
        }
    }
    
    /**
     * Get all direct expenses for a user
     * 
     * @param userId User ID
     * @return List of direct expenses
     */
    public List<Expense> getUserDirectExpenses(String userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        
        return expenseRepository.findAllDirectExpensesForUser(userId, userRepository);
    }
    
    /**
     * Settle payment between two users
     * 
     * Creates a REVERSE expense entry to mark settlement
     * 
     * @param fromUserId User paying (creditor)
     * @param toUserId User receiving (debtor)
     * @param amount Settlement amount
     */
    public void settleDirectExpense(String fromUserId, String toUserId, double amount) {
        User fromUser = userRepository.findById(fromUserId);
        User toUser = userRepository.findById(toUserId);
        
        if (fromUser == null || toUser == null) {
            throw new IllegalArgumentException("One or both users not found");
        }
        
        if (amount <= 0) {
            throw new IllegalArgumentException("Settlement amount must be positive");
        }
        
        // Create settlement expense (marked as DIRECT)
        List<Split> splits = new ArrayList<>();
        splits.add(new Split(toUser, amount));
        
        Expense settlement = new Expense(
            UUID.randomUUID().toString(),
            amount,
            "Settlement Payment: " + fromUser.getUserName() + " → " + toUser.getUserName(),
            fromUser,
            null,  // DIRECT expense
            ExpenseType.DIRECT,
            ExpenseSplitType.EQUAL,
            splits
        );
        
        expenseRepository.saveDirectExpense(settlement);
        
        System.out.println("✅ Settlement recorded: " + fromUser.getUserName() + " → " + 
                         toUser.getUserName() + " ₹" + amount);
    }
    
    /**
     * Display all direct expenses between two users with details
     */
    public void showDirectExpenseHistory(String userId1, String userId2) {
        User user1 = userRepository.findById(userId1);
        User user2 = userRepository.findById(userId2);
        
        if (user1 == null || user2 == null) {
            System.out.println("❌ One or both users not found");
            return;
        }
        
        List<Expense> expenses = expenseRepository.findDirectExpensesBetweenUsers(userId1, userId2, userRepository);
        
        System.out.println("\n📋 DIRECT EXPENSE HISTORY: " + user1.getUserName() + " ↔ " + user2.getUserName());
        System.out.println("========================================================");
        
        if (expenses.isEmpty()) {
            System.out.println("No direct expenses found");
        } else {
            for (Expense expense : expenses) {
                System.out.println("📌 " + expense.getDescription());
                System.out.println("   Paid by: " + expense.getPaidByUser().getUserName());
                System.out.println("   Amount: ₹" + expense.getExpenseAmount());
                System.out.println("   Splits:");
                for (Split split : expense.getSplitDetails()) {
                    System.out.println("     - " + split.getUser().getUserName() + ": ₹" + split.getAmountOwe());
                }
                System.out.println();
            }
        }
        
        System.out.println("CURRENT BALANCE: " + getDirectBalance(userId1, userId2));
        System.out.println("========================================================\n");
    }
}
