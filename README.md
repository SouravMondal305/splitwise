# Splitwise - Low Level Design

A Java-based expense splitting application that efficiently calculates and settles group expenses with an optimized payment algorithm.

## Features

### Core Functionality
- **User Management**: Register and manage multiple users
- **Group Management**: Create groups and add members
- **Expense Tracking**: Create expenses with various split types
- **Balance Calculation**: Automatic calculation of who owes whom
- **Payment Settlement**: Optimized algorithm to determine minimum transactions needed

### Expense Types
- **GROUP Expenses**: Expenses in a group context (has `group_id`)
  - Involves multiple group members
  - Split among all group participants
  - Example: Hotel booking, Restaurant bill, Tour tickets
  
- **DIRECT Expenses**: Peer-to-peer expenses between individuals (group_id is NULL)
  - Direct settlement between 2-3 users
  - No group context required
  - Example: One friend pays for movie tickets, another reimburses
  - Tracked separately from group expenses

### Split Types
- **EQUAL**: Split expense equally among all participants
- **UNEQUAL**: Split expense with custom amounts for each participant
- **PERCENTAGE**: Split expense based on percentages

### Advanced Features
- **Dual Expense System**: Support for both group and direct peer-to-peer expenses
- **Minimal Transaction Algorithm**: Uses a two-pointer matching approach to minimize the number of payment transactions
- **Group Settlement Summary**: Comprehensive view combining expense summary, member balances, and payment instructions
- **Direct Expense Tracking**: Independent tracking of peer-to-peer settlements
- **Individual Balance Sheets**: Detailed breakdown of each user's transactions including group and direct expenses

## Project Structure

```
splitwise/
├── src/main/java/org/example/
│   ├── Main.java                           # Entry point
│   ├── Splitwise.java                      # Main application (Singleton)
│   ├── Balance/
│   │   ├── Balance.java                    # Balance between two users
│   │   └── UserExpenseBalanceSheet.java    # Overall balance sheet for a user
│   ├── Controllers/
│   │   ├── BalanceSheetController.java     # Balance and settlement logic
│   │   ├── DirectExpenseService.java       # Peer-to-peer expense management
│   │   ├── ExpenseController.java          # Expense management
│   │   └── GroupController.java            # Group management
│   ├── Expense/
│   │   ├── Expense.java                    # Expense model (supports GROUP and DIRECT)
│   │   ├── ExpenseType.java                # Enum: GROUP or DIRECT
│   │   └── ExpenseSplitType.java           # Split type enum (EQUAL, UNEQUAL, PERCENTAGE)
│   ├── Group/
│   │   └── Group.java                      # Group model
│   ├── Payment/
│   │   └── Payment.java                    # Payment transaction model
│   ├── Split/
│   │   ├── Split.java                      # Split detail model
│   │   ├── ExpenseSplitStrategy.java       # Strategy pattern interface
│   │   └── SplitStrategies/
│   │       ├── EqualExpenseSplit.java      # Equal split implementation
│   │       ├── PercentageExpenseSplit.java # Percentage split implementation
│   │       └── UnequalExpenseSplit.java    # Unequal split implementation
│   └── User/
│       ├── User.java                       # User model
│       └── UserController.java             # User management
├── pom.xml                                 # Maven configuration
└── README.md                               # This file
```

## Design Patterns Used

### 1. **Singleton Pattern**
- `Splitwise` class uses thread-safe singleton pattern to ensure only one instance exists
- Double-checked locking for performance

```java
public static Splitwise getInstance() {
    if (instance == null) {
        synchronized (Splitwise.class) {
            if (instance == null) {
                instance = new Splitwise();
            }
        }
    }
    return instance;
}
```

### 2. **Strategy Pattern**
- `ExpenseSplitStrategy` interface allows different split calculation strategies
- Implementations: `EqualExpenseSplit`, `PercentageExpenseSplit`, `UnequalExpenseSplit`

### 3. **Controller Pattern**
- Separation of concerns with dedicated controllers:
  - `BalanceSheetController`: Balance calculations and settlement
  - `ExpenseController`: Expense creation and management
  - `GroupController`: Group operations
  - `UserController`: User operations

## Payment Settlement Algorithm

