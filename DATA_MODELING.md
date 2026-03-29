# Data Modeling - Splitwise LLD

This document provides comprehensive data modeling information including class hierarchies, entity relationships, and database schema design.

---

## Entity Relationship Diagram (ERD)

```
┌────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│                        DATABASE TABLES (H2)                                │
│                                                                             │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────────┐           ┌───────────────────┐                      │
│  │      USERS       │           │     GROUPS        │                      │
│  ├──────────────────┤           ├───────────────────┤                      │
│  │ user_id (PK)     │           │ group_id (PK)     │                      │
│  │ user_name        │           │ group_name        │                      │
│  │ email            │◄──────────│ created_by (FK)   │                      │
│  │ created_at       │           │ created_at        │                      │
│  └──────────────────┘           └───────────────────┘                      │
│           ▲                             ▲                                   │
│           │ (1:M)                       │ (1:M)                             │
│           │                             │                                   │
│  ┌────────┴─────────────┐       ┌───────┴──────────────┐                  │
│  │  GROUP_MEMBERS       │       │    EXPENSES          │                  │
│  ├──────────────────────┤       ├──────────────────────┤                  │
│  │ group_member_id (PK) │       │ expense_id (PK)      │                  │
│  │ group_id (FK)        │       │ group_id (FK-null)   │                  │
│  │ user_id (FK)         │       │ paid_by_user_id (FK) │                  │
│  │ joined_at            │       │ description          │                  │
│  └──────────────────────┘       │ amount               │                  │
│                                 │ expense_type        │                  │
│                                 │ split_type          │                  │
│                                 │ created_at          │                  │
│                                 └──────────┬───────────┘                  │
│                                            │ (1:M)                         │
│                                 ┌──────────▼──────────┐                   │
│                                 │ EXPENSE_SPLITS      │                   │
│                                 ├─────────────────────┤                   │
│                                 │ split_id (PK)       │                   │
│                                 │ expense_id (FK)     │                   │
│                                 │ user_id (FK)        │                   │
│                                 │ amount_owed         │                   │
│                                 └─────────────────────┘                   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────┐           │
│  │ USER_BALANCES (Pairwise Balance Tracking)                  │           │
│  ├─────────────────────────────────────────────────────────────┤           │
│  │ balance_id (PK)                                             │           │
│  │ group_id (FK - nullable)                                    │           │
│  │ user_id_1 (FK) → USERS                                      │           │
│  │ user_id_2 (FK) → USERS                                      │           │
│  │ amount_owed (user_1 owes user_2)                            │           │
│  │ amount_to_receive (user_1 receives from user_2)             │           │
│  └─────────────────────────────────────────────────────────────┘           │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────┐           │
│  │ PAYMENTS (Settlement Transactions)                          │           │
│  ├─────────────────────────────────────────────────────────────┤           │
│  │ payment_id (PK)                                             │           │
│  │ payer_id (FK) → USERS                                       │           │
│  │ receiver_id (FK) → USERS                                    │           │
│  │ group_id (FK - nullable)                                    │           │
│  │ amount                                                      │           │
│  │ status (PENDING/COMPLETED)                                  │           │
│  │ created_at                                                  │           │
│  └─────────────────────────────────────────────────────────────┘           │
│                                                                             │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Class Hierarchy & Structure

### 1. User Class

```java
public class User {
    String userId;                          // Unique identifier
    String userName;                        // User's display name
    UserExpenseBalanceSheet userExpenseBalanceSheet;  // Personal balance tracker
    
    // Constructor
    public User(String id, String userName)
    
    // Getters
    public String getUserId()
    public String getUserName()
    public UserExpenseBalanceSheet getUserExpenseBalanceSheet()
}
```

**Purpose:** Represents an individual in the system
**Key Relationships:**
- 1 User → 1 UserExpenseBalanceSheet (personal balance)
- 1 User → M Groups (member of groups)
- 1 User → M Expenses (paid by user)
- 1 User → M Splits (participant in expense splits)

---

### 2. Group Class

```java
public class Group {
    String groupId;                         // Unique group identifier
    String groupName;                       // Display name
    List<User> groupMembers;                // Members in group
    List<Expense> expenseList;              // All expenses in group
    ExpenseController expenseController;    // For expense operations
    
