# Architecture Questions - Deep Dive Explanation

## Question 1: Expenses Outside Groups (Direct Expenses)

### The Problem
In the current implementation, expenses **always** belong to a group:
```java
group.createExpense(...);  // Expense is ALWAYS in a group
```

But what about simple peer-to-peer scenarios like:
- Alice and Bob split a coffee (₹200)
- No formal group, just two people
- Quick settlement needed

### Current Design Limitation
```
Expense class:
├─ expenseId
├─ description
├─ amount
├─ paidByUser      ✓ Has user reference
├─ splitType
├─ splitDetails    ✓ Has splits
└─ groupId         ✗ NOT PRESENT (implicit in Group.expenseList)

Database Schema:
├─ expense_id
├─ group_id        ✓ REQUIRED (NOT NULL)
├─ paid_by_user_id
├─ description
└─ amount
```

### Solutions

#### **Solution A: Create Implicit Group for Direct Expenses**

```java
// When Alice and Bob settle directly:
Group directGroup = new Group();
directGroup.setGroupId("DIRECT_U1001_U2001");
directGroup.setGroupName("Direct Settlement");

// Add members
directGroup.addMember(alice);
directGroup.addMember(bob);

// Create expense
directGroup.createExpense(
    "DirectExp001",
    "Coffee",
    200,
    List.of(new Split(alice, 100), new Split(bob, 100)),
    ExpenseSplitType.EQUAL,
    alice
);
```

**Database Result:**
```sql
-- Implicit group created
INSERT INTO groups (group_id, group_name) 
VALUES ('DIRECT_U1001_U2001', 'Direct Settlement');

-- Expense linked to it
INSERT INTO expenses (expense_id, group_id, paid_by_user_id, amount)
VALUES ('DirectExp001', 'DIRECT_U1001_U2001', 'U1001', 200);
```

---

#### **Solution B: Make group_id Optional (NULL)**

**Modify Database Schema:**
```sql
-- Make group_id nullable
ALTER TABLE expenses 
MODIFY group_id VARCHAR(50) NULL;

-- Add business rule constraint
ALTER TABLE expenses 
ADD CHECK (
    (group_id IS NOT NULL) OR  -- Either has group
    (group_id IS NULL)         -- Or is direct expense
);
```

**Modify Expense Class:**
```java
public class Expense {
    String expenseId;
    String description;
    double expenseAmount;
    String groupId;              // ← ADD THIS
    User paidByUser;
    ExpenseSplitType splitType;
    List<Split> splitDetails;
    
    // groupId = null means direct expense between individuals
    // groupId = "G1001" means expense in that group
}
```

**Repository Code:**
```java
public class ExpenseRepository {
    
    // Save group expense (groupId NOT NULL)
    public void saveGroupExpense(Expense expense, String groupId) {
        String sql = "INSERT INTO expenses (expense_id, group_id, ...) VALUES (?, ?, ...)";
        // groupId is provided
    }
    
    // Save direct expense (groupId IS NULL)
    public void saveDirectExpense(Expense expense) {
        String sql = "INSERT INTO expenses (expense_id, group_id, ...) VALUES (?, NULL, ...)";
        // No groupId provided
    }
    
    // Fetch direct expenses for a user
    public List<Expense> findDirectExpenses(String userId1, String userId2) {
        String sql = "SELECT * FROM expenses WHERE group_id IS NULL " +
                     "AND paid_by_user_id IN (?, ?)";
        // Find all direct settlements between these two users
    }
}
```

---

### Comparison

| Aspect | Solution A (Implicit Group) | Solution B (Nullable group_id) |
|--------|---------------------------|---------------------------------|
| Database Change | None | Modify column constraint |
| Code Change | None to current Expense class | Add groupId field |
| Conceptual | Still uses group (virtual) | Direct relationship between users |
| Query Complexity | Simple (filter by groupId) | Need NULL checks |
| Scaling | ✓ Easier (all expenses = grouped) | ✗ Harder (two types) |
| **RECOMMENDED** | ✅ **YES** | ❌ NO |

**Best Practice:** Use Solution A (Implicit Group) because:
- All expenses are conceptually "grouped" (even if implicit)
- Consistent with current architecture
- Simpler queries (no NULL handling)
- Easier to extend (add other members later)

---

---

## Question 2: Repository Layer Architecture

### Full Picture: Data Flow

The Repository Layer is the **Bridge** between Application (Java Objects) and Database (H2).

