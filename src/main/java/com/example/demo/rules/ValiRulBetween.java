package com.example.demo.rules;

import java.util.Optional;

public interface ValiRulBetween extends ValidationRule<String> {

    default Optional<String> apply(Long val) {
        return apply(val.toString());
    }

}