    // Methods
    public void addMember(User member)
    public Expense createExpense(String expenseId, String description, 
                                 double expenseAmount, List<Split> splitDetails,
                                 ExpenseSplitType splitType, User paidByUser)
    public List<Expense> getExpenses()
    public List<User> getGroupMembers()
}
```

**Purpose:** Represents a collection of users sharing expenses
**Key Relationships:**
- 1 Group → M Users (members)
- 1 Group → M Expenses (group expenses)

---

### 3. Expense Class

```java
public class Expense {
    String expenseId;                       // Unique expense identifier
    String description;                     // What was the expense for
    double expenseAmount;                   // Total amount of expense
    User paidByUser;                        // Who paid
    String groupId;                         // Group ID (NULL for direct expenses)
    ExpenseType expenseType;                // GROUP or DIRECT
    ExpenseSplitType splitType;             // EQUAL, UNEQUAL, PERCENTAGE
    List<Split> splitDetails;               // How expense is split
    
    // Constructors
    public Expense(String expenseId, double expenseAmount, String description,
                   User paidByUser, ExpenseSplitType splitType, 
                   List<Split> splitDetails)
    
    public Expense(String expenseId, double expenseAmount, String description,
                   User paidByUser, String groupId, ExpenseType expenseType,
                   ExpenseSplitType splitType, List<Split> splitDetails)
    
    // Methods
    public boolean isDirectExpense()        // groupId == null
    public boolean isGroupExpense()         // groupId != null
    public List<Split> getSplitDetails()
}
```

**Enum: ExpenseType**
```java
public enum ExpenseType {
    GROUP,   // Expense in group context
    DIRECT   // Peer-to-peer direct expense
}
```

**Enum: ExpenseSplitType**
```java
public enum ExpenseSplitType {
    EQUAL,       // Equal division among participants
    UNEQUAL,     // Custom amounts for each participant
    PERCENTAGE   // Percentage-based division
}
```

**Purpose:** Represents a transaction/expense
**Key Relationships:**
- 1 Expense → 1 User (paidByUser)
- 1 Expense → 1 Group (nullable, for group expenses)
- 1 Expense → M Splits (how it's divided)

---

### 4. Split Class

```java
public class Split {
    User user;                              // Participant in split
    double amountOwe;                       // Amount this user owes
    
    // Constructor
    public Split(User user, double amountOwe)
    
    // Getters/Setters
    public User getUser()
    public double getAmountOwe()
    public void setAmountOwe(double amountOwe)
}
```

**Purpose:** Represents individual portion of an expense split
**Key Relationships:**
- M Split → 1 Expense (multiple splits per expense)
- 1 Split → 1 User (split belongs to one user)

---

### 5. Balance Class

```java
public class Balance {
    double amountOwe;                       // Amount user owes to another
    double amountGetBack;                   // Amount user should get back
    
    // Getters/Setters
    public double getAmountOwe()
    public void setAmountOwe(double amountOwe)
    public double getAmountGetBack()
    public void setAmountGetBack(double amountGetBack)
}
```

**Purpose:** Tracks pairwise balance between two users
**Key Relationships:**
- M Balance → 1 User (user maintains balances with multiple users)

---

### 6. UserExpenseBalanceSheet Class

```java
public class UserExpenseBalanceSheet {
    Map<String, Balance> userVsBalance;     // Balance with each user
    double totalYourExpense;                // Total expense this user was part of
    double totalPayment;                    // Total amount user paid
    double totalYouOwe;                     // Total amount user owes to others
    double totalYouGetBack;                 // Total amount others owe to user
    