```
┌──────────────────────────────────────────────────────────────────┐
│                    BUSINESS LOGIC LAYER                          │
│ - BalanceSheetController                                         │
│ - BalanceCalculation                                             │
│ - PaymentSettlement                                              │
│ - Only works with Java Objects                                   │
└────────────────┬─────────────────────────────────────────────────┘
                 │
                 │ Uses repository.save(), .findById(), etc.
                 │
┌────────────────▼──────────────────────────────────────────────────┐
│                    REPOSITORY LAYER (DAO)                         │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  UserRepository          GroupRepository       ExpenseRepository │
│  ┌──────────────────┐    ┌──────────────────┐  ┌──────────────┐ │
│  │ save(user)       │    │ save(group)      │  │ save(expense)│ │
│  │ findById()       │    │ addMember()      │  │ findByGroup()│ │
│  │ findAll()        │    │ findById()       │  │ findById()   │ │
│  │ exists()         │    │ getMembers()     │  │ findDirect() │ │
│  └──────────────────┘    └──────────────────┘  └──────────────┘ │
│                                                                   │
│  All repositories use:                                           │
│  - PreparedStatements (prevent SQL injection)                    │
│  - Transaction management (ACID properties)                      │
│  - Connection pooling (optional, for performance)                │
│                                                                   │
└────────────────┬──────────────────────────────────────────────────┘
                 │
                 │ SQL queries with JDBC
                 │
┌────────────────▼──────────────────────────────────────────────────┐
│              DATABASE ACCESS LAYER (DatabaseConfig)              │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  getConnection()  → Manages H2 connections                       │
│  initializeDatabase() → Creates tables & schema                  │
│  Statement execution → PreparedStatement / Batch operations      │
│                                                                   │
└────────────────┬──────────────────────────────────────────────────┘
                 │
                 │ JDBC Connection
                 │
┌────────────────▼──────────────────────────────────────────────────┐
│                H2 IN-MEMORY DATABASE                              │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  USERS table            GROUPS table          EXPENSES table     │
│  ┌────────────────┐    ┌─────────────────┐   ┌──────────────┐  │
│  │ user_id (PK)   │    │ group_id (PK)   │   │expense_id(PK)│  │
│  │ user_name      │    │ group_name      │   │ group_id (FK)│  │
│  │ email          │    │ created_by (FK) │   │paid_by_id(FK)│  │
│  └────────────────┘    └─────────────────┘   └──────────────┘  │
│                                                                   │
│  GROUP_MEMBERS         EXPENSE_SPLITS       USER_BALANCES       │
│  PAYMENTS (future)     AUDIT_LOG (future)                       │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

### Example Flow: Saving an Expense

#### **Step 1: Business Logic Creates Expense Object**
```java
// BalanceSheetController.java
Expense expense = new Expense(
    "Exp1001",              // expenseId
    900,                    // amount
    "Breakfast",            // description
    alice,                  // paidByUser (User object)
    ExpenseSplitType.EQUAL,
    List.of(
        new Split(alice, 300),
        new Split(bob, 300),
        new Split(charlie, 300)
    )
);
```

**State:** Java in-memory object only
```
Expense obj in RAM
├─ expenseId = "Exp1001"
├─ amount = 900
├─ paidByUser = [User object: alice]
├─ splitDetails = [3 Split objects]
└─ (groupId = NOT STORED IN JAVA CLASS)
```

---

#### **Step 2: Save to Database via Repository**
```java
// Application code
Group group = groupController.getGroup("G1001");
Expense savedExpense = group.createExpense(...);

