package com.oct.invoicesystem;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DbCreator {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://127.0.0.1:5432/postgres";
        String user = "postgres";
        String password = "dany";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            stmt.executeUpdate("CREATE DATABASE oct_invoice_test");
            System.out.println("Database created successfully!");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
