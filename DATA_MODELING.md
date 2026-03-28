# Data Modeling - Splitwise Database Schema

This document outlines the database schema design for the Splitwise application with tables, columns, relationships, and constraints.

## Overview

The Splitwise database follows a relational model with normalized tables to efficiently manage users, groups, expenses, and payments.

## Entity Relationship Diagram (ERD)

```
┌─────────────────┐
│     USERS       │
├─────────────────┤
│ user_id (PK)    │
│ user_name       │
│ email           │
│ created_at      │
└────────┬────────┘
         │
         ├─────────────────┬──────────────────┬──────────────────┐
         │                 │                  │                  │
         │        ┌────────▼─────────┐  ┌────▼──────────────┐  │
         │        │      GROUPS      │  │  GROUP_MEMBERS    │  │
         │        ├─────────────────┤  ├──────────────────┤  │
         │        │ group_id (PK)   │  │ group_member_id   │  │
         │        │ group_name      │◄─┤ group_id (FK)    │  │
         │        │ created_by (FK) │  │ user_id (FK)     │  │
         │        │ created_at      │  │ joined_at        │  │
         │        └────────┬────────┘  └──────────────────┘  │
         │                 │                                   │
         │        ┌────────▼──────────────┐                   │
         │        │     EXPENSES         │                   │
         │        ├──────────────────────┤                   │
         │        │ expense_id (PK)      │                   │
         │        │ group_id (FK)        │                   │
         │        │ paid_by_user_id (FK) ├───────────────────┘
         │        │ description          │
         │        │ amount               │
         │        │ split_type           │
         │        │ created_at           │
         │        └────────┬─────────────┘
         │                 │
         │        ┌────────▼──────────────┐
         │        │ EXPENSE_SPLITS       │
         │        ├──────────────────────┤
         │        │ split_id (PK)        │
         │        │ expense_id (FK)      │
         │        │ user_id (FK)        ├─────────────┐
         │        │ amount_owed          │             │
         │        │ split_percentage     │             │
         │        └──────────────────────┘             │
         │                                              │
         └──────────────────────────────────────────────┘
         │
    ┌────▼────────────────┐
    │ USER_BALANCES       │
    ├─────────────────────┤
    │ balance_id (PK)     │
    │ user_id_1 (FK)      │
    │ user_id_2 (FK)      │
    │ group_id (FK)       │
    │ amount_owed         │
    │ amount_to_receive   │
    │ last_updated        │
    └─────────────────────┘

    ┌─────────────────────┐
    │ PAYMENTS            │
    ├─────────────────────┤
    │ payment_id (PK)     │
    │ payer_id (FK)       │
    │ receiver_id (FK)    │
    │ group_id (FK)       │
    │ amount              │
    │ status              │
    │ created_at          │
    │ settled_at          │
    └─────────────────────┘
```

## Table Definitions

### 1. USERS
Stores user information in the system.

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `user_id` | VARCHAR(50) | PRIMARY KEY, NOT NULL | Unique identifier for user (e.g., U1001) |
| `user_name` | VARCHAR(100) | NOT NULL, UNIQUE | User's display name |
| `email` | VARCHAR(100) | UNIQUE | User's email address |
| `phone` | VARCHAR(20) | | User's phone number |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Account creation timestamp |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Last update timestamp |

**Indexes:**
- PRIMARY KEY: `user_id`
- UNIQUE: `user_name`, `email`

**SQL:**
```sql
CREATE TABLE users (
    user_id VARCHAR(50) PRIMARY KEY,
    user_name VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

---

### 2. GROUPS
Stores group information.

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `group_id` | VARCHAR(50) | PRIMARY KEY, NOT NULL | Unique identifier for group (e.g., G1001) |
| `group_name` | VARCHAR(100) | NOT NULL | Name of the group |
| `description` | TEXT | | Group description |
| `created_by` | VARCHAR(50) | FOREIGN KEY (users) | User who created the group |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Group creation timestamp |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Last update timestamp |
| `is_active` | BOOLEAN | DEFAULT TRUE | Whether group is active |

**Indexes:**
- PRIMARY KEY: `group_id`
- FOREIGN KEY: `created_by` → `users.user_id`

**SQL:**
```sql
CREATE TABLE groups (
    group_id VARCHAR(50) PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL,
    description TEXT,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);
