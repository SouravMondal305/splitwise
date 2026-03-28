package org.example.Controllers;

import org.example.Balance.Balance;
import org.example.Split.Split;
import org.example.User.User;
import org.example.Balance.UserExpenseBalanceSheet;
import org.example.Group.Group;
import org.example.Expense.Expense;
import org.example.Payment.Payment;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;


//✅ Simple logic in steps:
//
//Step 1: Update total expense paid.
//Step 2: Loop through each person who needs to pay.
//Step 3: Update payer's balance (kitna wapas aana chahiye).
//Step 4: Update owe karne wale ka balance (kitna paisa dena hai).

public class BalanceSheetController {

    public void updateUserExpenseBalanceSheet(User payer, List<Split> splits, double totalExpense) {

        // Get balance sheet of the person who paid (jisne paisa diya)
        UserExpenseBalanceSheet payerSheet = payer.getUserExpenseBalanceSheet();
        payerSheet.setTotalPayment(payerSheet.getTotalPayment() + totalExpense);

        // Go through each person who needs to pay (jo contribute karega)
        for (Split split : splits) {
            User personWhoOwes = split.getUser();  // The person who has to pay back
            UserExpenseBalanceSheet owesSheet = personWhoOwes.getUserExpenseBalanceSheet(); //Jo banda paisa dene wala hai, uski balance sheet nikal lo taaki uske records update kar sakein.
            double amountToPay = split.getAmountOwe();  // Kitna paisa dena hai

            if (payer.getUserId().equals(personWhoOwes.getUserId())) {
                // If payer is also involved in expense, update their expense share
                payerSheet.setTotalYourExpense(payerSheet.getTotalYourExpense() + amountToPay);
            } else {
                // Payer ka paisa wapas aana chahiye
                payerSheet.setTotalYouGetBack(payerSheet.getTotalYouGetBack() + amountToPay);

                // Store how much this person has to return to payer
                Balance payerBalance = payerSheet.getUserVsBalance()
                        .computeIfAbsent(personWhoOwes.getUserId(), k -> new Balance());
                payerBalance.setAmountGetBack(payerBalance.getAmountGetBack() + amountToPay);

                // Update the balance of the person who owes money (jisne paisa dena hai)
                owesSheet.setTotalYouOwe(owesSheet.getTotalYouOwe() + amountToPay);
                owesSheet.setTotalYourExpense(owesSheet.getTotalYourExpense() + amountToPay);

                // Store how much this person owes to payer (Jis bande ko paisa dena hai (personWhoOwes), uska record update kar rahe hain ki usne payer ko kitna paisa dena hai.)
                Balance owesBalance = owesSheet.getUserVsBalance()
                        .computeIfAbsent(payer.getUserId(), k -> new Balance());
                owesBalance.setAmountOwe(owesBalance.getAmountOwe() + amountToPay);
            }
        }
    }

    public void showBalanceSheetOfUser(User user){

        System.out.println("---------------------------------------");

        System.out.println("Balance sheet of user : " + user.getUserId());

        UserExpenseBalanceSheet userExpenseBalanceSheet =  user.getUserExpenseBalanceSheet();

        System.out.println("TotalYourExpense: " + userExpenseBalanceSheet.getTotalYourExpense());
        System.out.println("TotalGetBack: " + userExpenseBalanceSheet.getTotalYouGetBack());
        System.out.println("TotalYourOwe: " + userExpenseBalanceSheet.getTotalYouOwe());
        System.out.println("TotalPaymnetMade: " + userExpenseBalanceSheet.getTotalPayment());
        for(Map.Entry<String, Balance> entry : userExpenseBalanceSheet.getUserVsBalance().entrySet()){

            String userID = entry.getKey();
            Balance balance = entry.getValue();

            System.out.println("userID:" + userID + " YouGetBack:" + balance.getAmountGetBack() + " YouOwe:" + balance.getAmountOwe());
        }

        System.out.println("---------------------------------------");

    }

