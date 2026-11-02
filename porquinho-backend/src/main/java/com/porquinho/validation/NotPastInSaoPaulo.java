package com.porquinho.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The date may be today or later, "today" being the calendar day in America/Sao_Paulo (Story 3.8), so a user
 * in Brazil picking today at 22:00 is not rejected because UTC already moved to tomorrow. Null is valid.
 */
@Documented
@Constraint(validatedBy = NotPastInSaoPaulo.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotPastInSaoPaulo {

    ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    String message() default "A data não pode estar no passado";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<NotPastInSaoPaulo, LocalDate> {
        @Override
        public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
            return value == null || !value.isBefore(LocalDate.now(ZONE));
        }
    }
}