```

---

### 3. GROUP_MEMBERS
Stores group membership information.

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `group_member_id` | INT | PRIMARY KEY, AUTO_INCREMENT | Unique identifier for group member record |
| `group_id` | VARCHAR(50) | FOREIGN KEY (groups), NOT NULL | Reference to group |
| `user_id` | VARCHAR(50) | FOREIGN KEY (users), NOT NULL | Reference to user |
| `joined_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | When user joined the group |
| `is_active` | BOOLEAN | DEFAULT TRUE | Whether member is active in group |

**Indexes:**
- PRIMARY KEY: `group_member_id`
- FOREIGN KEY: `group_id` → `groups.group_id`
- FOREIGN KEY: `user_id` → `users.user_id`
- UNIQUE: (`group_id`, `user_id`)

**SQL:**
```sql
CREATE TABLE group_members (
    group_member_id INT PRIMARY KEY AUTO_INCREMENT,
    group_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (group_id) REFERENCES groups(group_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    UNIQUE KEY unique_group_member (group_id, user_id)
);
```

---

### 4. EXPENSES
Stores expense information.

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `expense_id` | VARCHAR(50) | PRIMARY KEY, NOT NULL | Unique identifier for expense (e.g., Exp1001) |
| `group_id` | VARCHAR(50) | FOREIGN KEY (groups), NOT NULL | Reference to group |
| `paid_by_user_id` | VARCHAR(50) | FOREIGN KEY (users), NOT NULL | User who paid the expense |
| `description` | VARCHAR(255) | NOT NULL | Description of expense |
| `amount` | DECIMAL(10,2) | NOT NULL, CHECK (amount > 0) | Total expense amount |
| `split_type` | ENUM('EQUAL', 'UNEQUAL', 'PERCENTAGE') | NOT NULL | Type of split |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | When expense was created |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Last update timestamp |

**Indexes:**
- PRIMARY KEY: `expense_id`
- FOREIGN KEY: `group_id` → `groups.group_id`
- FOREIGN KEY: `paid_by_user_id` → `users.user_id`
- INDEX: `group_id`, `created_at`

**SQL:**
```sql
CREATE TABLE expenses (
    expense_id VARCHAR(50) PRIMARY KEY,
    group_id VARCHAR(50) NOT NULL,
    paid_by_user_id VARCHAR(50) NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    split_type ENUM('EQUAL', 'UNEQUAL', 'PERCENTAGE') NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES groups(group_id),
    FOREIGN KEY (paid_by_user_id) REFERENCES users(user_id),
    INDEX idx_group_created (group_id, created_at)
);
```

---

### 5. EXPENSE_SPLITS
Stores individual split details for each expense.

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `split_id` | INT | PRIMARY KEY, AUTO_INCREMENT | Unique identifier for split |
| `expense_id` | VARCHAR(50) | FOREIGN KEY (expenses), NOT NULL | Reference to expense |
| `user_id` | VARCHAR(50) | FOREIGN KEY (users), NOT NULL | User for this split |
| `amount_owed` | DECIMAL(10,2) | NOT NULL, CHECK (amount_owed >= 0) | Amount this user owes |
| `split_percentage` | DECIMAL(5,2) | CHECK (split_percentage >= 0 AND split_percentage <= 100) | Percentage split (if applicable) |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |

**Indexes:**
- PRIMARY KEY: `split_id`
- FOREIGN KEY: `expense_id` → `expenses.expense_id`
- FOREIGN KEY: `user_id` → `users.user_id`
- UNIQUE: (`expense_id`, `user_id`)

