package com.oct.invoicesystem;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptGeneratorTest {

    @Test
    public void generateHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode("admin123");
        System.out.println("GENERATED_ADMIN_HASH=" + hash);
    }
}
