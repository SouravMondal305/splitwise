# Debt Simplification - Step by Step Guide

## Overview

The **Debt Simplification Service** is a core feature that detects and eliminates circular debts in the system. Instead of settling complex networks of IOUs, it intelligently simplifies them to the minimum number of transactions needed.

---

## Problem Statement

### Why Debt Simplification is Needed

In a typical expense-sharing scenario with multiple people, debts can become complex and circular:

```
Example 1: Simple Circular Debt
A owes B: 300
B owes C: 300
C owes A: 300
↓
Result: Everyone can settle without any transactions (net = 0)

Example 2: Partial Circular Debt
A → B: 500
B → C: 300
C → A: 300
↓
Simplified: A → B: 200 (only this transaction needed)
```

### Real-World Scenario

**Trip with friends:**
```
Alice pays 3000 for hotel → Bob owes 1000, Charlie owes 1000
Bob pays 1200 for food → Alice owes 400, Charlie owes 400
Charlie pays 1700 for tours → Alice owes 500, Bob owes 600

Original Debts:
- Bob → Alice: 600
- Bob → Charlie: 1000
- Charlie → Bob: 600

After Simplification:
- Bob → Charlie: 400 (600 - 600 = 0, then 1000 - 600 = 400)
```

---

## Algorithm Overview

The **DebtSimplificationService** uses a cycle-detection algorithm based on **Depth-First Search (DFS)**.

### Core Concept

1. **Represent debts as a directed graph**
   - Each user is a node
   - Each debt is a directed edge with amount

2. **Find cycles** in the graph
   - Use DFS to traverse debt paths
   - When path returns to start node, it's a cycle

3. **Eliminate cycles**
   - Find minimum debt in cycle
   - Subtract minimum from all debts
   - Remove zero-value debts

4. **Repeat** until no cycles remain

---

## Step-by-Step Walkthrough

### Step 1: Build Debt Graph

**Purpose:** Convert payment list into a directed graph structure

**Input:** List of Payment objects
```
Payment(Alice, Bob, 500)    // Alice → Bob: 500
Payment(Bob, Charlie, 300)  // Bob → Charlie: 300
Payment(Charlie, Alice, 300) // Charlie → Alice: 300
```

**Process:**
```
For each payment:
  Create node: from_user
  Create edge: from_user → to_user : amount
  
Graph Structure:
  Alice → {Bob: 500}
  Bob → {Charlie: 300}
  Charlie → {Alice: 300}
```

**Code Logic:**
```java
Map<String, Map<String, Double>> graph = new HashMap<>();

for (Payment payment : payments) {
    String payer = payment.getPayerId();      // "Alice"
    String receiver = payment.getReceiverId(); // "Bob"
    double amount = payment.getAmount();       // 500
    
    // Initialize if not exists
    graph.putIfAbsent(payer, new HashMap<>());
    
    // Add/accumulate edge
    double existing = graph.get(payer).getOrDefault(receiver, 0.0);
    graph.get(payer).put(receiver, existing + amount);
}
```

**Visual Representation:**
```
┌─────────────┐         500
│   Alice     │───────────────→ Bob
└─────────────┘                 │
      ▲                         │ 300
      │                         ▼
      │        ┌─────────────┐
      │        │  Charlie    │
      └────────┤             │
         300   └─────────────┘
```

---

### Step 2: Find Cycles Using DFS

**Purpose:** Detect circular debt patterns in the graph

**Key Insight:** A cycle exists when following the debt chain leads back to the starting node.

**Algorithm:**
```
For each node in graph:
  Start DFS from this node
  Follow paths of debts
  If we reach starting node again → CYCLE FOUND
  If dead end → No cycle from this starting point
```

**Example Trace:**
```
Starting from Alice:
  Current: Alice
  → Neighbors: [Bob]
  → Try Bob
    Current: Bob
    → Neighbors: [Charlie]
    → Try Charlie
      Current: Charlie
      → Neighbors: [Alice]
      → Check: Is Alice == starting node? YES!
      → Path found: [Alice, Bob, Charlie, Alice]
      → CYCLE DETECTED ✓
```