**SQL:**
```sql
CREATE TABLE expense_splits (
    split_id INT PRIMARY KEY AUTO_INCREMENT,
    expense_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    amount_owed DECIMAL(10,2) NOT NULL CHECK (amount_owed >= 0),
    split_percentage DECIMAL(5,2) CHECK (split_percentage >= 0 AND split_percentage <= 100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (expense_id) REFERENCES expenses(expense_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    UNIQUE KEY unique_expense_split (expense_id, user_id)
);
```

---

### 6. USER_BALANCES
Stores calculated balance between pairs of users (denormalized for performance).

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `balance_id` | INT | PRIMARY KEY, AUTO_INCREMENT | Unique identifier |
| `user_id_1` | VARCHAR(50) | FOREIGN KEY (users), NOT NULL | First user |
| `user_id_2` | VARCHAR(50) | FOREIGN KEY (users), NOT NULL | Second user |
| `group_id` | VARCHAR(50) | FOREIGN KEY (groups), NOT NULL | Reference to group |
| `amount_owed` | DECIMAL(10,2) | NOT NULL, DEFAULT 0 | Amount user_id_1 owes to user_id_2 |
| `amount_to_receive` | DECIMAL(10,2) | NOT NULL, DEFAULT 0 | Amount user_id_1 will receive from user_id_2 |
| `last_updated` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Last calculation timestamp |

**Indexes:**
- PRIMARY KEY: `balance_id`
- FOREIGN KEY: `user_id_1`, `user_id_2` → `users.user_id`
- FOREIGN KEY: `group_id` → `groups.group_id`
- UNIQUE: (`group_id`, `user_id_1`, `user_id_2`)

**SQL:**
```sql
CREATE TABLE user_balances (
    balance_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id_1 VARCHAR(50) NOT NULL,
    user_id_2 VARCHAR(50) NOT NULL,
    group_id VARCHAR(50) NOT NULL,
    amount_owed DECIMAL(10,2) NOT NULL DEFAULT 0,
    amount_to_receive DECIMAL(10,2) NOT NULL DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id_1) REFERENCES users(user_id),
    FOREIGN KEY (user_id_2) REFERENCES users(user_id),
    FOREIGN KEY (group_id) REFERENCES groups(group_id),
    UNIQUE KEY unique_group_user_pair (group_id, user_id_1, user_id_2)
);
```

---

### 7. PAYMENTS
Stores payment settlement transactions.

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `payment_id` | INT | PRIMARY KEY, AUTO_INCREMENT | Unique identifier |
| `payer_id` | VARCHAR(50) | FOREIGN KEY (users), NOT NULL | User making payment |
| `receiver_id` | VARCHAR(50) | FOREIGN KEY (users), NOT NULL | User receiving payment |
| `group_id` | VARCHAR(50) | FOREIGN KEY (groups), NOT NULL | Reference to group |
| `amount` | DECIMAL(10,2) | NOT NULL, CHECK (amount > 0) | Payment amount |
| `status` | ENUM('PENDING', 'COMPLETED', 'CANCELLED') | DEFAULT 'PENDING' | Payment status |
| `description` | VARCHAR(255) | | Payment description |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | When payment was recorded |
| `settled_at` | TIMESTAMP | | When payment was settled |

**Indexes:**
- PRIMARY KEY: `payment_id`
- FOREIGN KEY: `payer_id`, `receiver_id` → `users.user_id`
- FOREIGN KEY: `group_id` → `groups.group_id`
- INDEX: `status`, `created_at`

