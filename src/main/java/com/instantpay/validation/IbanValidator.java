package com.instantpay.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.iban4j.IbanUtil;

public class IbanValidator implements ConstraintValidator<ValidIban, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return false;
        try {
            IbanUtil.validate(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
