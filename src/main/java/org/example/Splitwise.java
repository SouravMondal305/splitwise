package org.example;

import org.example.Controllers.BalanceSheetController;
import org.example.Controllers.DirectExpenseService;
import org.example.Controllers.GroupController;
import org.example.Expense.ExpenseSplitType;
import org.example.Group.Group;
import org.example.Repository.DatabaseConfig;
import org.example.Repository.ExpenseRepository;
import org.example.Repository.GroupRepository;
import org.example.Repository.UserRepository;
import org.example.Split.Split;
import org.example.User.User;
import org.example.User.UserController;

import java.util.Arrays;
import java.util.List;

public class Splitwise {

    private static Splitwise instance; // Singleton instance

    private final UserController userController;
    private final GroupController groupController;
    private final BalanceSheetController balanceSheetController;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final DirectExpenseService directExpenseService;

    // Private constructor to enforce Singleton
    private Splitwise() {
        userController = new UserController();
        groupController = new GroupController();
        balanceSheetController = new BalanceSheetController();
        
        // Initialize repositories
        userRepository = new UserRepository();
        groupRepository = new GroupRepository();
        expenseRepository = new ExpenseRepository();
        directExpenseService = new DirectExpenseService(expenseRepository, userRepository);
    }

    // Public method to get the singleton instance
    public static Splitwise getInstance() {
        if (instance == null) {  // First check (No locking, improves performance)
            synchronized (Splitwise.class) {  // Locking to prevent race condition
                if (instance == null) {  // Second check (Ensures only one instance)
                    instance = new Splitwise();
                }
            }
        }
        return instance;
    }


    public void runSplitwiseDemo() {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║              SPLITWISE - END-TO-END DEMO (H2 DB)              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");
        
        // Initialize database
        DatabaseConfig.initializeDatabase();
        
        // Register users
        registerUsersAndSaveToDb();
        
        // Create and setup GROUP expenses
        createGroupExpenses();
        
        // Create DIRECT peer-to-peer expenses
        createDirectExpenses();
        
        // Display summaries
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    EXPENSE BALANCE SUMMARIES                  ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        
        displayUserBalanceSummaries();
        
        displayGroupBalanceSummary();
        
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║               ✅ END-TO-END DEMO COMPLETED                     ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");
    }

    private void registerUsersAndSaveToDb() {
        // Create users in memory
        User alice = new User("U1001", "Alice");
        User bob = new User("U2001", "Bob");
        User charlie = new User("U3001", "Charlie");
        User diana = new User("U4001", "Diana");
        User eve = new User("U5001", "Eve");
        
        // Register in in-memory controller
        userController.addUser(alice);
        userController.addUser(bob);
        userController.addUser(charlie);
        userController.addUser(diana);
        userController.addUser(eve);
        
        // Save to database (silently)
        userRepository.save(alice);
        userRepository.save(bob);
        userRepository.save(charlie);
        userRepository.save(diana);
        userRepository.save(eve);
    }