**SQL:**
```sql
CREATE TABLE payments (
    payment_id INT PRIMARY KEY AUTO_INCREMENT,
    payer_id VARCHAR(50) NOT NULL,
    receiver_id VARCHAR(50) NOT NULL,
    group_id VARCHAR(50) NOT NULL,
    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    status ENUM('PENDING', 'COMPLETED', 'CANCELLED') DEFAULT 'PENDING',
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    settled_at TIMESTAMP NULL,
    FOREIGN KEY (payer_id) REFERENCES users(user_id),
    FOREIGN KEY (receiver_id) REFERENCES users(user_id),
    FOREIGN KEY (group_id) REFERENCES groups(group_id),
    INDEX idx_status_created (status, created_at)
);
```

---

## Relationships

### 1. USERS → GROUPS (One-to-Many)
- One user can create multiple groups
- Relationship: `USERS.user_id` → `GROUPS.created_by`

### 2. GROUPS ↔ USERS (Many-to-Many)
- Groups have many members, users belong to many groups
- Relationship: `GROUP_MEMBERS` junction table
- `GROUP_MEMBERS.group_id` → `GROUPS.group_id`
- `GROUP_MEMBERS.user_id` → `USERS.user_id`

### 3. GROUPS → EXPENSES (One-to-Many)
- One group has many expenses
- Relationship: `EXPENSES.group_id` → `GROUPS.group_id`

### 4. USERS → EXPENSES (One-to-Many)
- One user can pay for multiple expenses
- Relationship: `EXPENSES.paid_by_user_id` → `USERS.user_id`

### 5. EXPENSES → EXPENSE_SPLITS (One-to-Many)
- One expense has many splits (one per participant)
- Relationship: `EXPENSE_SPLITS.expense_id` → `EXPENSES.expense_id`

### 6. USERS → EXPENSE_SPLITS (One-to-Many)
- One user has multiple splits across expenses
- Relationship: `EXPENSE_SPLITS.user_id` → `USERS.user_id`

### 7. GROUPS → USER_BALANCES (One-to-Many)
- One group has many user balances
- Relationship: `USER_BALANCES.group_id` → `GROUPS.group_id`

### 8. USERS → USER_BALANCES (Many-to-Many)
- Tracks pairwise balances between users
- Relationships: `USER_BALANCES.user_id_1`, `USER_BALANCES.user_id_2` → `USERS.user_id`

### 9. GROUPS → PAYMENTS (One-to-Many)
- One group has many payment settlements
- Relationship: `PAYMENTS.group_id` → `GROUPS.group_id`

### 10. USERS → PAYMENTS (Many-to-Many)
- Users as payers and receivers
- Relationships: `PAYMENTS.payer_id`, `PAYMENTS.receiver_id` → `USERS.user_id`

---

## Constraints and Rules

### Primary Key Constraints
- All `*_id` PRIMARY KEY fields are immutable and unique
- Auto-increment fields for junction tables

### Foreign Key Constraints
- All foreign keys reference valid primary keys
- Cascading deletes should be handled carefully (users cannot be deleted if they have active transactions)
- Cascading updates ensure referential integrity

### Check Constraints
- `expenses.amount > 0`: Expenses must have positive amounts
- `expense_splits.amount_owed >= 0`: Split amounts cannot be negative
- `expense_splits.split_percentage >= 0 AND <= 100`: Percentages must be valid
- `payments.amount > 0`: Payment amounts must be positive

### Unique Constraints
- Users have unique email and username
- Each user can join a group only once
- Each user has only one split per expense
- Each group-user pair has only one balance record
- Payment status tracking prevents duplicate entries

---

## Normalization

### First Normal Form (1NF)
✅ All attributes are atomic (no repeating groups)
✅ Each table has a primary key

### Second Normal Form (2NF)
✅ All non-key attributes depend on the entire primary key
✅ No partial dependencies

### Third Normal Form (3NF)
✅ No transitive dependencies
✅ Non-key attributes depend only on the primary key

### Denormalization Decision
- `USER_BALANCES` table is denormalized for performance
- Calculated values cached for faster queries
- Trade-off: Slightly more storage for significant query performance improvement

