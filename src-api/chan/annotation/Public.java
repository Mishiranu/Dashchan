package chan.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * Used to indicate element can be used by extensions.
 * This imposes some restrictions to deleting and modifying this element.
 */
@Inherited
@Target(value = {ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.FIELD})
@Retention(value = RetentionPolicy.SOURCE)
public @interface Public
{
	
}