    // Constructor
    public UserExpenseBalanceSheet()
    
    // Getters/Setters
    public Map<String, Balance> getUserVsBalance()
    public double getTotalYourExpense()
    public double getTotalYouOwe()
    public double getTotalYouGetBack()
    public double getTotalPayment()
}
```

**Purpose:** Personal balance sheet for each user
**Key Data:**
- Total expenses participated in
- Total amount paid
- Total owe vs get back
- Pairwise balance with each other user

---

### 7. Payment Class

```java
public class Payment {
    String payerId;                         // User who needs to pay
    String payerName;
    String receiverId;                      // User who receives payment
    String receiverName;
    double amount;                          // Amount to be paid
    
    // Constructor
    public Payment(String payerId, String payerName, String receiverId,
                   String receiverName, double amount)
    
    // Getters
    public String getPayerId()
    public String getPayerName()
    public String getReceiverId()
    public String getReceiverName()
    public double getAmount()
}
```

**Purpose:** Represents a settlement transaction (final payment needed)
**Note:** This is the OUTPUT of debt simplification, not stored in DB

---

## Database Schema (H2)

### Table: USERS
```sql
CREATE TABLE USERS (
    user_id VARCHAR(50) PRIMARY KEY,
    user_name VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(100) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Table: GROUPS
```sql
CREATE TABLE GROUPS (
    group_id VARCHAR(50) PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES USERS(user_id)
);
```

### Table: GROUP_MEMBERS
```sql
CREATE TABLE GROUP_MEMBERS (
    group_member_id INT PRIMARY KEY AUTO_INCREMENT,
    group_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (group_id, user_id),
    FOREIGN KEY (group_id) REFERENCES GROUPS(group_id),
    FOREIGN KEY (user_id) REFERENCES USERS(user_id)
);
```

### Table: EXPENSES
```sql
CREATE TABLE EXPENSES (
    expense_id VARCHAR(50) PRIMARY KEY,
    group_id VARCHAR(50),  -- NULL for direct peer-to-peer expenses
    paid_by_user_id VARCHAR(50) NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    expense_type VARCHAR(50) DEFAULT 'GROUP',  -- 'GROUP' or 'DIRECT'
    split_type VARCHAR(50) NOT NULL,           -- 'EQUAL', 'UNEQUAL', 'PERCENTAGE'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES GROUPS(group_id),
    FOREIGN KEY (paid_by_user_id) REFERENCES USERS(user_id),
    CHECK ((group_id IS NOT NULL AND expense_type = 'GROUP') OR 
           (group_id IS NULL AND expense_type = 'DIRECT'))
);
```

### Table: EXPENSE_SPLITS
```sql
CREATE TABLE EXPENSE_SPLITS (
    split_id INT PRIMARY KEY AUTO_INCREMENT,
    expense_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    amount_owed DECIMAL(10,2) NOT NULL,
    UNIQUE (expense_id, user_id),
    FOREIGN KEY (expense_id) REFERENCES EXPENSES(expense_id),
    FOREIGN KEY (user_id) REFERENCES USERS(user_id)
);
```

### Table: USER_BALANCES
```sql
CREATE TABLE USER_BALANCES (
    balance_id INT PRIMARY KEY AUTO_INCREMENT,
    group_id VARCHAR(50),
    user_id_1 VARCHAR(50) NOT NULL,
    user_id_2 VARCHAR(50) NOT NULL,
    amount_owed DECIMAL(10,2) NOT NULL DEFAULT 0,
    amount_to_receive DECIMAL(10,2) NOT NULL DEFAULT 0,
    UNIQUE (group_id, user_id_1, user_id_2),
    FOREIGN KEY (group_id) REFERENCES GROUPS(group_id),
    FOREIGN KEY (user_id_1) REFERENCES USERS(user_id),
    FOREIGN KEY (user_id_2) REFERENCES USERS(user_id)
);
```

### Table: PAYMENTS
```sql
CREATE TABLE PAYMENTS (
    payment_id INT PRIMARY KEY AUTO_INCREMENT,
    payer_id VARCHAR(50) NOT NULL,
    receiver_id VARCHAR(50) NOT NULL,
    group_id VARCHAR(50),
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payer_id) REFERENCES USERS(user_id),
    FOREIGN KEY (receiver_id) REFERENCES USERS(user_id),
    FOREIGN KEY (group_id) REFERENCES GROUPS(group_id)
);
```

---

## Class Diagram in Text Format

```
┌────────────────────────────────────────────────────────────────────┐
│                         SPLITWISE SYSTEM                           │
└────────────────────────────────────────────────────────────────────┘

┌──────────────────────┐
│      User            │
├──────────────────────┤
│ - userId: String     │
│ - userName: String   │
│ - balanceSheet       │
├──────────────────────┤
│ + getUserId()        │
│ + getUserName()      │
│ + getBalanceSheet()  │
└──────────────────────┘
         ▲
         │ 1...*
         │
         │
┌────────┴─────────────────────┐
│      UserController          │
├──────────────────────────────┤
│ - users: List<User>          │
├──────────────────────────────┤
│ + addUser(User)              │
│ + getUser(userId)            │
│ + getAllUsers()              │
└──────────────────────────────┘


┌──────────────────────┐
│      Group           │
├──────────────────────┤
│ - groupId: String    │
│ - groupName: String  │
│ - members: List      │
│ - expenses: List     │
├──────────────────────┤
│ + addMember()        │
│ + createExpense()    │
│ + getGroupMembers()  │
└──────────────────────┘
         │ 1...*
         │ expenses
         │
         ▼
┌──────────────────────────────────┐
│      Expense                     │
├──────────────────────────────────┤
│ - expenseId: String              │
│ - description: String            │
│ - expenseAmount: Double          │
│ - paidByUser: User               │
│ - groupId: String (nullable)     │
│ - expenseType: ExpenseType       │
│ - splitType: ExpenseSplitType    │
│ - splitDetails: List<Split>      │
├──────────────────────────────────┤
│ + isDirectExpense()              │
│ + isGroupExpense()               │
│ + getSplitDetails()              │
└──────────────────────────────────┘
         │ 1...*
         │ splitDetails
         │
         ▼
┌──────────────────────┐
│      Split           │
├──────────────────────┤
│ - user: User         │
│ - amountOwe: Double  │
├──────────────────────┤
│ + getUser()          │
│ + getAmountOwe()     │
└──────────────────────┘


┌─────────────────────────────────────────┐
│  UserExpenseBalanceSheet                │
├─────────────────────────────────────────┤
│ - userVsBalance: Map<String, Balance>   │
│ - totalYourExpense: Double              │
│ - totalPayment: Double                  │
│ - totalYouOwe: Double                   │
│ - totalYouGetBack: Double               │
├─────────────────────────────────────────┤
│ + getUserVsBalance()                    │
│ + getTotalYourExpense()                 │
│ + getTotalYouOwe()                      │
│ + getTotalYouGetBack()                  │
└─────────────────────────────────────────┘
         │ 1...*
         │ balances
         │
         ▼
┌──────────────────────┐
│      Balance         │
├──────────────────────┤
│ - amountOwe: Double  │
│ - amountGetBack      │
│   : Double           │
├──────────────────────┤
│ + getAmountOwe()     │
│ + getAmountGetBack() │
└──────────────────────┘


┌──────────────────────────────────────┐
│    ExpenseController                 │
├──────────────────────────────────────┤
│ - balanceSheetController             │
├──────────────────────────────────────┤
│ + createExpense(...)                 │
└──────────────────────────────────────┘


┌───────────────────────────────────────┐
│  BalanceSheetController               │
├───────────────────────────────────────┤
│ + updateUserExpenseBalanceSheet(...)  │
│ + showBalanceSheetOfUser(...)         │
│ + showGroupBalanceSheet(...)          │
└───────────────────────────────────────┘


┌──────────────────────────────────────┐
│   DirectExpenseService               │
├──────────────────────────────────────┤
│ - expenseRepository                  │
│ - userRepository                     │
├──────────────────────────────────────┤
│ + createDirectExpense(...)           │
│ + settlePayment(...)                 │
│ + getBalanceBetweenUsers(...)        │
└──────────────────────────────────────┘


┌──────────────────────────────────────┐
│  DebtSimplificationService           │
├──────────────────────────────────────┤
│ + simplifyDebts(payments)            │
│ - buildDebtGraph(payments)           │
│ - eliminateCycle(graph)              │
│ - dfsForCycle(...)                   │
│ - eliminateCycleFromPath(...)        │
│ + displaySimplification(...)         │
└──────────────────────────────────────┘

```

---

## Data Flow Example

### Scenario: Create Group Expense

```
User Alice pays ₹1000 for lunch
Bob and Charlie each owe ₹500

Step 1: Input Data
┌─────────────────────┐
│ Payer: Alice        │
│ Amount: 1000        │
│ Participants: B, C  │
│ Split: EQUAL        │
└─────────────────────┘
           │
           ▼
Step 2: Create Splits
┌─────────────────────────────┐
│ Split 1: Bob - 500          │
│ Split 2: Charlie - 500      │
└─────────────────────────────┘
           │
           ▼
Step 3: Create Expense Object
┌──────────────────────────────┐
│ Expense {                     │
│   expenseId: "exp_001"        │
│   paidByUser: Alice           │
│   amount: 1000                │
│   splits: [Bob: 500, Ch: 500] │
│   splitType: EQUAL            │
│ }                             │
└──────────────────────────────┘
           │
           ▼
Step 4: Update Balance Sheets
┌─────────────────────────────────────┐
│ Alice's Sheet:                       │
│  - totalPayment += 1000              │
│  - totalYouGetBack += 1000           │
│  - Balance[Bob].getBack += 500       │
│  - Balance[Charlie].getBack += 500   │
├─────────────────────────────────────┤
│ Bob's Sheet:                         │
│  - totalYouOwe += 500                │
│  - totalExpense += 500               │
│  - Balance[Alice].owe += 500         │
├─────────────────────────────────────┤
│ Charlie's Sheet:                     │
│  - totalYouOwe += 500                │
│  - totalExpense += 500               │
│  - Balance[Alice].owe += 500         │
└─────────────────────────────────────┘
           │
           ▼
Step 5: Store in Repository
┌──────────────────────────┐
│ Save to H2 Database      │
│ - Create EXPENSE row     │
│ - Create SPLIT rows      │
│ - Update BALANCE_SHEETS  │
│ - Update BALANCES        │
└──────────────────────────┘
```

---

## Relationships Summary

| From | To | Cardinality | Type |
|------|----|----|------|
| User | UserExpenseBalanceSheet | 1:1 | Composition |
| User | Balance | 1:M | Association |
| User | Expense | 1:M | Association (paidBy) |
| User | Split | 1:M | Association |
| Group | User | M:M | Association |
| Group | Expense | 1:M | Composition |
| Expense | Split | 1:M | Composition |
| Expense | User | M:1 | Association (paidBy) |

---

## Key Design Considerations

1. **Nullable GroupId in Expense**
   - Allows both group and direct expenses
   - GROUP expenses: groupId != null
   - DIRECT expenses: groupId = null

2. **Balance Bidirectional**
   - Each balance is stored unidirectionally
   - A owes B is separate from B owes A

3. **Split Validation**
   - Total of all split amounts must equal expense amount
   - Validated before creating expense

4. **UserExpenseBalanceSheet**
   - Cached calculation of all balances
   - Avoids recalculating on every query
   - Updated incrementally on each expense

5. **Payment Object**
   - Not persisted in database
   - Generated on-the-fly for settlements
   - Used for debt simplification output