### Overview
The algorithm determines the minimum number of transactions needed to settle all debts in a group using an optimized two-pointer approach.

### Steps

1. **Calculate Net Balance**: For each member, calculate their net balance
   - Positive balance = money owed to them
   - Negative balance = money they owe

2. **Separate Debtors and Creditors**:
   - Debtors: Members with negative balance (those who owe)
   - Creditors: Members with positive balance (those owed money)

3. **Match Pairs**: Use two pointers to match debtors with creditors
   - Amount transferred = min(debtor owes, creditor receives)
   - Move pointers based on remaining amounts

4. **Generate Payments**: Create payment transactions for each match

### Time Complexity
- **O(n log n)** due to sorting of debtors and creditors
- **O(n)** for the matching algorithm
- Overall: **O(n log n)**

### Space Complexity
- **O(n)** for storing balances and payment list

## Usage

### Running the Demo

```bash
# Compile the project
mvn clean compile

# Run the demo
mvn exec:java -Dexec.mainClass="org.example.Main"
```

### Example Output

```
╔════════════════════════════════════════════════════════════════╗
║           GROUP SETTLEMENT SUMMARY: G1001                      ║
╚════════════════════════════════════════════════════════════════╝

┌─ EXPENSE SUMMARY ─────────────────────────────────────────────┐
│ Total Group Expense: ₹1400.00                                  │
└───────────────────────────────────────────────────────────────┘

┌─ MEMBER BALANCES ─────────────────────────────────────────────┐
│ Member          Paid           Their Share    Balance          │
├───────────────────────────────────────────────────────────────┤
│ U1001           ₹900.00        ₹700.00        Gets ₹200.00     │
│ U2001           ₹500.00        ₹400.00        Gets ₹100.00     │
│ U3001           ₹0.00          ₹300.00        Owes ₹300.00     │
└───────────────────────────────────────────────────────────────┘

┌─ PAYMENT INSTRUCTIONS ────────────────────────────────────────┐
│ Transaction                                                    │
├───────────────────────────────────────────────────────────────┤
│ Alice → Charlie : ₹200.00                                      │
│ Bob → Charlie : ₹100.00                                        │
└───────────────────────────────────────────────────────────────┘
```

## API Reference

### Splitwise (Main Class)

```java
// Get singleton instance
Splitwise splitwise = Splitwise.getInstance();

// Run demo
splitwise.runSplitwiseDemo();
```

### BalanceSheetController

```java
// Show individual user balance sheet
balanceSheetController.showBalanceSheetOfUser(user);

// Show group balance sheet
balanceSheetController.showGroupBalanceSheet(group);

// Get payment settlement for a group (returns List<Payment>)
List<Payment> payments = balanceSheetController.getGroupPaymentSettlement(group);

// Display who pays whom (standalone)
balanceSheetController.showGroupPaymentSettlement(group);

// Display combined settlement summary (balance sheet + payments)
balanceSheetController.showGroupSettlementSummary(group);
```

### GroupController

```java
// Create a new group
groupController.createNewGroup(groupId, groupName, creator);

// Get existing group
Group group = groupController.getGroup(groupId);
```

### Group

```java
// Add member to group
group.addMember(user);

// Create expense
group.createExpense(
    expenseId,
    description,
    amount,
    splits,
    splitType,
    paidByUser
);

// Get group members
List<User> members = group.getGroupMembers();

// Get group expenses
List<Expense> expenses = group.getExpenses();
```

## Key Classes

### Payment.java
Represents a single payment transaction between two users.

```java
Payment payment = new Payment(
    payerId, payerName,
    receiverId, receiverName,
    amount
);
```

### Balance.java
Stores balance information between two users (amount owed and amount to get back).

### UserExpenseBalanceSheet.java
Maintains comprehensive balance information for a user:
- Total expenses
- Total payments made
- Total amount owed
- Total amount to get back
- Per-user balance details

## Example: Creating a Group Expense

