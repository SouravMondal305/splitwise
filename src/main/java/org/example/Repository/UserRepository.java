package org.example.Repository;

import org.example.User.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * UserRepository - Data Access Object for User entity
 * Handles all database operations for Users table
 */
public class UserRepository {
    
    /**
     * Save a user to the database
     * Flow: User object → SQL INSERT → Database
     */
    public void save(User user) {
        String sql = "INSERT INTO users (user_id, user_name, email) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, user.getUserId());
            stmt.setString(2, user.getUserName());
            stmt.setString(3, null);  // email not stored in User class currently
            
            int rowsInserted = stmt.executeUpdate();
            
            if (rowsInserted > 0) {
                // User saved silently
            }
        } catch (SQLException e) {
            System.err.println("❌ Error saving user: " + e.getMessage());
            throw new RuntimeException("Failed to save user", e);
        }
    }
    
    /**
     * Fetch a user by ID from database
     * Flow: Database → SQL SELECT → User object
     */
    public User findById(String userId) {
        String sql = "SELECT user_id, user_name FROM users WHERE user_id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                User user = new User(rs.getString("user_id"), rs.getString("user_name"));
                // User fetched silently
                return user;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error fetching user: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Fetch all users from database
     */
    public List<User> findAll() {
        String sql = "SELECT user_id, user_name FROM users ORDER BY created_at";
        List<User> users = new ArrayList<>();
        
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                User user = new User(rs.getString("user_id"), rs.getString("user_name"));
                users.add(user);
            }
            System.out.println("✅ All users fetched: " + users.size() + " users");
        } catch (SQLException e) {
            System.err.println("❌ Error fetching all users: " + e.getMessage());
        }
        
        return users;
    }
    
    /**
     * Check if user exists in database
     */
    public boolean exists(String userId) {
        String sql = "SELECT 1 FROM users WHERE user_id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("❌ Error checking user existence: " + e.getMessage());
        }
        
        return false;
    }
}
