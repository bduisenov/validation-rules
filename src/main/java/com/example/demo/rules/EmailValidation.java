package com.example.demo.rules;

import java.util.Optional;

import static com.example.demo.rules.ValidationRules.notBlank;

public class EmailValidation implements ValiRulBetween {

    @Override
    public Optional<String> apply(String val) {
        if (notBlank(val)) {
            return Optional.of("val must not be null or empty");
        }

        if (val.length() <= 1) {
            return Optional.of("Length must be greater than 1 char");
        }
        if (!(val.contains("@"))) {
            return Optional.of("Email must contain @ symbol");
        }

        String test = "";
        test += "some";
        test += "val";

        return Optional.of(test);
    }
}