    public void showGroupBalanceSheet(Group group) {
        System.out.println("\n=======================================");
        System.out.println("GROUP BALANCE SHEET: " + group.getGroupId());
        System.out.println("=======================================");

        // Create group-specific balance tracking
        Map<String, Double> memberTotalPaid = new HashMap<>();
        Map<String, Double> memberTotalOwed = new HashMap<>();
        double totalGroupExpense = 0;

        // Initialize all members
        for (User member : group.getGroupMembers()) {
            memberTotalPaid.put(member.getUserId(), 0.0);
            memberTotalOwed.put(member.getUserId(), 0.0);
        }

        // Calculate balances from group expenses only
        for (Expense expense : group.getExpenses()) {
            double expenseAmount = expense.expenseAmount;
            User payer = expense.paidByUser;
            totalGroupExpense += expenseAmount;

            // Payer paid this amount
            memberTotalPaid.put(payer.getUserId(), memberTotalPaid.get(payer.getUserId()) + expenseAmount);

            // Add what each person owes from this expense
            for (Split split : expense.splitDetails) {
                User owingUser = split.getUser();
                double amountOwed = split.getAmountOwe();
                memberTotalOwed.put(owingUser.getUserId(), memberTotalOwed.get(owingUser.getUserId()) + amountOwed);
            }
        }

        System.out.println("Total Group Expense: ₹" + String.format("%.2f", totalGroupExpense));
        System.out.println("\n" + String.format("%-15s %-15s %-15s %-15s", "Member", "Paid", "Their Share", "Balance"));
        System.out.println("-----------------------------------------------------------");

        // Display each member's group-specific balance
        for (User member : group.getGroupMembers()) {
            String memberId = member.getUserId();
            double paid = memberTotalPaid.get(memberId);
            double owed = memberTotalOwed.get(memberId);
            double balance = paid - owed;  // Positive = to receive, Negative = to pay

            String balanceStatus = "";
            if (balance > 0) {
                balanceStatus = "Gets back ₹" + String.format("%.2f", balance);
            } else if (balance < 0) {
                balanceStatus = "Owes ₹" + String.format("%.2f", Math.abs(balance));
            } else {
                balanceStatus = "Settled";
            }

            System.out.println(String.format("%-15s ₹%-14.2f ₹%-14.2f %s", 
                    memberId, paid, owed, balanceStatus));
        }

        System.out.println("=======================================\n");
    }

