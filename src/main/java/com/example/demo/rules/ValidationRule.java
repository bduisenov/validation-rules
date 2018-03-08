package com.example.demo.rules;

import java.util.Optional;
import java.util.function.Function;

public interface ValidationRule<T> extends Function<T, Optional<String>> {

}
