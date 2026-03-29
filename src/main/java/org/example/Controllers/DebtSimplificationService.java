package org.example.Controllers;

import org.example.Payment.Payment;
import java.util.*;

/**
 * DebtSimplificationService - Simplifies circular debts
 * 
 * Problem: A owes B 300, B owes C 300, C owes A 300 (circular debt)
 * Solution: Cancel all debts (everyone is settled)
 * 
 * Algorithm: Detects cycles in debt graph and eliminates them
 * 
 * Example 1 (Simple Cycle):
 * A → B: 300, B → C: 300, C → A: 300
 * Result: All debts cancelled (NET = 0 for all)
 * 
 * Example 2 (Partial Cycle):
 * A → B: 500, B → C: 300, C → A: 300
 * Result: A → B: 200 (only remaining debt)
 */
public class DebtSimplificationService {
    
    /**
     * Simplify debts by detecting and eliminating circular debts
     * 
     * @param payments List of payment transactions (who owes whom and how much)
     * @return Simplified payment list with circular debts eliminated
     */
    public List<Payment> simplifyDebts(List<Payment> payments) {
        if (payments.isEmpty()) {
            return payments;
        }
        
        // Create a graph of debts: Map<FromUser, Map<ToUser, Amount>>
        Map<String, Map<String, Double>> debtGraph = buildDebtGraph(payments);
        
        // Keep simplifying until no more cycles are found
        boolean simplified;
        do {
            simplified = eliminateCycle(debtGraph);
        } while (simplified);
        
        // Convert back to Payment list
        return convertGraphToPayments(debtGraph, payments);
    }
    
    /**
     * Build a directed graph of debts
     * 
     * @param payments List of payment transactions
     * @return Debt graph as nested maps
     */
    private Map<String, Map<String, Double>> buildDebtGraph(List<Payment> payments) {
        Map<String, Map<String, Double>> graph = new HashMap<>();
        
        for (Payment payment : payments) {
            String payer = payment.getPayerId();
            String receiver = payment.getReceiverId();
            double amount = payment.getAmount();
            
            // Initialize if not exists
            graph.putIfAbsent(payer, new HashMap<>());
            
            // Add edge (payer → receiver with amount)
            double existingDebt = graph.get(payer).getOrDefault(receiver, 0.0);
            graph.get(payer).put(receiver, existingDebt + amount);
        }
        
        return graph;
    }
    