    /**
     * Calculates who pays whom in the group using an optimal payment settlement algorithm.
     * Uses a two-pointer approach with min/max balances to minimize the number of transactions.
     * 
     * @param group The group to calculate payments for
     * @return List of Payment objects representing the settlement transactions
     */
    public List<Payment> getGroupPaymentSettlement(Group group) {
        List<Payment> payments = new ArrayList<>();
        
        // Create group-specific balance tracking
        Map<String, Double> memberBalance = new HashMap<>();
        Map<String, User> userMap = new HashMap<>();
        
        // Initialize all members
        for (User member : group.getGroupMembers()) {
            memberBalance.put(member.getUserId(), 0.0);
            userMap.put(member.getUserId(), member);
        }
        
        // Calculate net balance for each member (positive = owed to them, negative = they owe)
        for (Expense expense : group.getExpenses()) {
            double expenseAmount = expense.expenseAmount;
            User payer = expense.paidByUser;
            
            // Decrease payer's balance (they paid more than their share initially)
            memberBalance.put(payer.getUserId(), memberBalance.get(payer.getUserId()) - expenseAmount);
            
            // Add what each person owes from this expense
            for (Split split : expense.splitDetails) {
                User owingUser = split.getUser();
                double amountOwed = split.getAmountOwe();
                memberBalance.put(owingUser.getUserId(), memberBalance.get(owingUser.getUserId()) + amountOwed);
            }
        }
        
        // Create lists for debtors (negative balance) and creditors (positive balance)
        List<Map.Entry<String, Double>> debtors = new ArrayList<>();
        List<Map.Entry<String, Double>> creditors = new ArrayList<>();
        
        final double EPSILON = 1e-9; // For floating point comparison
        for (Map.Entry<String, Double> entry : memberBalance.entrySet()) {
            if (entry.getValue() < -EPSILON) {
                debtors.add(entry);
            } else if (entry.getValue() > EPSILON) {
                creditors.add(entry);
            }
        }
        
        // Sort debtors by amount owed (ascending) and creditors by amount to receive (descending)
        debtors.sort((a, b) -> Double.compare(a.getValue(), b.getValue()));
        creditors.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        
        // Match debtors with creditors to create minimal transactions
        int debtorIdx = 0;
        int creditorIdx = 0;
        
        while (debtorIdx < debtors.size() && creditorIdx < creditors.size()) {
            String debtorId = debtors.get(debtorIdx).getKey();
            double debtorAmount = Math.abs(debtors.get(debtorIdx).getValue());
            
            String creditorId = creditors.get(creditorIdx).getKey();
            double creditorAmount = creditors.get(creditorIdx).getValue();
            
            User debtor = userMap.get(debtorId);
            User creditor = userMap.get(creditorId);
            
            // Amount to transfer is the minimum of what debtor owes and what creditor is owed
            double amountToTransfer = Math.min(debtorAmount, creditorAmount);
            
            // Create payment
            payments.add(new Payment(
                    debtorId, 
                    debtor.getUserName(),
                    creditorId,
                    creditor.getUserName(),
                    amountToTransfer
            ));
            
            // Update remaining amounts
            debtorAmount -= amountToTransfer;
            creditorAmount -= amountToTransfer;
            
            // Move to next debtor or creditor
            if (debtorAmount < EPSILON) {
                debtorIdx++;
            } else {
                debtors.get(debtorIdx).setValue(-debtorAmount);
            }
            
            if (creditorAmount < EPSILON) {
                creditorIdx++;
            } else {
                creditors.get(creditorIdx).setValue(creditorAmount);
            }
        }
        
        return payments;
    }

    /**
     * Display who pays whom in the group with amounts
     * 
     * @param group The group to show payment settlement for
     */
    public void showGroupPaymentSettlement(Group group) {
        System.out.println("\n=======================================");
        System.out.println("WHO PAYS WHOM - GROUP: " + group.getGroupId());
        System.out.println("=======================================");
        
        List<Payment> payments = getGroupPaymentSettlement(group);
        
        if (payments.isEmpty()) {
            System.out.println("All debts are settled!");
        } else {
            System.out.println("\nPayment Instructions:");
            System.out.println(String.format("%-40s | %s", "Transaction", "Amount"));
            System.out.println("-------------------------------------------------------------");
            
            for (Payment payment : payments) {
                System.out.println(String.format("%-40s | ₹%.2f", 
                        payment.getPayerName() + " → " + payment.getReceiverName(), 
                        payment.getAmount()));
            }
        }
        
        System.out.println("=======================================\n");
    }

    /**
     * Display complete group settlement summary with balance sheet and payment instructions
     * Combines group balance sheet and payment settlement in a single comprehensive view
     * 
     * @param group The group to show complete settlement summary for
     */
    public void showGroupSettlementSummary(Group group) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║           GROUP SETTLEMENT SUMMARY: " + String.format("%-30s", group.getGroupId()) + "║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        // Create group-specific balance tracking
        Map<String, Double> memberTotalPaid = new HashMap<>();
        Map<String, Double> memberTotalOwed = new HashMap<>();
        Map<String, User> userMap = new HashMap<>();
        double totalGroupExpense = 0;

        // Initialize all members
        for (User member : group.getGroupMembers()) {
            memberTotalPaid.put(member.getUserId(), 0.0);
            memberTotalOwed.put(member.getUserId(), 0.0);
            userMap.put(member.getUserId(), member);
        }

