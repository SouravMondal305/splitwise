# Repository Layer Architecture - Complete Guide

## Overview

The Repository Layer acts as a bridge between the Application Logic (Java Objects) and the Database (H2). It implements the **Data Access Object (DAO) Pattern**.

```
┌─────────────────────────────────────────────────────────────────────┐
│                         APPLICATION LAYER                           │
│  (Business Logic, Controllers, Services)                            │
└────────────────────┬────────────────────────────────────────────────┘
                     │
                     │ Uses
                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      REPOSITORY LAYER (DAO)                         │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐ │
│  │ UserRepository   │  │GroupRepository   │  │ExpenseRepository │ │
│  ├──────────────────┤  ├──────────────────┤  ├──────────────────┤ │
│  │ save()           │  │ save()           │  │ save()           │ │
│  │ findById()       │  │ findById()       │  │ findByGroupId()  │ │
│  │ findAll()        │  │ addMember()      │  │ findById()       │ │
│  │ exists()         │  │ getMembers()     │  │ findDirect()     │ │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘ │
└────────────────────┬────────────────────────────────────────────────┘
                     │
                     │ Execute SQL
                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     DATABASE ACCESS LAYER                           │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │ DatabaseConfig.getConnection()                              │  │
│  │ - Connection pooling (optional)                             │  │
│  │ - Transaction management                                    │  │
│  │ - SQL execution with PreparedStatements                    │  │
│  └──────────────────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────────────────┘
                     │
                     │ SQL Queries
                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        H2 DATABASE                                  │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐   │
│  │  USERS           │ │  GROUPS          │ │  EXPENSES        │   │
│  │  (In-Memory)     │ │  GROUP_MEMBERS   │ │  EXPENSE_SPLITS  │   │
│  │                  │ │                  │ │  USER_BALANCES   │   │
│  │                  │ │                  │ │  PAYMENTS        │   │
│  └──────────────────┘ └──────────────────┘ └──────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Data Flow: SAVE Operation

### When saving an Expense to Database:

```
┌─────────────────────┐
│ Expense Object      │
│ ┌─────────────────┐ │
│ │ expenseId       │ │
│ │ description     │ │
│ │ amount          │ │
│ │ paidByUser      │ │
│ │ splitType       │ │
│ │ splitDetails[]  │ │
│ └─────────────────┘ │
└──────────┬──────────┘
           │
           │ Call: expenseRepository.save(expense, groupId)
           ▼
┌──────────────────────────────────────────┐
│ ExpenseRepository.save()                 │
├──────────────────────────────────────────┤
│ Step 1: Get connection                   │
│ Step 2: Start transaction (ACID)         │
│                                          │
│ Step 3: INSERT into EXPENSES             │
│   - expense_id, group_id, paid_by...     │
│   - executeUpdate()                      │
│                                          │
│ Step 4: INSERT into EXPENSE_SPLITS       │
│   - For each split:                      │
│   - split_id, expense_id, user_id...     │
│   - executeBatch()                       │
│                                          │
│ Step 5: COMMIT transaction               │
│ Step 6: Close connection                 │
└──────────┬───────────────────────────────┘
           │
           │ PreparedStatement + SQL
           ▼
┌─────────────────────────────────────────────────────┐
│ H2 Database                                         │
├─────────────────────────────────────────────────────┤
│ EXPENSES table:                                     │
│ ┌──────────────┬──────────┬────────────────────┐   │
│ │ expense_id   │ group_id │ paid_by_user_id    │...│
│ ├──────────────┼──────────┼────────────────────┤   │
│ │ Exp1001      │ G1001    │ U1001              │...│
│ └──────────────┴──────────┴────────────────────┘   │
│                                                     │
│ EXPENSE_SPLITS table:                              │
│ ┌─────────────┬──────────┬────────┐                │
│ │ split_id    │expense_id│user_id │ amount_owed   │
│ ├─────────────┼──────────┼────────┼───────────────┤
│ │ 1           │ Exp1001  │ U1001  │ 300           │
│ │ 2           │ Exp1001  │ U2001  │ 300           │
│ │ 3           │ Exp1001  │ U3001  │ 300           │
│ └─────────────┴──────────┴────────┴───────────────┘
└─────────────────────────────────────────────────────┘
```

---

## Data Flow: FETCH Operation

### When fetching Expenses from Database:

```
┌──────────────────────────────┐
│ Application Layer            │
│ expenseRepository.           │
│  findByGroupId("G1001")      │
└──────────────┬───────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│ ExpenseRepository.findByGroupId()       │
├─────────────────────────────────────────┤
│ Step 1: SQL Query                       │
│   SELECT * FROM expenses                │
│   WHERE group_id = 'G1001'              │
│                                         │
│ Step 2: Execute Query → ResultSet       │
│   ├─ expense_id: Exp1001                │
│   ├─ group_id: G1001                    │
│   ├─ amount: 900                        │
│   └─ split_type: EQUAL                  │
│                                         │
│ Step 3: For Each Expense Row:           │
│   - Query EXPENSE_SPLITS table          │
│   - Fetch splits for this expense       │
│   - Create Split objects                │
│                                         │
│ Step 4: Reconstruct Java Objects        │
│   - Create Expense instance             │
│   - Populate with data from database    │
│   - Attach Split objects                │
│                                         │
│ Step 5: Return List<Expense>            │
└──────────────┬───────────────────────────┘
               │
               ▼