**Code Logic:**
```java
private boolean eliminateCycle(Map<String, Map<String, Double>> graph) {
    // Try starting from each node
    for (String startNode : new HashSet<>(graph.keySet())) {
        List<String> path = new ArrayList<>();
        path.add(startNode);
        
        // Use DFS to find cycle
        if (dfsForCycle(startNode, startNode, graph, path, new HashSet<>())) {
            if (path.size() > 2) {
                // Cycle found, eliminate it
                eliminateCycleFromPath(path, graph);
                return true;
            }
        }
    }
    return false;
}

private boolean dfsForCycle(String current, String target,
                           Map<String, Map<String, Double>> graph,
                           List<String> path, Set<String> visited) {
    
    Map<String, Double> neighbors = graph.getOrDefault(current, new HashMap<>());
    
    for (String neighbor : neighbors.keySet()) {
        // Check if we found path back to start (cycle detected)
        if (neighbor.equals(target) && path.size() > 1) {
            path.add(neighbor);
            return true;  // Cycle found!
        }
        
        // Continue DFS if not visited
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
```

---

### Step 3: Eliminate Cycle From Path

**Purpose:** Reduce debts in the cycle by the minimum amount

**Key Idea:** In a cycle, the maximum common debt can be eliminated

**Example:**
```
Cycle: Alice → Bob → Charlie → Alice
Amounts: 500 → 300 → 300

Step 1: Find minimum
  Min(500, 300, 300) = 300

Step 2: Subtract minimum from each debt
  Alice → Bob: 500 - 300 = 200
  Bob → Charlie: 300 - 300 = 0 (remove)
  Charlie → Alice: 300 - 300 = 0 (remove)

Result Graph:
  Alice → {Bob: 200}
  (Bob and Charlie edges removed)
```

**Why This Works:**

Imagine the actual settlement:
- Alice pays Bob 500 ✓
- Bob pays Charlie 300 ✓
- Charlie pays Alice 300 ✓

Net settlement:
- Alice: -500 + 300 = -200 (owes 200)
- Bob: +500 - 300 = +200 (gets 200)
- Charlie: +300 - 300 = 0 (settled)

Simplified:
- Alice pays Bob 200 ✓

**Code Logic:**
```java
private void eliminateCycleFromPath(List<String> path, 
                                    Map<String, Map<String, Double>> graph) {
    // Step 1: Find minimum debt in cycle
    double minDebt = Double.MAX_VALUE;
    
    for (int i = 0; i < path.size() - 1; i++) {
        String from = path.get(i);
        String to = path.get(i + 1);
        
        double debt = graph.get(from).getOrDefault(to, 0.0);
        minDebt = Math.min(minDebt, debt);
    }
    
    // Step 2: Reduce all debts in cycle by minimum
    for (int i = 0; i < path.size() - 1; i++) {
        String from = path.get(i);
        String to = path.get(i + 1);
        
        double currentDebt = graph.get(from).get(to);
        double newDebt = currentDebt - minDebt;
        
        // Remove if essentially zero
        if (newDebt < 0.01) {
            graph.get(from).remove(to);
        } else {
            graph.get(from).put(to, newDebt);
        }
    }
}
```

---

### Step 4: Repeat Until No Cycles Remain

**Purpose:** Simplify all circular debts

**Process:**
```
Iteration 1:
  Find cycle → Yes
  Eliminate → Reduce one cycle
  
Iteration 2:
  Find cycle → Yes (different cycle)
  Eliminate → Reduce another cycle
  
Iteration 3:
  Find cycle → No
  Done → All cycles eliminated
```

**Code Logic:**
```java
public List<Payment> simplifyDebts(List<Payment> payments) {
    if (payments.isEmpty()) {
        return payments;
    }
    
    // Build graph
    Map<String, Map<String, Double>> debtGraph = buildDebtGraph(payments);
    
    // Keep eliminating cycles until none remain
    boolean simplified;
    do {
        simplified = eliminateCycle(debtGraph);  // Returns true if cycle found
    } while (simplified);
    
    // Convert back to Payment list
    return convertGraphToPayments(debtGraph, payments);
}
```

