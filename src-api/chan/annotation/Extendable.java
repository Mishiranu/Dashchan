package chan.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * Used to indicate element can be used by extensions and can be extended.
 * This imposes some restrictions to deleting and modifying this element.
 */
@Inherited
@Target(value = {ElementType.TYPE, ElementType.METHOD})
@Retention(value = RetentionPolicy.SOURCE)
public @interface Extendable
{
	
}