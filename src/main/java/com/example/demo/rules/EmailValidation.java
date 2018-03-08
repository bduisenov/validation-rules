package com.example.demo.rules;

import java.util.Optional;

public class EmailValidation implements ValidationRule<String> {

    @Override
    public Optional<String> apply(String val) {
        if (val.length() <= 1) {
            return Optional.of("Length must be greater than 1 char");
        }
        if (!(val.contains("@"))) {
            return Optional.of("Email must contain @ symbol");
        }
        return Optional.empty();
    }
}