┌────────────────────────────────┐
│ List<Expense> Objects          │
│ ┌──────────────────────────┐   │
│ │ Expense[0]               │   │
│ │ ├─ expenseId: Exp1001    │   │
│ │ ├─ amount: 900           │   │
│ │ ├─ paidByUser: User obj  │   │
│ │ └─ splitDetails[3]       │   │
│ │    ├─ Split(U1001, 300)  │   │
│ │    ├─ Split(U2001, 300)  │   │
│ │    └─ Split(U3001, 300)  │   │
│ └──────────────────────────┘   │
└────────────────────────────────┘
```

---

## SQL Query Examples in Repository

### 1. SAVE EXPENSE - Multi-table Insert

```java
// Step 1: Insert into EXPENSES
String expenseSql = "INSERT INTO expenses (expense_id, group_id, paid_by_user_id, " +
                   "description, amount, split_type) VALUES (?, ?, ?, ?, ?, ?)";
PreparedStatement expenseStmt = conn.prepareStatement(expenseSql);
expenseStmt.setString(1, expense.expenseId);         // Exp1001
expenseStmt.setString(2, groupId);                   // G1001
expenseStmt.setString(3, paidByUser.getUserId());    // U1001
expenseStmt.setString(4, "Breakfast");               // description
expenseStmt.setDouble(5, 900.0);                     // amount
expenseStmt.setString(6, "EQUAL");                   // split_type
expenseStmt.executeUpdate();  // Actually execute

// Step 2: Insert into EXPENSE_SPLITS (batch)
String splitSql = "INSERT INTO expense_splits (expense_id, user_id, amount_owed) VALUES (?, ?, ?)";
PreparedStatement splitStmt = conn.prepareStatement(splitSql);
for (Split split : expense.splitDetails) {
    splitStmt.setString(1, "Exp1001");               // expense_id
    splitStmt.setString(2, "U1001");                 // user_id
    splitStmt.setDouble(3, 300.0);                   // amount_owed
    splitStmt.addBatch();  // Add to batch, don't execute yet
}
splitStmt.executeBatch();  // Execute all at once
```

---

### 2. FETCH EXPENSES - Multi-table Query

```java
// Query EXPENSES table with GROUP filter
String expenseSql = "SELECT * FROM expenses WHERE group_id = ? ORDER BY created_at DESC";
PreparedStatement expenseStmt = conn.prepareStatement(expenseSql);
expenseStmt.setString(1, "G1001");  // Filter by group
ResultSet expenseRs = expenseStmt.executeQuery();

while (expenseRs.next()) {
    String expenseId = expenseRs.getString("expense_id");     // Exp1001
    double amount = expenseRs.getDouble("amount");            // 900.0
    String splitType = expenseRs.getString("split_type");     // EQUAL
    
    // For each expense, fetch its splits
    String splitSql = "SELECT * FROM expense_splits WHERE expense_id = ?";
    PreparedStatement splitStmt = conn.prepareStatement(splitSql);
    splitStmt.setString(1, expenseId);
    ResultSet splitRs = splitStmt.executeQuery();
    
    while (splitRs.next()) {
        String userId = splitRs.getString("user_id");          // U1001
        double amountOwed = splitRs.getDouble("amount_owed");  // 300.0
        // Create Split object
    }
}
```

---

### 3. CALCULATE WHO OWES WHOM - Complex Query

```java
// Query USER_BALANCES to get settlement info
String balanceSql = "SELECT * FROM user_balances " +
                   "WHERE group_id = ? AND amount_owed > 0";
