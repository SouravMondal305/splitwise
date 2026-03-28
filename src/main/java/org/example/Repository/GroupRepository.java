package org.example.Repository;

import org.example.Group.Group;
import org.example.User.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * GroupRepository - Data Access Object for Group entity
 * Handles all database operations for Groups and Group_Members tables
 */
public class GroupRepository {
    
    /**
     * Save a group to database
     * Flow: Group object → SQL INSERT → Database
     */
    public void save(Group group, String createdById) {
        String sql = "INSERT INTO groups (group_id, group_name, created_by) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, group.getGroupId());
            stmt.setString(2, group.getGroupName() != null ? group.getGroupName() : "");
            stmt.setString(3, createdById);
            
            stmt.executeUpdate();
            System.out.println("✅ Group saved: " + group.getGroupId());
        } catch (SQLException e) {
            System.err.println("❌ Error saving group: " + e.getMessage());
            throw new RuntimeException("Failed to save group", e);
        }
    }
    
    /**
     * Add member to a group
     * Flow: Group member addition → SQL INSERT → Database
     */
    public void addMember(String groupId, String userId) {
        String sql = "INSERT INTO group_members (group_id, user_id) VALUES (?, ?)";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, groupId);
            stmt.setString(2, userId);
            
            stmt.executeUpdate();
            System.out.println("✅ Member added to group: " + userId + " → " + groupId);
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE")) {
                System.out.println("ℹ️  Member already in group");
            } else {
                System.err.println("❌ Error adding member: " + e.getMessage());
                throw new RuntimeException("Failed to add member", e);
            }
        }
    }
    
    /**
     * Fetch a group by ID
     * Flow: Database → SQL SELECT → Group object
     */
    public Group findById(String groupId) {
        String sql = "SELECT * FROM groups WHERE group_id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, groupId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                Group group = new Group();
                group.setGroupId(rs.getString("group_id"));
                group.setGroupName(rs.getString("group_name"));
                System.out.println("✅ Group fetched: " + groupId);
                return group;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error fetching group: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get all members of a group from database
     */
    public List<User> getGroupMembers(String groupId, UserRepository userRepo) {
        String sql = "SELECT user_id FROM group_members WHERE group_id = ?";
        List<User> members = new ArrayList<>();
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, groupId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String userId = rs.getString("user_id");
                User user = userRepo.findById(userId);
                if (user != null) {
                    members.add(user);
                }
            }
            System.out.println("✅ Fetched " + members.size() + " members for group: " + groupId);
        } catch (SQLException e) {
            System.err.println("❌ Error fetching group members: " + e.getMessage());
        }
        
        return members;
    }
}
