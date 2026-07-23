package com.codegym.mathclass.systemlog.annotation;

import com.codegym.mathclass.systemlog.entity.SystemLogLevel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {
    String action();
    String resourceType() default "SYSTEM";
    SystemLogLevel level() default SystemLogLevel.INFO;
}