        // Calculate balances from group expenses only
        for (Expense expense : group.getExpenses()) {
            double expenseAmount = expense.expenseAmount;
            User payer = expense.paidByUser;
            totalGroupExpense += expenseAmount;

            // Payer paid this amount
            memberTotalPaid.put(payer.getUserId(), memberTotalPaid.get(payer.getUserId()) + expenseAmount);

            // Add what each person owes from this expense
            for (Split split : expense.splitDetails) {
                User owingUser = split.getUser();
                double amountOwed = split.getAmountOwe();
                memberTotalOwed.put(owingUser.getUserId(), memberTotalOwed.get(owingUser.getUserId()) + amountOwed);
            }
        }

        // Display group expense summary
        System.out.println("\n┌─ EXPENSE SUMMARY ─────────────────────────────────────────────┐");
        System.out.println("│ Total Group Expense: ₹" + String.format("%-54.2f", totalGroupExpense) + "│");
        System.out.println("└───────────────────────────────────────────────────────────────┘");

        // Display each member's balance
        System.out.println("\n┌─ MEMBER BALANCES ─────────────────────────────────────────────┐");
        System.out.println("│ " + String.format("%-15s %-14s %-14s %-22s", "Member", "Paid", "Their Share", "Balance") + "│");
        System.out.println("├───────────────────────────────────────────────────────────────┤");

        for (User member : group.getGroupMembers()) {
            String memberId = member.getUserId();
            double paid = memberTotalPaid.get(memberId);
            double owed = memberTotalOwed.get(memberId);
            double balance = paid - owed;

            String balanceStatus = "";
            if (balance > 0.001) {
                balanceStatus = "Gets ₹" + String.format("%.2f", balance);
            } else if (balance < -0.001) {
                balanceStatus = "Owes ₹" + String.format("%.2f", Math.abs(balance));
            } else {
                balanceStatus = "Settled";
            }

            System.out.println("│ " + String.format("%-15s ₹%-13.2f ₹%-13.2f %-22s", 
                    memberId, paid, owed, balanceStatus) + "│");
        }
        System.out.println("└───────────────────────────────────────────────────────────────┘");

        // Display payment settlement
        List<Payment> payments = getGroupPaymentSettlement(group);
        System.out.println("\n┌─ PAYMENT INSTRUCTIONS ────────────────────────────────────────┐");
        
