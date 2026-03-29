package org.example;

public class Main {

    public static void main(String[] args){

        // Get Singleton instance
        Splitwise splitwise = Splitwise.getInstance();
        
        // Run end-to-end demo
        splitwise.runSplitwiseDemo();
        
        // Run debt simplification demo
        splitwise.runDebtSimplificationDemo();
    }
}