// Behind the scenes in GroupController or a Service:
expenseRepository.save(expense, "G1001");  // ← groupId added here
```

---

#### **Step 3: Repository Translates to SQL**

**In ExpenseRepository.save():**
```java
public void save(Expense expense, String groupId) {
    // 1. Get connection to H2
    Connection conn = DatabaseConfig.getConnection();
    
    // 2. SQL for EXPENSES table
    String expenseSql = "INSERT INTO expenses " +
        "(expense_id, group_id, paid_by_user_id, description, amount, split_type) " +
        "VALUES (?, ?, ?, ?, ?, ?)";
    
    PreparedStatement expenseStmt = conn.prepareStatement(expenseSql);
    
    // 3. Map Java object to SQL parameters
    expenseStmt.setString(1, expense.expenseId);              // "Exp1001"
    expenseStmt.setString(2, groupId);                        // "G1001"
    expenseStmt.setString(3, expense.paidByUser.getUserId()); // "U1001"
    expenseStmt.setString(4, expense.description);            // "Breakfast"
    expenseStmt.setDouble(5, expense.expenseAmount);          // 900.0
    expenseStmt.setString(6, expense.splitType.toString());   // "EQUAL"
    
    // 4. Execute INSERT
    expenseStmt.executeUpdate();
    
    // 5. Now insert splits
    String splitSql = "INSERT INTO expense_splits " +
        "(expense_id, user_id, amount_owed) VALUES (?, ?, ?)";
    
    PreparedStatement splitStmt = conn.prepareStatement(splitSql);
    
    for (Split split : expense.splitDetails) {
        splitStmt.setString(1, expense.expenseId);     // "Exp1001"
        splitStmt.setString(2, split.getUser().getUserId());  // "U1001", "U2001", etc.
        splitStmt.setDouble(3, split.getAmountOwe());  // 300.0, 300.0, 300.0
        splitStmt.addBatch();  // Add to batch
    }
    
    // 6. Execute all splits at once
    splitStmt.executeBatch();
}
```

**Translation Mapping:**
```
Java Object Field          →  SQL Column        →  Database Value
───────────────────────────────────────────────────────────────
expense.expenseId          →  expense_id        →  "Exp1001"
(not in class)groupId      →  group_id          →  "G1001"
expense.paidByUser.getId() →  paid_by_user_id   →  "U1001"
expense.description        →  description       →  "Breakfast"
expense.expenseAmount      →  amount            →  900.0
expense.splitType          →  split_type        →  "EQUAL"
```

---

#### **Step 4: Database Receives INSERT**

**H2 Database State Before:**
```
EXPENSES table:
(empty)

EXPENSE_SPLITS table:
(empty)
```

**SQL Executed:**
```sql
INSERT INTO expenses (expense_id, group_id, paid_by_user_id, description, amount, split_type)
VALUES ('Exp1001', 'G1001', 'U1001', 'Breakfast', 900.0, 'EQUAL');

INSERT INTO expense_splits (expense_id, user_id, amount_owed)
VALUES ('Exp1001', 'U1001', 300.0);

INSERT INTO expense_splits (expense_id, user_id, amount_owed)
VALUES ('Exp1001', 'U2001', 300.0);

INSERT INTO expense_splits (expense_id, user_id, amount_owed)
VALUES ('Exp1001', 'U3001', 300.0);
```

**H2 Database State After:**
```
EXPENSES table:
┌───────────┬──────────┬─────────────────┬─────────────┬─────────┬────────────┐
│expense_id │ group_id │ paid_by_user_id │ description │ amount  │ split_type │
├───────────┼──────────┼─────────────────┼─────────────┼─────────┼────────────┤
│ Exp1001   │ G1001    │ U1001           │ Breakfast   │ 900.00  │ EQUAL      │
└───────────┴──────────┴─────────────────┴─────────────┴─────────┴────────────┘

EXPENSE_SPLITS table:
┌──────────┬───────────┬──────────┬────────────────┐
│ split_id │expense_id │ user_id  │ amount_owed    │
├──────────┼───────────┼──────────┼────────────────┤
│ 1        │ Exp1001   │ U1001    │ 300.00         │
│ 2        │ Exp1001   │ U2001    │ 300.00         │
│ 3        │ Exp1001   │ U3001    │ 300.00         │
└──────────┴───────────┴──────────┴────────────────┘
```

---

### Example Flow: Fetching Expenses

#### **Step 1: Application Requests Data**
```java
List<Expense> groupExpenses = expenseRepository.findByGroupId("G1001");
```

---

#### **Step 2: Repository Queries Database**

**SQL Queries Executed:**
```sql
-- Query 1: Get all expenses for group
SELECT * FROM expenses WHERE group_id = 'G1001';

-- Result:
-- expense_id | group_id | paid_by_user_id | description | amount | split_type
-- Exp1001    | G1001    | U1001           | Breakfast   | 900    | EQUAL

-- Query 2: Get splits for first expense
SELECT * FROM expense_splits WHERE expense_id = 'Exp1001';