        if (payments.isEmpty()) {
            System.out.println("│ All debts are settled!                                        │");
        } else {
            System.out.println("│ " + String.format("%-63s", "Transaction") + "│");
            System.out.println("├───────────────────────────────────────────────────────────────┤");
            
            for (Payment payment : payments) {
                String transaction = payment.getPayerName() + " → " + payment.getReceiverName() + 
                                   " : ₹" + String.format("%.2f", payment.getAmount());
                System.out.println("│ " + String.format("%-63s", transaction) + "│");
            }
        }
        System.out.println("└───────────────────────────────────────────────────────────────┘\n");
    }

    /**
     * Get payment settlement for DIRECT expenses between users
     * 
     * Similar to group settlement but for peer-to-peer expenses
     * 
     * @param expenses List of direct expenses between two users
     * @param users Map of all users involved
     * @return List of minimal payments needed
     */
    public List<Payment> getDirectPaymentSettlement(List<Expense> expenses, Map<String, User> users) {
        final double EPSILON = 1e-9; // For floating point comparison
        
        // Calculate net balance for each user
        Map<String, Double> userBalance = new HashMap<>();
        
        for (Expense expense : expenses) {
            User payer = expense.paidByUser;
            double totalAmount = expense.expenseAmount;
            
            // Initialize user in map if not present
            userBalance.putIfAbsent(payer.getUserId(), 0.0);
            
            // Payer gets money back
            userBalance.put(payer.getUserId(), userBalance.get(payer.getUserId()) + totalAmount);
            
            // Calculate what each person owes
            for (Split split : expense.splitDetails) {
                User owingUser = split.getUser();
                double amountOwed = split.getAmountOwe();
                
                userBalance.putIfAbsent(owingUser.getUserId(), 0.0);
                
                if (payer.getUserId().equals(owingUser.getUserId())) {
                    // Payer also owns share, reduce their credit
                    userBalance.put(owingUser.getUserId(), userBalance.get(owingUser.getUserId()) - amountOwed);
                } else {
                    // Other user owes
                    userBalance.put(owingUser.getUserId(), userBalance.get(owingUser.getUserId()) - amountOwed);
                }
            }
        }
        
        // Separate debtors and creditors
        ArrayList<Map.Entry<String, Double>> debtors = new ArrayList<>();
        ArrayList<Map.Entry<String, Double>> creditors = new ArrayList<>();
        
        for (Map.Entry<String, Double> entry : userBalance.entrySet()) {
            if (entry.getValue() < -EPSILON) {
                debtors.add(entry);
            } else if (entry.getValue() > EPSILON) {
                creditors.add(entry);
            }
        }
        
        // Two-pointer algorithm to match payments
        List<Payment> payments = new ArrayList<>();
        int debtorIdx = 0;
        int creditorIdx = 0;
        
        while (debtorIdx < debtors.size() && creditorIdx < creditors.size()) {
            String debtorId = debtors.get(debtorIdx).getKey();
            double debtorAmount = Math.abs(debtors.get(debtorIdx).getValue());
            
            String creditorId = creditors.get(creditorIdx).getKey();
            double creditorAmount = creditors.get(creditorIdx).getValue();
            
            User debtor = users.get(debtorId);
            User creditor = users.get(creditorId);
            
            double amountToTransfer = Math.min(debtorAmount, creditorAmount);
            
            payments.add(new Payment(debtorId, debtor.getUserName(), creditorId, 
                                     creditor.getUserName(), amountToTransfer));
            
            debtorAmount -= amountToTransfer;
            creditorAmount -= amountToTransfer;
            
            if (debtorAmount < EPSILON) {
                debtorIdx++;
            } else {
                debtors.get(debtorIdx).setValue(-debtorAmount);
            }
            
            if (creditorAmount < EPSILON) {
                creditorIdx++;
            } else {
                creditors.get(creditorIdx).setValue(creditorAmount);
            }
        }
        
        return payments;
    }
    
    /**
     * Display payment settlement for direct expenses
     * 
     * @param expenses List of direct expenses
     * @param users Map of all users involved
     */
    public void showDirectPaymentSettlement(List<Expense> expenses, Map<String, User> users) {
        List<Payment> payments = getDirectPaymentSettlement(expenses, users);
        
        System.out.println("\n═══════════════════════════════════════════════════════════════");
        System.out.println("         DIRECT EXPENSE SETTLEMENT SUMMARY");
        System.out.println("═══════════════════════════════════════════════════════════════");
        
        if (payments.isEmpty()) {
            System.out.println("✅ All expenses are settled!");
        } else {
            System.out.println("\n💸 PAYMENTS TO BE MADE:");
            for (Payment payment : payments) {
                System.out.println(String.format("   %s → %s : ₹%.2f",
                        payment.getPayerName(), payment.getReceiverName(), payment.getAmount()));
            }
        }
        System.out.println("═══════════════════════════════════════════════════════════════\n");
    }
}


//Updates:
//User	totalPayment	totalYourExpense	totalYouGetBack	totalYouOwe
//Alice (Payer)	₹600	₹200 (self-expense)	₹400 (from Bob & Charlie)	₹0
//Bob (Owing)	₹0	₹200	₹0	₹200 (owes Alice)
//Charlie (Owing)	₹0	₹200	₹0	₹200 (owes Alice)