    /**
     * Try to find and eliminate one cycle from the debt graph
     * 
     * Uses DFS to detect cycles and eliminates them
     * 
     * @param graph Debt graph
     * @return true if a cycle was found and eliminated, false otherwise
     */
    private boolean eliminateCycle(Map<String, Map<String, Double>> graph) {
        // Try to find a cycle starting from each node
        for (String startNode : new HashSet<>(graph.keySet())) {
            List<String> path = new ArrayList<>();
            path.add(startNode);
            
            if (dfsForCycle(startNode, startNode, graph, path, new HashSet<>())) {
                // Path should end with start node (e.g., [A, B, C, A])
                if (path.size() > 2) {
                    // Cycle found, eliminate it
                    eliminateCycleFromPath(path, graph);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * DFS to find a cycle in the debt graph
     * 
     * @param current Current node
     * @param target Target node (start of cycle)
     * @param graph Debt graph
     * @param path Current path
     * @param visited Visited nodes in current path
     * @return true if cycle found
     */
    private boolean dfsForCycle(String current, String target, 
                               Map<String, Map<String, Double>> graph,
                               List<String> path, Set<String> visited) {
        
        // Get neighbors (people current person owes money to)
        Map<String, Double> neighbors = graph.getOrDefault(current, new HashMap<>());
        
        for (String neighbor : neighbors.keySet()) {
            if (neighbor.equals(target) && path.size() > 1) {
                // Found a cycle back to target
                path.add(neighbor);
                return true;
            }
            
            if (!visited.contains(neighbor)) {
                visited.add(neighbor);
                path.add(neighbor);
                
                if (dfsForCycle(neighbor, target, graph, path, visited)) {
                    return true;
                }
                
                path.remove(path.size() - 1);
                visited.remove(neighbor);
            }
        }
        
        return false;
    }
    
    /**
     * Eliminate a cycle by reducing debts along the cycle path
     * 
     * Example: A → B → C → A with amounts [300, 300, 300]
     * Minimum = 300
     * Result: All reduced by 300, all become 0
     * 
     * @param path Cycle path (includes start and end node which are same)
     * @param graph Debt graph to modify
     */
    private void eliminateCycleFromPath(List<String> path, Map<String, Map<String, Double>> graph) {
        // Find minimum debt in the cycle
        double minDebt = Double.MAX_VALUE;
        
        for (int i = 0; i < path.size() - 1; i++) {
            String from = path.get(i);
            String to = path.get(i + 1);
            
            double debt = graph.get(from).getOrDefault(to, 0.0);
            minDebt = Math.min(minDebt, debt);
        }
        
        // Reduce all debts in cycle by minimum amount
        for (int i = 0; i < path.size() - 1; i++) {
            String from = path.get(i);
            String to = path.get(i + 1);
            
            double currentDebt = graph.get(from).get(to);
            double newDebt = currentDebt - minDebt;
            
            if (newDebt < 0.01) {  // Remove if essentially 0
                graph.get(from).remove(to);
            } else {
                graph.get(from).put(to, newDebt);
            }
        }
    }
    
    /**
     * Convert debt graph back to Payment list
     * 
     * @param graph Simplified debt graph
     * @param originalPayments Original payments (for user names)
     * @return List of simplified payments
     */
    private List<Payment> convertGraphToPayments(Map<String, Map<String, Double>> graph, 
                                                 List<Payment> originalPayments) {
        // Create a user name map from original payments
        Map<String, String> userNames = new HashMap<>();
        for (Payment p : originalPayments) {
            userNames.put(p.getPayerId(), p.getPayerName());
            userNames.put(p.getReceiverId(), p.getReceiverName());
        }
        
        List<Payment> simplified = new ArrayList<>();
        
        for (String from : graph.keySet()) {
            Map<String, Double> debts = graph.get(from);
            for (String to : debts.keySet()) {
                double amount = debts.get(to);
                
                if (amount > 0.01) {  // Only include non-zero debts
                    String payerName = userNames.getOrDefault(from, from);
                    String receiverName = userNames.getOrDefault(to, to);
                    
                    Payment payment = new Payment(from, payerName, to, receiverName, amount);
                    simplified.add(payment);
                }
            }
        }
        
        return simplified;
    }
    
    /**
     * Get simplification statistics
     * 
     * @param original Original payments
     * @param simplified Simplified payments
     * @return Statistics string
     */
    public String getSimplificationStats(List<Payment> original, List<Payment> simplified) {
        double originalTotal = original.stream().mapToDouble(Payment::getAmount).sum();
        double simplifiedTotal = simplified.stream().mapToDouble(Payment::getAmount).sum();
        int reductionCount = original.size() - simplified.size();
        
        StringBuilder stats = new StringBuilder();
        stats.append("\n╔════════════════════════════════════════════════════════════════╗\n");
        stats.append("║               DEBT SIMPLIFICATION STATISTICS                   ║\n");
        stats.append("╚════════════════════════════════════════════════════════════════╝\n");
        stats.append(String.format("Original Transactions: %d\n", original.size()));
        stats.append(String.format("Simplified Transactions: %d\n", simplified.size()));
        stats.append(String.format("Transactions Eliminated: %d (%.1f%% reduction)\n", 
            reductionCount, (reductionCount * 100.0 / original.size())));
        stats.append(String.format("Original Total Debt: ₹%.2f\n", originalTotal));
        stats.append(String.format("Simplified Total Debt: ₹%.2f\n", simplifiedTotal));
        
        return stats.toString();
    }
    
    /**
     * Display simplification before and after
     * 
     * @param original Original payments
     * @param simplified Simplified payments
     */
    public void displaySimplification(List<Payment> original, List<Payment> simplified) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║           BEFORE SIMPLIFICATION (ORIGINAL DEBTS)             ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        
        if (original.isEmpty()) {
            System.out.println("No debts");
        } else {
            for (Payment p : original) {
                System.out.println(String.format("%s → %s : ₹%.2f", 
                    p.getPayerName(), p.getReceiverName(), p.getAmount()));
            }
        }
        
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║          AFTER SIMPLIFICATION (CIRCULAR DEBTS REMOVED)       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        
        if (simplified.isEmpty()) {
            System.out.println("✅ All debts settled! (No circular debts found)");
        } else {
            for (Payment p : simplified) {
                System.out.println(String.format("%s → %s : ₹%.2f", 
                    p.getPayerName(), p.getReceiverName(), p.getAmount()));
            }
        }
        
        System.out.println(getSimplificationStats(original, simplified));
    }
}