---

## Query Examples

### Find all expenses in a group
```sql
SELECT e.*, u.user_name 
FROM expenses e
JOIN users u ON e.paid_by_user_id = u.user_id
WHERE e.group_id = 'G1001'
ORDER BY e.created_at DESC;
```

### Get balance between two users
```sql
SELECT * FROM user_balances
WHERE group_id = 'G1001' 
AND user_id_1 = 'U1001' 
AND user_id_2 = 'U2001';
```

### Calculate who owes whom
```sql
SELECT 
    u1.user_name AS payer,
    u2.user_name AS receiver,
    ub.amount_owed AS amount
FROM user_balances ub
JOIN users u1 ON ub.user_id_1 = u1.user_id
JOIN users u2 ON ub.user_id_2 = u2.user_id
WHERE ub.group_id = 'G1001' 
AND ub.amount_owed > 0;
```

### Get pending payments
```sql
SELECT 
    u1.user_name AS payer,
    u2.user_name AS receiver,
    p.amount,
    p.created_at
FROM payments p
JOIN users u1 ON p.payer_id = u1.user_id
JOIN users u2 ON p.receiver_id = u2.user_id
WHERE p.group_id = 'G1001'
AND p.status = 'PENDING'
ORDER BY p.created_at DESC;
```

### Get expense split details
```sql
SELECT 
    e.description,
    e.amount,
    u.user_name,
    es.amount_owed,
    es.split_percentage
FROM expenses e
JOIN expense_splits es ON e.expense_id = es.expense_id
JOIN users u ON es.user_id = u.user_id
WHERE e.expense_id = 'Exp1001'
ORDER BY u.user_name;
```

---

## Indexes for Performance

### Recommended Indexes
```sql
-- Users table
CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_username ON users(user_name);

-- Groups table
CREATE INDEX idx_group_created_by ON groups(created_by);
CREATE INDEX idx_group_active ON groups(is_active);

-- Expenses table
CREATE INDEX idx_expense_group ON expenses(group_id);
CREATE INDEX idx_expense_payer ON expenses(paid_by_user_id);
CREATE INDEX idx_expense_date ON expenses(created_at);

-- Group Members table
CREATE INDEX idx_member_user ON group_members(user_id);
CREATE INDEX idx_member_active ON group_members(is_active);

-- Expense Splits table
CREATE INDEX idx_split_user ON expense_splits(user_id);

-- User Balances table
CREATE INDEX idx_balance_group ON user_balances(group_id);
CREATE INDEX idx_balance_user1 ON user_balances(user_id_1);
CREATE INDEX idx_balance_user2 ON user_balances(user_id_2);

-- Payments table
CREATE INDEX idx_payment_group ON payments(group_id);
CREATE INDEX idx_payment_payer ON payments(payer_id);
CREATE INDEX idx_payment_receiver ON payments(receiver_id);
CREATE INDEX idx_payment_status ON payments(status);
```

---

## Migration Path

### From In-Memory to Database

The current application uses in-memory storage. To migrate to a database:

1. **Phase 1**: Create database schema (scripts provided above)
2. **Phase 2**: Add data persistence layer (DAOs/Repositories)
3. **Phase 3**: Implement connection pooling
4. **Phase 4**: Add transaction management
5. **Phase 5**: Implement caching layer for performance

---

## Scalability Considerations

### Partitioning Strategy
- Partition `EXPENSES` table by `group_id` for horizontal scaling
- Partition `PAYMENTS` table by `created_at` for time-series optimization

### Archival Strategy
- Archive old expenses (> 1 year) to separate tables
- Keep recent data in main tables for faster queries
- Maintain denormalized summaries for historical analysis

### Backup Strategy
- Daily backups for transactional consistency
- Separate backup for audit trail
- Point-in-time recovery capability

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | March 28, 2026 | Initial schema design |

