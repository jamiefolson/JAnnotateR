package com.jfolson.jannotater;

import java.io.IOException;
import java.io.Writer;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RJavaUtils
{
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");
	final static Logger logger = LoggerFactory.getLogger("com.jfolson.jannotater");

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

	public static void convertToPrimitive(Writer output, Element type,
			String fromObj, String toObj) throws IOException {
		try{
		//logger.debug("can cast: "+type.getSimpleName().toString()+"?");
		String typeString = type.getSimpleName().toString().toUpperCase();
		if (typeString.equals("CHARACTER")){
			typeString = "CHAR";
		}else if (typeString.equals("INTEGER")){
			typeString = "INT";
		}
		TypeKind kind = TypeKind.valueOf(typeString);
			switch (kind) {
			case BOOLEAN:
				RJavaUtils.writeLine(output, toObj + " <- .jcall(" +fromObj + 
						", \"Z\", \"booleanValue\")");
				return;
			case BYTE:
				RJavaUtils.writeLine(output, toObj + " <- .jcall(" +fromObj + 
						", \"B\", \"byteValue\")");
				return;
			case CHAR:
				RJavaUtils.writeLine(output, toObj + " <- .jcall(" +fromObj + 
						", \"C\", \"charValue\")");
				return;
			case DOUBLE:
				RJavaUtils.writeLine(output, toObj + " <- .jcall(" +fromObj + 
						", \"D\", \"doubleValue\")");
				return;
			case FLOAT:
				RJavaUtils.writeLine(output, toObj + " <- .jcall(" +fromObj + 
						", \"F\", \"floatValue\")");
				return ;
			case INT:
				RJavaUtils.writeLine(output, toObj + " <- .jcall(" +fromObj + 
						", \"I\", \"intValue\")");
				return ;
			case LONG:
				RJavaUtils.writeLine(output, toObj + " <- .jcall(" +fromObj + 
						", \"J\", \"longValue\")");
				return ;
			default:
			}
		}catch(IllegalArgumentException e){
			
		}
		if ((type instanceof NoType))
			return;
		if (type.toString().equals("java.lang.String")){
			RJavaUtils.writeLine(output, toObj + " <- .jcall(" +fromObj + 
					", \"S\", \"toString\")");
		}
			return;
		/*if ((type instanceof ArrayType)) {
			return isRjavaPrimitive(((ArrayType)type).getComponentType());
		}*/
	}
	public static boolean canConvertToPrimitive(Element type) {
		try{
		//logger.debug("can cast: "+type.getSimpleName().toString()+"?");
			String typeString = type.getSimpleName().toString().toUpperCase();
			if (typeString.equals("CHARACTER")){
				typeString = "CHAR";
			}else if (typeString.equals("INTEGER")){
				typeString = "INT";
			}
		TypeKind kind = TypeKind.valueOf(typeString);
			switch (kind) {
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
		}catch(IllegalArgumentException e){
			
		}
		if ((type instanceof NoType))
			return false;
		if (type.toString().equals("java.lang.String"))
			return true;
		/*if ((type instanceof ArrayType)) {
			return isRjavaPrimitive(((ArrayType)type).getComponentType());
		}*/
		return false;
	}

	public static boolean isRjavaPrimitive(TypeMirror type) {
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
			return isRjavaPrimitive(((ArrayType)type).getComponentType());
		}
		return false;
	}
}