---

### Step 5: Convert Back to Payment List

**Purpose:** Transform simplified graph back to Payment objects

**Process:**
```
For each edge in simplified graph:
  Create Payment object
  
Before:
  Alice → {Bob: 200}
  Bob → {Alice: 100}

After:
  Payment(Alice, Bob, 200)
  Payment(Bob, Alice, 100)
```

**Code Logic:**
```java
private List<Payment> convertGraphToPayments(Map<String, Map<String, Double>> graph,
                                            List<Payment> originalPayments) {
    // Create user name mapping from original
    Map<String, String> userNames = new HashMap<>();
    for (Payment p : originalPayments) {
        userNames.put(p.getPayerId(), p.getPayerName());
        userNames.put(p.getReceiverId(), p.getReceiverName());
    }
    
    List<Payment> simplified = new ArrayList<>();
    
    // Convert each edge to Payment
    for (String from : graph.keySet()) {
        Map<String, Double> debts = graph.get(from);
        for (String to : debts.keySet()) {
            double amount = debts.get(to);
            
            if (amount > 0.01) {  // Only non-zero debts
                String payerName = userNames.getOrDefault(from, from);
                String receiverName = userNames.getOrDefault(to, to);
                
                Payment payment = new Payment(from, payerName, 
                                            to, receiverName, amount);
                simplified.add(payment);
            }
        }
    }
    
    return simplified;
}
```

---

## Complete Example Walkthrough

### Scenario: Europe Trip

**Initial Setup:**
```
Alice, Bob, Charlie split expenses

1. Alice pays 3000 for hotel (split 3 ways)
   Alice → Bob: 1000
   Alice → Charlie: 1000

2. Bob pays 1200 for food (split 3 ways)
   Bob → Alice: 400
   Bob → Charlie: 400

3. Charlie pays 1700 for tours (unequal split)
   Charlie → Alice: 500
   Charlie → Bob: 600
```

**Original Debts (Before Simplification):**
```
Alice → Bob: 1000
Alice → Charlie: 1000
Bob → Alice: 400
Bob → Charlie: 400
Charlie → Alice: 500
Charlie → Bob: 600
```

### Execution Trace

**Step 1: Build Graph**
```
Graph = {
  Alice: {Bob: 1000, Charlie: 1000},
  Bob: {Alice: 400, Charlie: 400},
  Charlie: {Alice: 500, Bob: 600}
}
```

**Step 2-3: Find and Eliminate Cycle 1**
```
DFS from Alice:
  Alice → Bob (exist) → Alice (cycle!)
  
Cycle: [Alice, Bob, Alice]
Amounts: 1000 → 400 → (back)
Min = 400

After reduction:
  Alice → Bob: 1000 - 400 = 600
  Bob → Alice: 400 - 400 = 0 (removed)

Graph now:
  Alice: {Bob: 600, Charlie: 1000},
  Bob: {Charlie: 400},
  Charlie: {Alice: 500, Bob: 600}
```

**Step 2-3: Find and Eliminate Cycle 2**
```
DFS from Alice:
  Alice → Charlie (exist) → Alice (cycle!)
  
Cycle: [Alice, Charlie, Alice]
Amounts: 1000 → 500 → (back)
Min = 500

After reduction:
  Alice → Charlie: 1000 - 500 = 500
  Charlie → Alice: 500 - 500 = 0 (removed)

Graph now:
  Alice: {Bob: 600, Charlie: 500},
  Bob: {Charlie: 400},
  Charlie: {Bob: 600}
```

**Step 2-3: Find and Eliminate Cycle 3**
```
DFS from Bob:
  Bob → Charlie (exist) → Bob? (no direct link back)
  
Continue DFS:
  Bob → Charlie → Alice (no direct link)
  
No cycle starting from Bob

DFS from Charlie:
  Charlie → Bob (exist) → Charlie? (no direct link)
  
Continue DFS:
  Charlie → Bob → Alice (no direct link)
  
No cycle starting from Charlie

DFS from Alice:
  Alice → Bob (exist) → Charlie (exist) → Bob (cycle!)
  
Cycle: [Alice, Bob, Charlie, Bob]
Amounts: 600 → 400 → 600 → (back to Bob)
Min = 400

After reduction:
  Alice → Bob: 600 - 400 = 200
  Bob → Charlie: 400 - 400 = 0 (removed)
  Charlie → Bob: 600 - 400 = 200

Graph now:
  Alice: {Bob: 200, Charlie: 500},
  Charlie: {Bob: 200}
```

