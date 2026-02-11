package org.example.sep26management.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility class to generate BCrypt password hashes
 * Run this main method to generate password hash for Admin@123
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        String password = "123";
        String hashedPassword = encoder.encode(password);

        System.out.println("========================================");
        System.out.println("Password: " + password);
        System.out.println("Hashed Password: " + hashedPassword);
        System.out.println("========================================");
        System.out.println();
        System.out.println("SQL to update password:");
        System.out.println(
                "UPDATE users SET password_hash = '" + hashedPassword + "' WHERE email = 'admin@warehouse.com';");
        System.out.println("========================================");
    }
}