    private void createGroupExpenses() {
        // Get users
        User alice = userController.getUser("U1001");
        User bob = userController.getUser("U2001");
        User charlie = userController.getUser("U3001");
        
        // Create group
        Group tripGroup = groupController.createNewGroup("G1001", "Europe Trip 2026", alice);
        tripGroup.addMember(bob);
        tripGroup.addMember(charlie);
        
        // Save to database
        groupRepository.save(tripGroup, "U1001");
        
        // Expense 1: Alice paid for hotel
        List<Split> hotelSplits = Arrays.asList(
            new Split(alice, 1000),
            new Split(bob, 1000),
            new Split(charlie, 1000)
        );
        tripGroup.createExpense(
            "exp_hotel_001",
            "Hotel for 3 nights in Paris",
            3000,
            hotelSplits,
            ExpenseSplitType.EQUAL,
            alice
        );
        
        // Save to database
        expenseRepository.saveGroupExpense(
            new org.example.Expense.Expense("exp_hotel_001", 3000, "Hotel for 3 nights in Paris",
                alice, "G1001", org.example.Expense.ExpenseType.GROUP, ExpenseSplitType.EQUAL, hotelSplits),
            "G1001"
        );
        System.out.println("✅ Expense added: Hotel - Alice paid ₹3000");
        
        // Expense 2: Bob paid for food
        List<Split> foodSplits = Arrays.asList(
            new Split(alice, 400),
            new Split(bob, 400),
            new Split(charlie, 400)
        );
        tripGroup.createExpense(
            "exp_food_001",
            "Lunch at restaurant",
            1200,
            foodSplits,
            ExpenseSplitType.EQUAL,
            bob
        );
        
        expenseRepository.saveGroupExpense(
            new org.example.Expense.Expense("exp_food_001", 1200, "Lunch at restaurant",
                bob, "G1001", org.example.Expense.ExpenseType.GROUP, ExpenseSplitType.EQUAL, foodSplits),
            "G1001"
        );
        System.out.println("✅ Expense added: Food - Bob paid ₹1200");
        
        // Expense 3: Charlie paid for tours
        List<Split> tourSplits = Arrays.asList(
            new Split(alice, 500),
            new Split(bob, 600),
            new Split(charlie, 600)
        );
        tripGroup.createExpense(
            "exp_tour_001",
            "Eiffel Tower & Louvre tickets",
            1700,
            tourSplits,
            ExpenseSplitType.UNEQUAL,
            charlie
        );
        
        expenseRepository.saveGroupExpense(
            new org.example.Expense.Expense("exp_tour_001", 1700, "Eiffel Tower & Louvre tickets",
                charlie, "G1001", org.example.Expense.ExpenseType.GROUP, ExpenseSplitType.UNEQUAL, tourSplits),
            "G1001"
        );
        System.out.println("✅ Expense added: Tours - Charlie paid ₹1700");
    }

    private void createDirectExpenses() {
        User alice = userController.getUser("U1001");
        User diana = userController.getUser("U4001");
        User eve = userController.getUser("U5001");
        User bob = userController.getUser("U2001");
        
        // Direct Expense 1: Alice & Diana - Movie tickets
        List<Split> movieSplits = Arrays.asList(
            new Split(alice, 300),
            new Split(diana, 300)
        );
        try {
            directExpenseService.createDirectExpense(
                alice,
                java.util.Arrays.asList("U4001"),
                "Movie tickets - Cinema",
                600,
                ExpenseSplitType.EQUAL,
                movieSplits
            );
            System.out.println("✅ Expense added: Movie tickets - Alice paid ₹600");
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
        
        // Direct Expense 2: Diana & Eve & Bob - Dinner
        List<Split> dinnerSplits = Arrays.asList(
            new Split(diana, 250),
            new Split(eve, 250),
            new Split(bob, 250)
        );
        try {
            directExpenseService.createDirectExpense(
                diana,
                java.util.Arrays.asList("U5001", "U2001"),
                "Dinner at Italian restaurant",
                750,
                ExpenseSplitType.EQUAL,
                dinnerSplits
            );
            System.out.println("✅ Expense added: Dinner - Diana paid ₹750");
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
        
        // Direct Expense 3: Eve & Bob - Unequal split
        List<Split> taxiSplits = Arrays.asList(
            new Split(eve, 200),
            new Split(bob, 100)
        );
        try {
            directExpenseService.createDirectExpense(
                eve,
                java.util.Arrays.asList("U2001"),
                "Shared taxi ride",
                300,
                ExpenseSplitType.UNEQUAL,
                taxiSplits
            );
            System.out.println("✅ Expense added: Taxi share - Eve paid ₹300");
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    private void displayUserBalanceSummaries() {
        for (User user : userController.getAllUsers()) {
            balanceSheetController.showUserExpenseBalanceSummary(user);
        }
    }

    private void displayGroupBalanceSummary() {
        Group tripGroup = groupController.getGroup("G1001");
        balanceSheetController.showGroupSettlementSummary(tripGroup);
    }
}
