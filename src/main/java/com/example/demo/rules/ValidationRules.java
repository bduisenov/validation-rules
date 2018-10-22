package com.example.demo.rules;

public class ValidationRules {

    private ValidationRules() {
        // NOOP
    }

    static boolean notBlank(String val) {
        if (val == null) {
            return false;
        }
        return !val.trim().isEmpty();
    }
}