PreparedStatement stmt = conn.prepareStatement(balanceSql);
stmt.setString(1, "G1001");
ResultSet rs = stmt.executeQuery();

while (rs.next()) {
    String payer = rs.getString("user_id_1");        // U2001
    String receiver = rs.getString("user_id_2");     // U1001
    double amount = rs.getDouble("amount_owed");     // 300.0
    // Result: U2001 owes U1001 ₹300
}
```

---

## Transaction Management

### Atomicity in Saving Expense

When saving an expense, we use transactions to ensure **all-or-nothing** semantics:

```java
conn.setAutoCommit(false);  // START TRANSACTION

try {
    // Operation 1: Insert in EXPENSES
    expenseStmt.executeUpdate();
    
    // Operation 2: Insert 3 rows in EXPENSE_SPLITS
    splitStmt.executeBatch();
    
    // All succeeded → COMMIT
    conn.commit();
} catch (SQLException e) {
    // Any operation failed → ROLLBACK
    conn.rollback();
}

conn.setAutoCommit(true);  // Resume auto-commit
```

**Why:** If EXPENSES inserts successfully but EXPENSE_SPLITS fails, we don't want orphaned expense records.

---

## Question 1 Answer: Direct Expenses (No Group)

For 2-3 people settling directly without a formal group:

### Option 1: groupId = NULL
```sql
-- In EXPENSES table
INSERT INTO expenses (expense_id, group_id, paid_by_user_id, amount)
VALUES ('Direct001', NULL, 'U1001', 500);
```

### Option 2: Create Virtual Group
```sql
-- Create an implicit group
INSERT INTO groups (group_id, group_name, created_by)
VALUES ('DIRECT_U1001_U2001', 'Direct Settlement', 'U1001');

-- Add to expenses with this group
INSERT INTO expenses (expense_id, group_id, ...)
VALUES ('Direct001', 'DIRECT_U1001_U2001', ...);
```

In repository:
```java
public void saveDirectExpense(Expense expense) {
    // Save with groupId = null
    save(expense, null);  // groupId is NULL
}

public List<Expense> findDirectExpenses(String userId) {
    String sql = "SELECT * FROM expenses WHERE group_id IS NULL " +
                 "AND (paid_by_user_id = ? OR user_id IN (" +
                 "  SELECT user_id FROM expense_splits WHERE expense_id = ?))";
    // Query for user's direct expenses
}
```

---

## Summary: How Data Flows

```
JAVA WORLD                  MAPPING LAYER              DATABASE WORLD
────────────────────────────────────────────────────────────────────

Expense object              ExpenseRepository          EXPENSES table
├─ expenseId        ───────────► save() ─────────────► expense_id
├─ description      ───────────► (convert)───────────► description
├─ amount           ───────────►              ────────► amount
├─ paidByUser       ───────────► (extract ID)────────► paid_by_user_id
├─ splitDetails[]   ───────────►              
│  └─ Split[]                   └─► save() ─────────► EXPENSE_SPLITS
│                                   (batch)          split_id
│                                               ────► expense_id
│                                               ────► user_id
│                                               ────► amount_owed

FETCH REVERSE:

EXPENSES table             ExpenseRepository           Expense object
├─ expense_id    ◄────────── findById() ◄───────────── expenseId
├─ description   ◄────────── (reconstruct)◄───────── description
├─ amount        ◄────────── (map data)  ◄───────── amount
├─ paid_by_user_id◄────────► fetch User  ◄───────── paidByUser
└─ group_id      ◄──────────                         

EXPENSE_SPLITS table       ExpenseRepository          List<Split>
├─ split_id      ◄────────── findById() ◄───────────► Split[0]
├─ user_id       ◄────────── (reconstruct)◄────────► Split[1]
└─ amount_owed   ◄────────── (map data)  ◄────────► Split[2]
```

---

## Benefits of Repository Pattern

1. **Abstraction**: Business logic doesn't know about SQL
2. **Testability**: Easy to mock repositories in tests
3. **Maintainability**: All database code in one place
4. **Reusability**: Share repositories across services
5. **Performance**: Can optimize queries without changing business logic
6. **Scalability**: Easy to switch databases (MySQL, PostgreSQL, etc.)

