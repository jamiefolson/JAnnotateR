package com.jfolson.jannotater;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.CONSTRUCTOR})
public @interface RJava
{
	public static final String DEFAULT = "[default]";
	public static final String RETURN_VARIABLE = "jreturnobj";
	public static final String SELF_VARIABLE = "jself";

	public String rName() default "[default]";

	public String rCode() default "[default]";

	public String rBefore() default "[default]";

	public String rReturn() default "[default]";

	//  public String preCall() default "[default]";

	//  public String postCall() default "[default]";
}

