package org.example.Controllers;

import org.example.Balance.Balance;
import org.example.Split.Split;
import org.example.User.User;
import org.example.Balance.UserExpenseBalanceSheet;
import org.example.Group.Group;
import org.example.Expense.Expense;

import java.util.List;
import java.util.Map;
import java.util.HashMap;


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




}


//Updates:
//User	totalPayment	totalYourExpense	totalYouGetBack	totalYouOwe
//Alice (Payer)	₹600	₹200 (self-expense)	₹400 (from Bob & Charlie)	₹0
//Bob (Owing)	₹0	₹200	₹0	₹200 (owes Alice)
//Charlie (Owing)	₹0	₹200	₹0	₹200 (owes Alice)