```java
// Create users
User alice = new User("U1001", "Alice");
User bob = new User("U2001", "Bob");
User charlie = new User("U3001", "Charlie");

// Create group
groupController.createNewGroup("G1001", "Trip", alice);
Group group = groupController.getGroup("G1001");

// Add members
group.addMember(bob);
group.addMember(charlie);

// Create expense (Alice pays ₹900, equally split among 3)
group.createExpense(
    "Exp1001",
    "Dinner",
    900,
    List.of(
        new Split(alice, 300),
        new Split(bob, 300),
        new Split(charlie, 300)
    ),
    ExpenseSplitType.EQUAL,
    alice
);

// Show settlement
balanceSheetController.showGroupSettlementSummary(group);
```

## Algorithm Optimization

### Why Two-Pointer Matching?

Traditional approach (everyone pays everyone) creates O(n²) transactions.

Example with 4 people where 3 owe 1 person:
- **Naive approach**: 3 payments ✓ (optimal)
- **Two-pointer algorithm**: 3 payments ✓

Example with complex debts:
- **Naive approach**: Could create unnecessary intermediate transactions
- **Two-pointer algorithm**: Finds minimal set greedily

The algorithm efficiently matches creditors with debtors to minimize total transactions while settling all debts.

## Future Enhancements

1. **Persistence**: Add database support for storing groups and expenses
2. **Payment Methods**: Support for different payment methods (UPI, Bank Transfer, etc.)
3. **Currency Support**: Multi-currency support with exchange rates
4. **Recurring Expenses**: Support for recurring group expenses
5. **Notifications**: Alert users when payments are due
6. **UI**: Web or mobile interface for better user experience
7. **Analytics**: Insights into spending patterns
8. **Settle Partial Debts**: Allow users to settle debts partially

## Requirements

- Java 16 or higher
- Maven 3.9.14 or higher

## Database Design

### Expense Table Structure

The `EXPENSES` table supports both GROUP and DIRECT expenses:

```sql
CREATE TABLE expenses (
    expense_id VARCHAR(50) PRIMARY KEY,
    group_id VARCHAR(50),              -- NULL for DIRECT expenses
    paid_by_user_id VARCHAR(50) NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    expense_type VARCHAR(50),          -- 'GROUP' or 'DIRECT'
    split_type VARCHAR(50) NOT NULL,   -- 'EQUAL', 'UNEQUAL', 'PERCENTAGE'
    created_at TIMESTAMP,
    -- Constraint: GROUP expenses must have group_id, DIRECT expenses must have NULL group_id
    CHECK ((group_id IS NOT NULL AND expense_type = 'GROUP') OR 
           (group_id IS NULL AND expense_type = 'DIRECT'))
);
```

### Key Design Decisions

1. **Nullable group_id Column**: 
   - For GROUP expenses: `group_id` is NOT NULL (references `groups.group_id`)
   - For DIRECT expenses: `group_id` is NULL (peer-to-peer settlement)
   - CHECK constraint ensures consistency: Either both group_id and expense_type='GROUP' or group_id=NULL and expense_type='DIRECT'

2. **Dual Expense Types**:
   - GROUP: Member-to-member within a group context
   - DIRECT: Individual-to-individual without group context

3. **Split Strategy**:
   - Both types use the same `EXPENSE_SPLITS` table
   - Splits define who owes what for each expense

### Repository Pattern

The `ExpenseRepository` provides separate methods for managing both expense types:

```java
// Save GROUP expense (group_id NOT NULL)
public void saveGroupExpense(Expense expense, String groupId) { ... }

// Save DIRECT expense (group_id NULL)
public void saveDirectExpense(Expense expense) { ... }

// Query GROUP expenses
public List<Expense> findByGroupId(String groupId, UserRepository userRepo) { ... }

// Query DIRECT expenses between two users
public List<Expense> findDirectExpensesBetweenUsers(String userId1, String userId2, UserRepository userRepo) { ... }
```

## Building

```bash
# Clean build
mvn clean build

# Compile only
mvn compile

# Run tests (if added)
mvn test

# Create JAR
mvn package
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is open source and available under the MIT License.

## Author

**Sourav Mondal** - [SouravMondal305](https://github.com/SouravMondal305)

## Acknowledgments

- Design patterns inspired by low-level design best practices
- Algorithm optimization influenced by greedy algorithm strategies
- Payment settlement concept based on real-world Splitwise application

---

**Last Updated**: March 28, 2026