-- Result:
-- split_id | expense_id | user_id | amount_owed
-- 1        | Exp1001    | U1001   | 300
-- 2        | Exp1001    | U2001   | 300
-- 3        | Exp1001    | U3001   | 300
```

---

#### **Step 3: Repository Reconstructs Java Objects**

```java
public List<Expense> findByGroupId(String groupId, UserRepository userRepo) {
    List<Expense> expenses = new ArrayList<>();
    
    // Step A: Execute first query
    ResultSet expenseRs = conn.executeQuery(
        "SELECT * FROM expenses WHERE group_id = 'G1001'"
    );
    
    while (expenseRs.next()) {
        String expenseId = expenseRs.getString("expense_id");  // "Exp1001"
        double amount = expenseRs.getDouble("amount");         // 900.0
        
        // Step B: For each expense, fetch its splits
        List<Split> splits = new ArrayList<>();
        ResultSet splitRs = conn.executeQuery(
            "SELECT * FROM expense_splits WHERE expense_id = 'Exp1001'"
        );
        
        while (splitRs.next()) {
            String userId = splitRs.getString("user_id");      // "U1001"
            double amountOwe = splitRs.getDouble("amount_owed"); // 300.0
            
            // Step C: Fetch User object from UserRepository
            User user = userRepo.findById(userId);  // Nested call
            
            // Step D: Create Split object
            Split split = new Split(user, amountOwe);
            splits.add(split);
        }
        
        // Step E: Fetch payer user
        User paidBy = userRepo.findById("U1001");
        
        // Step F: Create Expense object from database data
        Expense expense = new Expense(
            expenseId,
            amount,
            "Breakfast",
            paidBy,
            ExpenseSplitType.EQUAL,
            splits
        );
        
        expenses.add(expense);
    }
    
    return expenses;  // List of Java objects
}
```

**Object Reconstruction:**
```
Database Rows                 →  Java Objects
────────────────────────────────────────────────
expenses row 1               →  Expense object
├─ expense_id: "Exp1001"      │  ├─ expenseId = "Exp1001"
├─ group_id: "G1001"          │  ├─ amount = 900
├─ paid_by_user_id: "U1001"   │  ├─ paidByUser = [User obj]
├─ description: "Breakfast"   │  ├─ description = "Breakfast"
└─ amount: 900                │  └─ splitDetails = [3 Split objs]

expense_splits rows (3)       →  Split objects (3)
├─ split_id: 1                │  ├─ Split(User: U1001, 300)
├─ user_id: "U1001"           │  ├─ Split(User: U2001, 300)
└─ amount_owed: 300           │  └─ Split(User: U3001, 300)
```

---

#### **Step 4: Application Uses Fetched Objects**

```java
List<Expense> expenses = expenseRepository.findByGroupId("G1001");

// Now work with Java objects (in-memory)
for (Expense expense : expenses) {
    System.out.println(expense.description);  // "Breakfast"
    System.out.println(expense.expenseAmount); // 900
    for (Split split : expense.splitDetails) {
        System.out.println(split.getAmountOwe()); // 300, 300, 300
    }
}
```

---

### Why This Architecture?

| Aspect | Reason |
|--------|--------|
| **Separation of Concerns** | Business logic (BalanceSheetController) doesn't know about SQL |
| **Database Abstraction** | Can switch from H2 to MySQL without changing application code |
| **Testability** | Easy to mock repositories in unit tests |
| **Maintainability** | All SQL code in repositories, all business logic in controllers |
| **Performance** | Can optimize queries/indexes without touching business logic |
| **Security** | PreparedStatements prevent SQL injection |
| **Transactions** | Repository handles ACID compliance (all-or-nothing operations) |

---

### Key Differences from Current Implementation

| Current (In-Memory) | With Repository | With Database |
|-------------------|-----------------|----------------|
| `Group.expenseList` | `ExpenseRepository.findByGroupId()` | SQL JOIN on group_id |
| Objects in RAM | Objects reconstructed from DB | Persistent storage |
| groupId implicit | groupId in repository call | groupId in table |
| No transactions | Transaction-aware | Full ACID support |
| Application shutdown = data lost | Data persists across restarts | Complete history |

---

## Conclusion

**Question 1 Answer:** Use implicit groups for direct expenses (Solution A) to keep the architecture consistent.

**Question 2 Answer:** The repository layer is the critical bridge:
- **Input**: Java objects from business logic
- **Processing**: Convert to SQL, execute queries
- **Output**: Database persistence or reconstructed Java objects
- **Benefit**: Clean separation between OOP and relational paradigms

