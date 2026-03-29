# Splitwise - Low-Level Design (LLD)

## Overview

**Splitwise** is a Java-based expense-sharing application that helps groups and individuals split expenses and manage debts. The system supports multiple ways of splitting expenses (Equal, Percentage, Unequal) and intelligently simplifies circular debts to minimize the number of transactions needed to settle all dues.

### Key Features
- 👥 **User Management**: Create and manage users
- 👫 **Group Expenses**: Split expenses among group members with multiple split strategies
- 🤝 **Direct Expenses**: Peer-to-peer expense settlements between individuals
- 💰 **Balance Tracking**: Real-time balance sheets for users and groups
- 🔄 **Debt Simplification**: Automatically detect and eliminate circular debts to reduce transactions
- 💾 **Database Persistence**: H2 in-memory database for data storage
- 📊 **Comprehensive Reporting**: Detailed balance summaries and transaction reports

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Java 16 |
| **Build Tool** | Maven |
| **Database** | H2 (In-Memory) |
| **Architecture Pattern** | Model-View-Controller (MVC) with Service Layer |
| **Design Patterns** | Singleton, Factory, Strategy, Repository |

---

## Architecture Overview

### High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                          APPLICATION LAYER                      │
│                                                                  │
│  ┌──────────────────┐         ┌──────────────────┐              │
│  │   Main / CLI     │         │  Splitwise       │              │
│  │   Interface      │────────▶│  (Singleton)     │              │
│  └──────────────────┘         └──────────────────┘              │
│                                         │                        │
├─────────────────────────────────────────┼────────────────────────┤
│                     CONTROLLER LAYER (Services)                  │
│                                         │                        │
│  ┌────────────────────────────────────────────────────┐          │
│  │  • BalanceSheetController  (Balance Calculation)  │          │
│  │  • ExpenseController       (Expense Creation)     │          │
│  │  • GroupController         (Group Management)     │          │
│  │  • UserController          (User Management)      │          │
│  │  • DirectExpenseService    (P2P Expenses)        │          │
│  │  • DebtSimplificationService (Cycle Detection)   │          │
│  └────────────────────────────────────────────────────┘          │
│                                         │                        │
├─────────────────────────────────────────┼────────────────────────┤
│                      MODEL LAYER (Domain Entities)               │
│                                         │                        │
│  ┌──────────┐  ┌────────┐  ┌────────┐  ┌──────────┐             │
│  │  User    │  │ Group  │  │Expense │  │ Payment  │             │
│  └──────────┘  └────────┘  └────────┘  └──────────┘             │
│       │            │           │             │                   │
│  ┌────┴────┐       │           └─────────────┘                   │
│  │ Balance │       │                                              │
│  └─────────┘       │                                              │
│                    └─────────────┬──────────────┐                │
│                                  │              │                │
│                           ┌──────┴───────┐   ┌──┴────────┐      │
│                           │   Split      │   │ Strategies │     │
│                           └──────────────┘   └───────────┘      │
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│                    REPOSITORY LAYER (Data Access)                │
│                                                                  │
│  ┌─────────────────────────────────────────────┐                │
│  │  • UserRepository                           │                │
│  │  • GroupRepository                          │                │
│  │  • ExpenseRepository                        │                │
│  │  • DatabaseConfig (H2 Connection)           │                │
│  └─────────────────────────────────────────────┘                │
│                           │                                      │
├───────────────────────────┼──────────────────────────────────────┤
│                           ▼                                       │
│              ┌──────────────────────┐                            │
│              │   H2 In-Memory DB    │                            │
│              │   (Data Persistence) │                            │
│              └──────────────────────┘                            │
└──────────────────────────────────────────────────────────────────┘
```

---

## Component Responsibilities

### 1. **Entity Layer** (Domain Models)

#### User
- Represents an individual user in the system
- Contains user ID, name, and personal expense balance sheet
- Each user maintains their own `UserExpenseBalanceSheet`

#### Group
- Represents a collection of users sharing expenses
- Contains list of members and expenses
- Manages group-level expense creation

#### Expense
- Represents a transaction between users
- Can be either `GROUP` (group context) or `DIRECT` (peer-to-peer)
- Contains split details and payer information
- Stores expense type, split type, and description

#### Balance
- Tracks amount owed and amount to receive between user pairs
- Part of the balance sheet calculation

#### Split
- Represents individual portion of an expense
- Contains user and their share amount
- Used by various split strategies

#### Payment
- Represents a simplified transaction (final settlement)
- Contains payer, receiver, and amount
- Used in debt simplification output

### 2. **Controller/Service Layer**

#### BalanceSheetController
- **Responsibility**: Update user balance sheets when expenses are added
- **Logic**:
  1. Track who paid the total expense amount
  2. For each split, update the balance between payer and participant
  3. Calculate running totals (owe, get back, total paid)

#### ExpenseController
- **Responsibility**: Create expenses with proper split validation
- Uses Strategy Pattern to select appropriate split calculation method

#### GroupController
- **Responsibility**: Manage group creation and retrieval
- Maintains registry of all groups

#### UserController
- **Responsibility**: Manage user creation and retrieval
- Maintains registry of all users

#### DirectExpenseService
- **Responsibility**: Manage peer-to-peer expenses
- Validates that direct expenses involve 2-3 people only
- Updates balance sheets without group context

#### DebtSimplificationService
- **Responsibility**: Detect and eliminate circular debts
- Uses DFS (Depth-First Search) to find cycles
- Reduces debt amounts along cycles by minimum value
- Returns simplified transaction list

### 3. **Repository Layer** (Data Access)

- **Purpose**: Abstract database operations
- **Components**:
  - `UserRepository`: CRUD operations for users
  - `GroupRepository`: CRUD operations for groups
  - `ExpenseRepository`: CRUD operations for expenses
  - `DatabaseConfig`: H2 database initialization

### 4. **Splitwise Singleton**
- **Central coordinator** managing all controllers and repositories
- Implements **Singleton Pattern** with double-checked locking
- Ensures single instance of application throughout runtime
- Runs both end-to-end demo and debt simplification demo

---

## Key Workflows

### Workflow 1: Creating and Settling Group Expenses

```
1. Create Group
   └─→ Add Members

