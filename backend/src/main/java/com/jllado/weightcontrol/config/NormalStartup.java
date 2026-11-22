package com.jllado.weightcontrol.config;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(NormalStartup.Condition.class)
public @interface NormalStartup {
    class Condition implements org.springframework.context.annotation.Condition {
        public static boolean maintenance(org.springframework.core.env.Environment environment) {
            return environment.containsProperty("app.weekly-summary.backfill.mode") || environment.containsProperty("app.meal-rating.rerate.mode");
        }

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !maintenance(context.getEnvironment());
        }
    }
}