**Step 2-3: Find and Eliminate Cycle 4**
```
DFS from all nodes → No more cycles found!
Loop exits
```

**Step 5: Final Result**
```
Simplified Payments:
1. Alice → Bob: 200
2. Alice → Charlie: 500
3. Charlie → Bob: 200
```

---

## Complexity Analysis

### Time Complexity

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| Build Graph | O(n) | n = number of transactions |
| DFS for cycle | O(V + E) | V = users, E = debts |
| Find/Eliminate cycle | O(V + E) | Worst case multiple iterations |
| Overall | O(k × (V + E)) | k = number of cycles |

**Practical:** Usually O(V + E) because cycle elimination is efficient

### Space Complexity

| Data Structure | Complexity |
|---|---|
| Debt Graph | O(V + E) |
| DFS Stack/Visited | O(V) |
| Result List | O(E) |
| **Total** | **O(V + E)** |

---

## Edge Cases & Handling

### 1. Empty Payment List
```java
if (payments.isEmpty()) {
    return payments;  // No simplification needed
}
```

### 2. No Cycles (Linear Debts)
```
A → B: 100
B → C: 50

Result: No changes (already optimal)
```

### 3. Complete Circular Debt
```
A ↔ B ↔ C ↔ A (with equal amounts)

Result: Empty (all debts cancel out)
```

### 4. Multiple Disconnected Cycles
```
Cycle 1: A → B → A
Cycle 2: C → D → E → C

Algorithm: Eliminates both separately
```

### 5. Floating Point Precision
```java
if (newDebt < 0.01) {  // Threshold to handle float rounding
    graph.get(from).remove(to);
}
```

---

## Advantages of Debt Simplification

| Advantage | Benefit |
|-----------|---------|
| **Fewer Transactions** | Less confusion, easier settlements |
| **Reduced Errors** | Fewer opportunities for mistakes |
| **Faster Settlement** | Quick resolution of complex debts |
| **Better UX** | Clear, minimal payment instructions |
| **Optimized Efficiency** | Users understand what exactly needs to be paid |

### Example Benefit
```
Without Simplification: 6 transactions needed
With Simplification: 3 transactions needed
Reduction: 50% fewer transactions!
```

---

## Implementation Notes

### Thread Safety
Current implementation is **not thread-safe**. For multi-threaded environment:
```java
private synchronized List<Payment> simplifyDebts(List<Payment> payments) {
    // ... implementation
}
```

### Performance Optimization
For very large graphs (1000+ users, 10000+ debts):
1. Use graph compression algorithms
2. Implement batch cycle detection
3. Cache intermediate results

### Testing Recommendations
```java
@Test
public void testSimpleCycle() { }

@Test
public void testNoCycles() { }

@Test
public void testMultipleCycles() { }

@Test
public void testEmptyPayments() { }

@Test
public void testFloatingPointPrecision() { }
```

---

## Visual Summary

```
INPUT: Complex Debt Network
┌─────────────┐
│ A ↔ B ↔ C ↔ A
│ Multiple    │
│ transactions │
│ Some circles │
└─────────────┘
       │
       ▼
ALGORITHM: Cycle Detection & Elimination
┌────────────────────────────────────┐
│ 1. Build directed graph            │
│ 2. DFS to find cycles              │
│ 3. Find min debt in cycle          │
│ 4. Reduce all by minimum           │
│ 5. Remove zero debts               │
│ 6. Repeat until no cycles          │
└────────────────────────────────────┘
       │
       ▼
OUTPUT: Simplified Payments
┌─────────────┐
│ A → B: 200  │
│ A → C: 500  │
│ C → B: 200  │
│ (Minimal)   │
│ (Optimal)   │
└─────────────┘
```