2. Create Expense
   ├─→ Select Payer (who paid)
   ├─→ Enter Amount
   ├─→ Define Splits (how to divide)
   └─→ Call ExpenseController.createExpense()

3. Update Balance Sheets
   ├─→ Calculate payer's return amount
   ├─→ For each participant:
   │   ├─→ Update their "Amount Owe"
   │   └─→ Update payer's "Amount Get Back"
   └─→ Store pairwise balances

4. View Balance Summary
   └─→ Show who owes whom and how much
```

### Workflow 2: Direct Peer-to-Peer Expenses

```
1. Create Direct Expense
   ├─→ Validate participants (2-3 people)
   ├─→ Validate split amounts match total
   └─→ Create Expense with ExpenseType.DIRECT

2. Update Balance Sheets
   └─→ Same as group expenses (no group context)

3. Create Payment Records
   └─→ Generate settlement transactions
```

### Workflow 3: Debt Simplification

```
1. Collect All Balances
   └─→ Gather all pairwise debts

2. Convert to Debt Graph
   └─→ Create directed graph: A → B: amount

3. Find Cycles (DFS)
   ├─→ Start from each node
   ├─→ Follow debt path until reaching start node
   └─→ If cycle found, mark it

4. Eliminate Cycle
   ├─→ Find minimum debt in cycle
   ├─→ Reduce all debts by minimum
   └─→ Remove zero-debt edges

5. Repeat Until No Cycles
   └─→ Continue until no more cycles found

6. Convert Back to Payments
   └─→ Output simplified settlement list
