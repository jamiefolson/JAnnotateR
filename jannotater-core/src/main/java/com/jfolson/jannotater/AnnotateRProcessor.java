package com.jfolson.jannotater;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner6;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnnotateRProcessor extends AbstractProcessor
{
	//private static final String UNSUPPORTED_ANNOTATION_MESSAGE = "@RJava annotations can only be processed for executables(ie constructors, methods): ";
	private static final Set<String> supportedAnnotations = new HashSet<String>(Arrays.asList(new String[] { "com.jfolson.jannotater.RJava" }));
	
	final Logger logger = LoggerFactory.getLogger("com.jfolson.jannotater");
	public static final String INDENT = "\t\t\t";
	private ProcessingEnvironment mEnv;
	private Elements mElements;
	
	/**
	 * Whether or not to wrap objects as S4 classes 
	 */
	public boolean useS4Methods = true;
	/**
	 *  Keep a map of input filenames to output Writer objects, since I don't know exactly how they'll be visited.
	 *  
	 *  It might be possible to keep a single Writer alive and simply check to see if the current declaration is contained
	 *  in a different file, but I'm not sure if you're guaranteed to visit all declarations in a file at once.
	 */
	HashMap<String, Writer> inputOutputMap = new HashMap<String, Writer>();
	Types mTypes;
	HashSet<String> exportedSymbols = new HashSet<String>();
	HashSet<String> exportedGenerics = new HashSet<String>();
	HashSet<TypeMirror> exportedClasses = new HashSet<TypeMirror>();
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");

	public void init(ProcessingEnvironment processingEnv)
	{
		this.mEnv = processingEnv;
		this.mTypes = this.mEnv.getTypeUtils();
		this.mElements = this.mEnv.getElementUtils();
	}

	public Set<String> getSupportedAnnotationTypes() {
		return supportedAnnotations;
	}

	public AnnotateRProcessor()
	{
		logger.info("Creating Processor");
	}

	private void checkRNameCollisions(String rName, String rQualifiedType) {
		String rQualifiedName = rName + "." + rQualifiedType;
		if (this.exportedSymbols.contains(rQualifiedName)) {
			throw new RuntimeException("Duplicate Name: '" + rName + "' defined multiply for Java type '" + 
						rQualifiedType + "'");
		}

		this.exportedSymbols.add(rQualifiedName);
	}

	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
	{
		logger.info("starting to process annotations");

		Set<? extends Element> elements = roundEnv.getRootElements();

		for (Element element : elements)
		{
			logger.info("Root: " + element);
		}
		
		if (!annotations.isEmpty()){
		TypeElement rjavaAnnotationType = annotations.iterator().next();
		elements = roundEnv.getElementsAnnotatedWith(rjavaAnnotationType);
		for (Element element : elements)
		{
			logger.debug("Need to export caller type: " + element.getEnclosingElement().asType().toString());
			this.exportedClasses.add(element.getEnclosingElement().asType());
		}
		for (Element element : elements)
		{
			if (!(element instanceof ExecutableElement)){
				throw new UnsupportedOperationException("@RJava annotations can only be processed for " +
						"executables(e.g. constructors, methods), instead found: " + element);	
			}
			ExecutableElement d = (ExecutableElement)element;
			TypeMirror returnType = d.getReturnType();
			ArrayList<TypeMirror> ancestors = new ArrayList<TypeMirror>(this.mTypes.directSupertypes(returnType));
			boolean found = false;
			while (!ancestors.isEmpty() && !found){
				TypeMirror next = (TypeMirror)ancestors.remove(0);
				// Only contain the first parent that you are also exporting.
				// Currently exports both class and interface parents, since S4 allows multiple inheritance
				// This is a little confusing, since ALL of the "contain"-ed R classes have an external jobjRef slot
				// However, since they're all named the same, there shouldn't be any problems.
				if (this.exportedClasses.contains(next)){
					found = true;
				}else {
					ancestors.addAll(this.mTypes.directSupertypes(next));
				}
			}
			if (found){
				logger.debug("Need to export return type: " + element.getEnclosingElement().asType().toString());
				this.exportedClasses.add(returnType);
			}
		}
		
			for (Element element : elements)
			{
				logger.debug("Element name: " + element.getSimpleName());
				logger.debug("Element kind: " + element.getKind());
				logger.debug("Enclosing element: " + element.getEnclosingElement());
				logger.debug("Element type: " + element.asType());
				logger.debug("Element class: " + element.getClass());

				String filename = getDeclarationFilename(element);
				Writer output = this.inputOutputMap.get(filename);
				if (output == null) {
					try {
						output = getWriter(filename + ".R");
					}
					catch (IOException e)
					{
						e.printStackTrace();
						throw new RuntimeException("Error writing to file: "+e.getMessage());
					}
					this.inputOutputMap.put(filename, output);
				}
				this.processDeclaration((ExecutableElement)element);
				//element.accept(this.visitor, null);
			}
		
		// Generate necessary classes here.  There WILL ONLY EVER BE ONE ROUND.
		// Multiple rounds happen when annotations result in new java files, which have to be processed for annotations.

		if (useS4Methods){

			try {
				Writer output;
				output = this.getWriter("0methods.R");
				for (String rName : this.exportedGenerics){
					// Create a generic method if none exists
					//RJavaUtils.writeLine(output, "\tif (!isGeneric(\"" + rName + "\")) {");

					RJavaUtils.writeLine(output, "#' @export"); 
					RJavaUtils.writeLine(output, "#' @rdname "+rName); 
					RJavaUtils.writeLine(output, "setGeneric(\"" + rName + "\", "+
							"function(obj,...) standardGeneric(\"" + rName + "\"))");

					//RJavaUtils.writeLine(output, "}"); 
				}

				output = this.getWriter("0classes.R");

				for (TypeMirror type : this.exportedClasses){
					ArrayList<TypeMirror> ancestors = new ArrayList<TypeMirror>(this.mTypes.directSupertypes(type));
					ArrayList<TypeMirror> parents = new ArrayList<TypeMirror>();
					while (!ancestors.isEmpty()){
						TypeMirror next = (TypeMirror)ancestors.remove(0);
						// Only contain the first parent that you are also exporting.
						// Currently exports both class and interface parents, since S4 allows multiple inheritance
						// This is a little confusing, since ALL of the "contain"-ed R classes have an external jobjRef slot
						// However, since they're all named the same, there shouldn't be any problems.
						if (this.exportedClasses.contains(next)){
							parents.add(next);
						}else {
							ancestors.addAll(this.mTypes.directSupertypes(next));
						}
					}
					RJavaUtils.writeLine(output, "setClass(\"" + type.toString() + "\",");
					RJavaUtils.writeLine(output, "\tcontains=c(");
					for (TypeMirror parent : parents){
						RJavaUtils.writeLine(output, "\t\t\""+parent.toString()+"\",");
					}
					RJavaUtils.writeLine(output, "\t\t\"jobjRef\")");// end contain
					RJavaUtils.writeLine(output, ")");//end setClass
				}
			} catch (javax.annotation.processing.FilerException e){
				throw new RuntimeException("More than one round of processing created R code!  This breaks the processor!");
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException("Error writing to file: "+e.getMessage());
			}
		}
		}
		roundComplete();
		return true;
	}

	/**
	 * Get or create an output Writer for the given input filename
	 * @param name input filename
	 * @return Writer for the output file
	 * @throws IOException if the file could not be created
	 */
	private Writer getWriter(String name) throws IOException {
		Writer output = this.inputOutputMap.get(name);
		if (output != null) return output;
		FileObject fileObject = null;

		fileObject = this.mEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "R", name, new Element[0]);

		File file = FileUtils.toFile(fileObject.toUri().toURL());
		logger.debug("creating: " + file.getAbsolutePath());

		output = new PrintWriter(fileObject.openOutputStream());
		this.inputOutputMap.put(name, output);

		return output;
	}

	/**
	 * Get the name of the file containing the declaration
	 * @param element A declaration element
	 * @return the name of the file containing element
	 */
	private String getDeclarationFilename(Element element) {
		String filename = null;
		Element parent = element;
		Element enclosing = parent.getEnclosingElement();
		while ((enclosing != null) && (enclosing.getKind() != ElementKind.PACKAGE)) {
			parent = enclosing;
			enclosing = parent.getEnclosingElement();
		}
		filename = parent.toString();
		return filename;
	}

	/**
	 * Actually visit a method declaration
	 * @param d
	 */
	public void processDeclaration(ExecutableElement d)
	{
		try
		{
			Set<Modifier> modifiers = d.getModifiers();

			boolean isConstructor = false;
			if (d.getKind() == ElementKind.CONSTRUCTOR) {
				isConstructor = true;
			}
			boolean isStatic = false;
			if (modifiers.contains(Modifier.STATIC)) {
				isStatic = true;
			}
			
			//  Looks awkward, but lots of naming stuff to get
			RJava annotation = d.getAnnotation(RJava.class);
			String typeName = d.getEnclosingElement().getSimpleName().toString();
			TypeMirror type = d.getEnclosingElement().asType();
			String cannonicalTypeName = type.toString();
			String simpleName = d.getSimpleName().toString();
			TypeMirror returnType = d.getReturnType();
			
			// Figure out where to write output
			String filename = getDeclarationFilename(d);
			Writer output = getWriter(filename);

			// Must "export" as R classes any java classes for which constructors or methods are exported
			this.exportedClasses.add(type);
			
			if (isConstructor) { // constructor's have no "return type" so assign here
				returnType = d.getEnclosingElement().asType();
			}
			logger.debug("Writing " + simpleName + " to " + filename);

			//  Allow for explicit assignment to method name
			String rName = annotation.rName();
			if (rName.equals("[default]")) {
				rName = simpleName;
				// Prepend "new" to typeName for constructors?
				if (isConstructor) {
					rName = "new" + typeName;
				}
			}
			
			// Qualify method name with type, a-la S3 methods
			//TODO Maybe this should use cannonical typename?
			String rQualifiedName = rName + "." + typeName;
			if (isConstructor) {
				// Constructor names *should be* already qualified (unless assigned poorly in the annotation) 
				rQualifiedName = rName;
			}
			// Check for collisions
			checkRNameCollisions(rName, typeName);

			String javadoc = this.mElements.getDocComment(d);
			if (javadoc == null) {
				RJavaUtils.writeLine(output, "#' TODO:Function documentation");
			} else {
				RJavaUtils.writeLine(output, "#' " + javadoc.replaceAll(LINE_SEPARATOR, LINE_SEPARATOR+"#' "));
				RJavaUtils.writeLine(output, "#' Previous documentation autogenerated from javadoc");
			}
			RJavaUtils.writeLine(output, "#' @param obj An rJava jobjRef of java type " + cannonicalTypeName + " to be operated on");
			RJavaUtils.writeLine(output, "#' \t This may be, but is not required to be, an R S4 object of the same type");

			RJavaUtils.writeLine(output, "#' @seealso \\link[" + typeName + "." + simpleName + ":../java/javadoc/" + filename.replace('.', File.separatorChar) + "]{javadoc api for the containing class}");

			RJavaUtils.writeLine(output, "#' @rdname "+rName); 
			// Export the appropriate symbols for non-methods
			// Methods are handled below
			if (isStatic) { 
				RJavaUtils.writeLine(output, "#' @export " + rQualifiedName);
			} else if (isConstructor) {
				RJavaUtils.writeLine(output, "#' @export " + rQualifiedName);
			} //TODO Maybe these should be handled with the exports?  
			else if (this.useS4Methods) {
				RJavaUtils.writeLine(output, "#' @exportMethod " + rName);
			} else {
				RJavaUtils.writeLine(output, "#' @S3method " + rName + " " + typeName);
				RJavaUtils.writeLine(output, "#' @export " + rQualifiedName);
			}

			// Assign to the qualified name, even if you later expose something else
			RJavaUtils.write(output, rQualifiedName + " <- function(");

			if ((!isStatic) && (!isConstructor)) {
				RJavaUtils.write(output, "obj"); // Explicitly pass "self" obj to methods
				if (d.getParameters().size() > 0) {
					RJavaUtils.writeLine(output, ", ");
				}
				RJavaUtils.writeLine(output, "" + LINE_SEPARATOR + "### object of type " + cannonicalTypeName + " to be operated on");
			}

			writeParameters(d.getParameters(), output, true);
			RJavaUtils.writeLine(output, ") {");

			castParameters(d.getParameters(), output);

			String rCode = annotation.rCode();
			if (!rCode.equals("[default]")) {
				RJavaUtils.writeLine(output, "\t" + rCode);
			}else {
				String rArgs = annotation.rArgs();
				if (!rArgs.equals("[default]")) {
					RJavaUtils.writeLine(output, "\t" + rArgs);
				}
				RJavaUtils.write(output, "\tjreturnobj <- ");

				String returnTypeStr = RJavaUtils.typeToRAbbreviation(returnType, true);

				if (isConstructor) {
					RJavaUtils.write(output, ".jnew(\"" + cannonicalTypeName + "\"");
				}
				else {
					RJavaUtils.write(output, ".jcall(");
					if (isStatic) {
						String staticTypeStr = RJavaUtils.typeToRAbbreviation(d.getEnclosingElement().asType(), false);

						RJavaUtils.writeLine(output, "\"" + staticTypeStr + "\", ");
					} else {
						RJavaUtils.writeLine(output, "obj$jobj, ");
					}

					RJavaUtils.writeLine(output, "\t\t\t\"" + returnTypeStr + "\", ");
					RJavaUtils.write(output, "\t\t\t\"" + d.getSimpleName() + "\"");
				}

				if (d.getParameters().size() > 0) {
					RJavaUtils.writeLine(output, ", ");
				}
				writeParameters(d.getParameters(), output, false);
				RJavaUtils.writeLine(output, ")"); // end jcall/jnew
				
				if (isConstructor){
					// For constructors, wrap the returned jobjRef using the desired R OOP 
					if (useS4Methods){
					// Export the classes and the inheritance chain at the end, so you don't have to pollute the 
					// inheritance chain and the namespace
					// So for now, just call the R constructor for the class you create at the end of the round
					// (You need to know what R parent(s) to include, plus it needs to be loaded first in "0classes.R")
					RJavaUtils.writeLine(output, "\treturnobj <- new(\"" + cannonicalTypeName + "\", jreturnobj)");
					}else {
						RJavaUtils.writeLine(output, "\treturnobj <- list(jobj=jreturnobj)");
						
						//  Add all classes right here for S3, since S3 doesn't search a hierarchy
						RJavaUtils.writeLine(output, "\tclass(returnobj) <- c(\"" + cannonicalTypeName + "\"");
						ArrayList<TypeMirror> parents = new ArrayList<TypeMirror>(this.mTypes.directSupertypes(type));
						HashSet<TypeMirror> alreadyDeclared = new HashSet<TypeMirror>();
						while (!parents.isEmpty()) {
							TypeMirror next = (TypeMirror)parents.remove(0);
							if (!alreadyDeclared.contains(next)) {
								RJavaUtils.write(output, ",\"" + next.toString() + "\"");
								parents.addAll(this.mTypes.directSupertypes(next));
								alreadyDeclared.add(next);
							}
						}
						RJavaUtils.writeLine(output, ")" + LINE_SEPARATOR);
					}

					//TODO I think I wrote this without thinking through the consequences 
					// This should at least be configured in the annotation
					/*
					boolean serializable = false;
					for (Class parent : javaType.getClass().getInterfaces()) {
						if (parent.equals(Serializable.class)) {
							serializable = true;
						}
					}
					if (serializable) {
						RJavaUtils.writeLine(output, "\t.jcache(obj$jobj)" + LINE_SEPARATOR);
					}
					*/
				}
				createWrapperObject("jreturnobj","returnobj", returnType, output);

				String rReturn = annotation.rReturn();
				if (!rReturn.equals("[default]")) {
					RJavaUtils.writeLine(output, "\t" + rReturn);
				}else {
					RJavaUtils.writeLine(output, "\treturnobj");
				}
				RJavaUtils.writeLine(output, "}" + LINE_SEPARATOR);
			}

			//  Create and export generics cruft
			if ((!isStatic) && (!isConstructor))
			{
				if (this.useS4Methods)
				{
					this.exportedGenerics.add(rName);
					RJavaUtils.writeLine(output, "#' @export"); 
					RJavaUtils.writeLine(output, "#' @rdname "+rName); 
					RJavaUtils.writeLine(output, "setMethod(" + rName + ", " + 
							typeName + ", "+ rQualifiedName +")");
				}
				else
				{ 
					if (!this.exportedGenerics.contains(rName)){
						RJavaUtils.writeLine(output, "#' @export");
						RJavaUtils.writeLine(output, "#' @rdname "+rName); 
						RJavaUtils.writeLine(output, rName + " <- function(jself,...) {" +
								LINE_SEPARATOR + "\t UseMethod(\"" + rName + "\")}");
						this.exportedGenerics.add(rName);
					}
				}

			}

			RJavaUtils.writeLine(output, "");
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Cast a list of parameters from R objects to the required java classes.
	 * @param parameters list of parameter types to cast to
	 * @param output where to write output to
	 */
	private void castParameters(List<? extends VariableElement> parameters, Writer output)
	{
		Iterator<? extends VariableElement> paramIter = parameters.iterator();
		while (paramIter.hasNext()) {
			VariableElement parameter = paramIter.next();

			//Must explicitly cast R objects to REXP references in java
			if (parameter.asType().toString().equals("org.rosuda.REngine.REXP")) {
				try {
					RJavaUtils.writeLine(output, 
							this.INDENT + parameter.getSimpleName() + 
								" <- .jcast(toJava(" + parameter.getSimpleName() + ")"+
										",new.class=\"org/rosuda/REngine/REXP\")");
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * @param fromName
	 * @param javaType
	 * @param output
	 * @throws IOException
	 */
	private void createWrapperObject(String jobjName,String robjName, TypeMirror javaType, Writer output) throws IOException {
		logger.debug("Wrapping object type: " + javaType.toString() + " of TypeMirror: " + javaType.getClass());
		if (RJavaUtils.canCastAsPrimitive(javaType)) {
			logger.debug("Casting as a primitive");
			RJavaUtils.writeLine(output, "\t" + robjName + "<-" + jobjName);
		} else if (!(javaType instanceof DeclaredType)) {
			logger.debug("Not a DeclaredType");
			RJavaUtils.writeLine(output, "\t" + robjName + "<-" + jobjName);
		} else {
			if (this.exportedClasses.contains(javaType)){
				if (useS4Methods){
					// Export the classes and the inheritance chain at the end, so you don't have to pollute the 
					// inheritance chain and the namespace
					// So for now, just call the R constructor for the class you create at the end of the round
					// (You need to know what R parent(s) to include, plus it needs to be loaded first in "0classes.R")
					RJavaUtils.writeLine(output, "\t"+robjName + " <- new(\"" + javaType.toString() + "\", jreturnobj)");
				}else {
					RJavaUtils.writeLine(output, "\t"+robjName + " <- list(jobj=jreturnobj)");

					//  Add all classes right here for S3, since S3 doesn't search a hierarchy
					RJavaUtils.writeLine(output, "\tclass("+robjName + ") <- c(\"" + javaType.toString() + "\"");
					ArrayList<TypeMirror> parents = new ArrayList<TypeMirror>(this.mTypes.directSupertypes(javaType));
					HashSet<TypeMirror> visited = new HashSet<TypeMirror>();  // Avoid repeating classes
					while (!parents.isEmpty()) {
						TypeMirror next = (TypeMirror)parents.remove(0);
						if (!visited.contains(next)) {
							// Only add S3 classes that are used
							if (this.exportedClasses.contains(next)){
								RJavaUtils.write(output, ",\"" + next.toString() + "\"");
							}
							parents.addAll(this.mTypes.directSupertypes(next));
							visited.add(next);
						}
					}
					RJavaUtils.writeLine(output, ")" + LINE_SEPARATOR);
				}

				//TODO I think I wrote this without thinking through the consequences 
				// .jcache should at least be configured in the annotation if called at all
				/*
				boolean serializable = false;
				for (Class parent : javaType.getClass().getInterfaces()) {
					if (parent.equals(Serializable.class)) {
						serializable = true;
					}
				}
				if (serializable) {
					RJavaUtils.writeLine(output, "\t.jcache(obj$jobj)" + LINE_SEPARATOR);
				}
				 */
			}else {
				RJavaUtils.writeLine(output, "\t" + robjName + "<-" + jobjName);
			}
		}
	}

	private void writeParameters(Collection<? extends VariableElement> parameters, Writer output, boolean inlineComments) throws IOException
	{
		boolean first = true;
		Iterator<? extends VariableElement> paramIter = parameters.iterator();
		while (paramIter.hasNext()) {
			VariableElement parameter = paramIter.next();
			RJavaUtils.write(output, this.INDENT + parameter.getSimpleName());
			if (paramIter.hasNext())
				RJavaUtils.writeLine(output, ", ");
			else {
				RJavaUtils.writeLine(output, "");
			}
			if (inlineComments) {
				RJavaUtils.writeLine(output, this.INDENT + "### will be converted to a Java object of class: \"" + parameter.toString() + "\"");
			}

			first = false;
		}
	}

	public void roundComplete()
	{
		logger.info("RoundComplete.  Closing files...");
		try {
			for (Map.Entry entry : this.inputOutputMap.entrySet()) {
				logger.info("Closing file " + (String)entry.getKey());
				Writer output = (Writer)entry.getValue();
				output.close();
			}
			logger.info("Files closed.");
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		this.inputOutputMap.clear();
		this.exportedClasses.clear();
	}

	private class RJavaVisitor extends SimpleElementVisitor6<Void, Void>
	{
		private RJavaVisitor()
		{
		}

		protected Void defaultAction(Element e, Void p)
		{
			throw new UnsupportedOperationException("@RJava annotations can only be processed for executables(ie constructors, methods): " + e);
		}

		public Void visitExecutable(ExecutableElement e, Void p) {
			AnnotateRProcessor.this.processDeclaration(e);
			return null;
		}
		public Void visitPackage(PackageElement e, Void p) {
			throw new UnsupportedOperationException("@RJava annotations can only be processed for executables(ie constructors, methods): " + e);
		}

		public Void visitType(TypeElement e, Void p) {
			throw new UnsupportedOperationException("@RJava annotations can only be processed for executables(ie constructors, methods): " + e);
		}

		public Void visitTypeParameter(TypeParameterElement e, Void p) {
			throw new UnsupportedOperationException("@RJava annotations can only be processed for executables(ie constructors, methods): " + e);
		}

		public Void visitVariable(VariableElement e, Void p) {
			throw new UnsupportedOperationException("@RJava annotations can only be processed for executables(ie constructors, methods): " + e);
		}
	}
}