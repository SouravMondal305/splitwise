package org.example.Repository;

import java.sql.*;

/**
 * Database initialization and configuration
 * Creates H2 in-memory database and initializes schema
 */
public class DatabaseConfig {
    
    private static final String DB_URL = "jdbc:h2:mem:splitwise;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    private static final String DB_DRIVER = "org.h2.Driver";
    
    static {
        try {
            Class.forName(DB_DRIVER);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("H2 Driver not found", e);
        }
    }
    
    /**
     * Get database connection
     * @return Connection object
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, "sa", "");
    }
    
    /**
     * Initialize database schema
     */
    public static void initializeDatabase() {
        try (Connection conn = getConnection()) {
            createTables(conn);
            // Database initialized silently
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
    
    private static void createTables(Connection conn) throws SQLException {
        // Create USERS table
        conn.createStatement().execute(
            "CREATE TABLE IF NOT EXISTS users (" +
            "    user_id VARCHAR(50) PRIMARY KEY," +
            "    user_name VARCHAR(100) NOT NULL UNIQUE," +
            "    email VARCHAR(100) UNIQUE," +
            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")"
        );
        
        // Create GROUPS table
        conn.createStatement().execute(
            "CREATE TABLE IF NOT EXISTS groups (" +
            "    group_id VARCHAR(50) PRIMARY KEY," +
            "    group_name VARCHAR(100) NOT NULL," +
            "    created_by VARCHAR(50) NOT NULL," +
            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    FOREIGN KEY (created_by) REFERENCES users(user_id)" +
            ")"
        );
        
        // Create GROUP_MEMBERS table
        conn.createStatement().execute(
            "CREATE TABLE IF NOT EXISTS group_members (" +
            "    group_member_id INT PRIMARY KEY AUTO_INCREMENT," +
            "    group_id VARCHAR(50) NOT NULL," +
            "    user_id VARCHAR(50) NOT NULL," +
            "    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    UNIQUE (group_id, user_id)," +
            "    FOREIGN KEY (group_id) REFERENCES groups(group_id)," +
            "    FOREIGN KEY (user_id) REFERENCES users(user_id)" +
            ")"
        );
        
        // Create EXPENSES table
        // group_id can be NULL for direct peer-to-peer expenses
        conn.createStatement().execute(
            "CREATE TABLE IF NOT EXISTS expenses (" +
            "    expense_id VARCHAR(50) PRIMARY KEY," +
            "    group_id VARCHAR(50)," +
            "    paid_by_user_id VARCHAR(50) NOT NULL," +
            "    description VARCHAR(255) NOT NULL," +
            "    amount DECIMAL(10,2) NOT NULL," +
            "    expense_type VARCHAR(50) DEFAULT 'GROUP'," +  // GROUP or DIRECT
            "    split_type VARCHAR(50) NOT NULL," +
            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    FOREIGN KEY (group_id) REFERENCES groups(group_id)," +
            "    FOREIGN KEY (paid_by_user_id) REFERENCES users(user_id)," +
            "    CHECK ((group_id IS NOT NULL AND expense_type = 'GROUP') OR " +
            "           (group_id IS NULL AND expense_type = 'DIRECT'))" +
            ")"
        );
        
        // Create EXPENSE_SPLITS table
        conn.createStatement().execute(
            "CREATE TABLE IF NOT EXISTS expense_splits (" +
            "    split_id INT PRIMARY KEY AUTO_INCREMENT," +
            "    expense_id VARCHAR(50) NOT NULL," +
            "    user_id VARCHAR(50) NOT NULL," +
            "    amount_owed DECIMAL(10,2) NOT NULL," +
            "    UNIQUE (expense_id, user_id)," +
            "    FOREIGN KEY (expense_id) REFERENCES expenses(expense_id)," +
            "    FOREIGN KEY (user_id) REFERENCES users(user_id)" +
            ")"
        );
        
        // Create USER_BALANCES table
        conn.createStatement().execute(
            "CREATE TABLE IF NOT EXISTS user_balances (" +
            "    balance_id INT PRIMARY KEY AUTO_INCREMENT," +
            "    group_id VARCHAR(50)," +
            "    user_id_1 VARCHAR(50) NOT NULL," +
            "    user_id_2 VARCHAR(50) NOT NULL," +
            "    amount_owed DECIMAL(10,2) NOT NULL DEFAULT 0," +
            "    amount_to_receive DECIMAL(10,2) NOT NULL DEFAULT 0," +
            "    UNIQUE (group_id, user_id_1, user_id_2)," +
            "    FOREIGN KEY (group_id) REFERENCES groups(group_id)," +
            "    FOREIGN KEY (user_id_1) REFERENCES users(user_id)," +
            "    FOREIGN KEY (user_id_2) REFERENCES users(user_id)" +
            ")"
        );
        
        // Create PAYMENTS table
        conn.createStatement().execute(
            "CREATE TABLE IF NOT EXISTS payments (" +
            "    payment_id INT PRIMARY KEY AUTO_INCREMENT," +
            "    payer_id VARCHAR(50) NOT NULL," +
            "    receiver_id VARCHAR(50) NOT NULL," +
            "    group_id VARCHAR(50)," +
            "    amount DECIMAL(10,2) NOT NULL," +
            "    status VARCHAR(50) DEFAULT 'PENDING'," +
            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    FOREIGN KEY (payer_id) REFERENCES users(user_id)," +
            "    FOREIGN KEY (receiver_id) REFERENCES users(user_id)," +
            "    FOREIGN KEY (group_id) REFERENCES groups(group_id)" +
            ")"
        );
    }
}
