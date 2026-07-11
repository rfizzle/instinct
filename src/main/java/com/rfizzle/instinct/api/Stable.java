package com.rfizzle.instinct.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as part of Instinct's stable public API surface, governed by the Concord API
 * Standard: stable across patch and minor versions, breaking changes only with a major version
 * bump and a changelog entry.
 *
 * <p>This is the suite's intended {@code @ApiStatus.Stable} marker. JetBrains'
 * {@code org.jetbrains.annotations.ApiStatus} has no {@code Stable} member (it only ships
 * {@code Internal}, {@code Experimental}, etc.), so the marker is declared locally — consistent
 * with Concord's convention-over-dependency rule that {@code api} package code is duplicated per
 * mod, never shared as a runtime library.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Stable {
}
