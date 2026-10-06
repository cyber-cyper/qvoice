package org.junit;
import java.lang.annotation.*;
/** Sandbox-only stub of JUnit 4's @Test (the real JUnit is used by the Gradle build). */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface Test {
    Class<? extends Throwable> expected() default None.class;
    long timeout() default 0L;
    class None extends Throwable { private None() {} }
}
