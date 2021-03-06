package com.example.demo.rules;

import com.example.demo.rules.EmailValidation;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

public class EmailValidationTest {

    private EmailValidation validation = new EmailValidation();

    @Test
    public void validation_works() throws Exception {
        Optional<String> result = validation.apply("some@email.com");

        assertThat(result.isPresent()).isFalse();
    }

}