```

---

## Design Patterns Used

| Pattern | Usage | Benefit |
|---------|-------|---------|
| **Singleton** | Splitwise class | Single entry point, controlled initialization |
| **Strategy** | ExpenseSplitStrategy, split types | Flexible split calculation algorithms |
| **Factory** | Creating expenses, users, groups | Centralized object creation |
| **Repository** | Data access layer | Decouple business logic from DB operations |
| **MVC** | Overall architecture | Separation of concerns |

---

## Split Strategies

### 1. Equal Split
- Divides expense equally among all participants
- Formula: `amount per person = total_amount / number_of_people`

### 2. Unequal Split
- Allows specifying exact amount for each participant
- Each participant gets a custom amount

### 3. Percentage Split
- Divides expense by percentage
- Formula: `amount = total_amount * (percentage / 100)`

---

## Debt Simplification Algorithm

### Problem Statement
When multiple people have complex debts, there can be circular payments that could be simplified or eliminated.

**Example:**
```
A owes B: 300
B owes C: 300  
C owes A: 300
→ All can settle with 0 transactions (circular debt)
```

### Algorithm Steps

1. **Build Debt Graph**
   - Convert all payment transactions into directed graph
   - Node = User, Edge = Debt

2. **Find Cycles Using DFS**
   - For each starting node, traverse following debt paths
   - If path returns to starting node, a cycle is found

3. **Eliminate Cycle**
   - Find minimum debt value in the cycle
   - Subtract minimum from all debts in cycle
   - Remove zero-debt edges

4. **Repeat**
   - Continue finding and eliminating cycles until none remain

5. **Return Simplified List**
   - Convert remaining graph back to payment list

### Time Complexity
- Building graph: **O(n)** where n = number of transactions
- Finding cycles: **O(V + E)** where V = users, E = debts
- Overall: **O(n + V + E)**

### Space Complexity
- **O(V + E)** for the debt graph

---

## How to Run

### Prerequisites
- Java 16+
- Maven

### Build
```bash
mvn clean compile
```

### Run
```bash
mvn exec:java -Dexec.mainClass="org.example.Main"
```

### Output
The application will display:
1. ✅ User registration
2. ✅ Group expense creation
3. ✅ Direct expense creation
4. 📊 Balance summaries
5. 🔄 Debt simplification before/after
6. 📈 Simplification statistics

---

## Example Scenario

### Setup
- Users: Alice, Bob, Charlie, Diana, Eve
- Group: "Europe Trip 2026"

### Transactions
1. **Alice pays ₹3000** for hotel (split equally 3 ways)
   - Alice: 0, Bob: -1000, Charlie: -1000

2. **Bob pays ₹1200** for food (split equally 3 ways)
   - Alice: +400, Bob: 0, Charlie: +400

3. **Charlie pays ₹1700** for tours (unequal split)
   - Alice: +500, Bob: +600, Charlie: 0

### Final Debts (Before Simplification)
- Bob → Alice: 600
- Bob → Charlie: 1000
- Charlie → Bob: 600

### After Simplification
- Bob → Charlie: 400 (circular debt eliminated)

---

## File Structure

```
splitwise/
├── pom.xml
└── src/main/java/org/example/
    ├── Main.java
    ├── Splitwise.java
    ├── User/
    │   ├── User.java
    │   └── UserController.java
    ├── Group/
    │   └── Group.java
    ├── Expense/
    │   ├── Expense.java
    │   ├── ExpenseType.java
    │   └── ExpenseSplitType.java
    ├── Balance/
    │   ├── Balance.java
    │   └── UserExpenseBalanceSheet.java
    ├── Split/
    │   ├── Split.java
    │   ├── ExpenseSplitStrategy.java
    │   └── SplitStrategies/
    │       ├── EqualExpenseSplit.java
    │       ├── PercentageExpenseSplit.java
    │       └── UnequalExpenseSplit.java
    ├── Payment/
    │   └── Payment.java
    ├── Controllers/
    │   ├── BalanceSheetController.java
    │   ├── ExpenseController.java
    │   ├── GroupController.java
    │   ├── DirectExpenseService.java
    │   └── DebtSimplificationService.java
    └── Repository/
        ├── DatabaseConfig.java
        ├── UserRepository.java
        ├── GroupRepository.java
        └── ExpenseRepository.java
```

---

## Future Enhancements

- [ ] REST API using Spring Boot
- [ ] User authentication and authorization
- [ ] Payment settlement tracking
- [ ] Generate PDF reports
- [ ] Mobile app support
- [ ] Real-time notifications
- [ ] Currency conversion support
- [ ] Advanced debt optimization algorithms

---

## Contributors

Project created as a comprehensive LLD exercise for learning OOP, design patterns, and system design principles.

