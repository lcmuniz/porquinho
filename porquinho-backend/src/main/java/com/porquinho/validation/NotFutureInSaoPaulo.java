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

/**
 * The date may be today or earlier, "today" being the calendar day in America/Sao_Paulo (Story 4.5; sibling of
 * {@link NotPastInSaoPaulo}), so a user in Brazil picking today at 22:00 is not rejected and a date that is
 * already "tomorrow" in UTC but not in Brazil is accepted. Null is valid.
 */
@Documented
@Constraint(validatedBy = NotFutureInSaoPaulo.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotFutureInSaoPaulo {

    String message() default "A data não pode estar no futuro";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<NotFutureInSaoPaulo, LocalDate> {
        @Override
        public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
            return value == null || !value.isAfter(LocalDate.now(NotPastInSaoPaulo.ZONE));
        }
    }
}
