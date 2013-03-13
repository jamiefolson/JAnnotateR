package com.jfolson.jannotater;

import java.io.IOException;
import java.io.Writer;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

public class RJavaUtils
{
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");

	public static void write(Writer output, String content) throws IOException
	{
		output.write(content);
	}

	public static void writeLine(Writer output, String content) throws IOException
	{
		output.write(content + LINE_SEPARATOR);
	}

	public static String typeToRAbbreviation(TypeMirror type)
	{
		return typeToRAbbreviation(type, false);
	}

	public static String typeToRAbbreviation(TypeMirror type, boolean isReturnType) {
		if ((type instanceof PrimitiveType)) {
			switch (((PrimitiveType)type).getKind()) {
			case BOOLEAN:
				return "Z";
			case BYTE:
				return "B";
			case CHAR:
				return "C";
			case DOUBLE:
				return "D";
			case FLOAT:
				return "F";
			case INT:
				return "I";
			case LONG:
				return "J";
			default:
			}
		}
		if ((type instanceof NoType))
			return "V";
		if (type.toString().equals("java.lang.String"))
			return "S";
		if ((type instanceof ArrayType)) {
			return "[" + typeToRAbbreviation(((ArrayType)type).getComponentType());
		}

		String typeString = type.toString().replace(".", "/");

		if ((type instanceof TypeVariable)) {
			TypeVariable variable = (TypeVariable)type;
			typeString = "java/lang/Object";
		}

		if (isReturnType) {
			return "L" + typeString + ";";
		}

		return typeString;
	}

	public static boolean canCastAsPrimitive(TypeMirror type) {
		if ((type instanceof PrimitiveType)) {
			switch (((PrimitiveType)type).getKind()) {
			case BOOLEAN:
				return true;
			case BYTE:
				return true;
			case CHAR:
				return true;
			case DOUBLE:
				return true;
			case FLOAT:
				return true;
			case INT:
				return true;
			case LONG:
				return true;
			default:
			}
		}
		if ((type instanceof NoType))
			return true;
		if (type.toString().equals("java.lang.String"))
			return true;
		if ((type instanceof ArrayType)) {
			return canCastAsPrimitive(((ArrayType)type).getComponentType());
		}
		return false;
	}
}