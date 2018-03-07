package com.example.demo.rules;

public class EmailValidation implements ValidationRule<String> {

    @Override
    public Boolean apply(String val) {
        return val.length() > 1 && val.contains("@");
    }
}
