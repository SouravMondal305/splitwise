package org.example.Payment;

/**
 * Represents a payment transaction between two users
 * Indicates that one user (payer) needs to pay another user (receiver) a certain amount
 */
public class Payment {
    
    private String payerId;
    private String payerName;
    private String receiverId;
    private String receiverName;
    private double amount;
    
    public Payment(String payerId, String payerName, String receiverId, String receiverName, double amount) {
        this.payerId = payerId;
        this.payerName = payerName;
        this.receiverId = receiverId;
        this.receiverName = receiverName;
        this.amount = amount;
    }
    
    public String getPayerId() {
        return payerId;
    }
    
    public String getPayerName() {
        return payerName;
    }
    
    public String getReceiverId() {
        return receiverId;
    }
    
    public String getReceiverName() {
        return receiverName;
    }
    
    public double getAmount() {
        return amount;
    }
    
    @Override
    public String toString() {
        return payerName + " (" + payerId + ") pays " + receiverName + " (" + receiverId + ") ₹" + 
               String.format("%.2f", amount);
    }
}
