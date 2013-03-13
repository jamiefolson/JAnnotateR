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
import javax.lang.model.type.ExecutableType;
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
	HashMap<String, Writer> mInputOutputMap = new HashMap<String, Writer>();
	Types mTypes;
	HashSet<String> mExportedSymbols = new HashSet<String>();
	HashSet<String> mExportedGenerics = new HashSet<String>();
	/**
	 * Export as TypeElement objects not TypeMirror, so you don't get screwed by
	 * the specialization of a generic not inheriting from the generic.  While 
	 * that's correct for java, R types are different, plus, it's not compiled,
	 * so you can't do static type-checking.
	 * 
	 * This way, at least specialized classes will inherit the generic's methods.
	 */
	HashSet<TypeMirror> mExportedTypes = new HashSet<TypeMirror>();
	HashMap<TypeElement,List<ExecutableElement>> mExportedClassMethods = 
			new HashMap<TypeElement,List<ExecutableElement>>();
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
		if (this.mExportedSymbols.contains(rQualifiedName)) {
			throw new RuntimeException("Duplicate Name: '" + rName + "' defined multiply for Java type '" + 
					rQualifiedType + "'");
		}

		this.mExportedSymbols.add(rQualifiedName);
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
			//TypeElement rjavaAnnotationType = annotations.iterator().next();
			elements = roundEnv.getElementsAnnotatedWith(RJava.class);
			
			// Collect methods
			for (Element element : elements) {
				if (!(element instanceof ExecutableElement)){
					throw new UnsupportedOperationException("@RJava annotations can only be processed for " +
							"executables(e.g. constructors, methods), instead found: " + element);	
				}
				ExecutableElement d = (ExecutableElement)element;
				this.addClassMethod((TypeElement) element.getEnclosingElement(),d);
			}
			// Process classes
			for (Element element : elements) {
				this.processType(element.getEnclosingElement().asType());
				//logger.debug("Need to export caller type: " + element.getEnclosingElement().asType().toString());
			}
			
			for (Element element : elements)
			{
				logger.debug("Element name: " + element.getSimpleName());
				logger.debug("Element kind: " + element.getKind());
				logger.debug("Element type: " + element.asType());
				ExecutableElement d = (ExecutableElement)element;
				if (d.getKind() == ElementKind.CONSTRUCTOR) {
					continue; // Constructors have no return type
				}
				//this.addClassMethod((TypeElement) element.getEnclosingElement(),d);
				TypeMirror returnType = d.getReturnType();
				if (returnType == null){
					continue; // void methods have no return type
				}
				if(this.isExportedType(returnType)){
					logger.debug("Need to export return type: " + returnType);
					this.processType(returnType);
				}
			}

			// Generate necessary classes here.  There WILL ONLY EVER BE ONE ROUND.
			// Multiple rounds happen when annotations result in new java files, which have to be processed for annotations.

			// Generate default package .onLoad
			try {
				Writer output;
				output = this.getWriter("zzz.R");
				RJavaUtils.writeLine(output, ".onLoad <- function(libname,pkgname){");
				RJavaUtils.writeLine(output, "\t.jpackage(pkgname, lib.loc = libname)");
				RJavaUtils.writeLine(output, "}");

				if (useS4Methods){
					// Define and export S4 generic methods
					output = this.getWriter("0methods.R");
					for (String rName : this.mExportedGenerics){
						// Create a generic method if none exists
						RJavaUtils.writeLine(output, "#' @export"); 
						RJavaUtils.writeLine(output, "#' @rdname "+rName); 
						RJavaUtils.writeLine(output, "setGeneric(\"" + rName + "\", "+
								"function(obj,...) standardGeneric(\"" + rName + "\"))");
					}

					// Define and export S4 classes
					output = this.getWriter("0classes.R");
					HashSet<TypeMirror> toExport = new HashSet<TypeMirror>(this.mExportedTypes);
					while(!toExport.isEmpty()){
						this.writeClassExport(output,toExport,toExport.iterator().next());
					}

				}
			} catch (javax.annotation.processing.FilerException e){
				throw new RuntimeException("More than one round of processing created R code!  This breaks the processor!");
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException("Error writing to file: "+e.getMessage());
			}
		}
		roundComplete();
		return true;
	}

	private boolean isExportedType(TypeMirror type,Element typeCls){
		// Export this specific type
		if (this.mExportedTypes.contains(type)){
			logger.debug("Found exported type: "+type);
			return true;
		}
		if (typeCls instanceof TypeElement){
			if (this.mExportedClassMethods.containsKey(typeCls)){
				logger.debug("Found exported class: "+typeCls);
				return true;
			}
		}else if (typeCls instanceof TypeParameterElement){
			for (TypeMirror t : ((TypeParameterElement)typeCls).getBounds()){
				if (this.isExportedType(t)){
					return true;
				}
			}
		}else {
			logger.info("Unknown type element: "+typeCls+ " for type: "+type);
			//return false;
		}
		// Export an ancestor
		for (TypeMirror ancestor : this.mTypes.directSupertypes(type)){
			if (this.isExportedType(ancestor)){
				return true;
			}
		}
		
		return false;
	}
	
	private boolean isExportedElement(TypeElement typeCls){
		return this.isExportedType(typeCls.asType(), typeCls);
	}

	private boolean isExportedType(TypeMirror type){
		return this.isExportedType(type, 
					this.mTypes.asElement(type));
	}

	private void processType(TypeMirror type){
		logger.debug("Entering root type: " + type);
		TypeElement typeCls = (TypeElement) this.mTypes.asElement(type);
		String filename = getDeclarationFilename(typeCls);
		Writer output;
		try {
			output = getWriter(filename + ".R");
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new RuntimeException("Error writing to file: "+e.getMessage());
		}
		this.processType(output, type);
		this.mExportedTypes.add(type);
	}
	private void processType(Writer output, TypeMirror type){
		// Only process each class once
		if (this.mExportedTypes.contains(type)){
			return;
		}
		TypeElement typeCls = (TypeElement) this.mTypes.asElement(type);
		logger.debug("Entering type: " + type.toString());
		for (TypeMirror ancestor : this.mTypes.directSupertypes(type)){
			this.processType(output,ancestor);
		}
		
		if (this.mExportedClassMethods.containsKey(typeCls)){
			logger.debug("Processing methods for type: " + type.toString());


			for (ExecutableElement element : this.mExportedClassMethods.get(typeCls))
			{
				ExecutableType elementType = 
						(ExecutableType) this.mTypes.asMemberOf(
								(DeclaredType) type, 
								element);
				logger.debug("Element name: " + element.getSimpleName());
				logger.debug("Element kind: " + element.getKind());
				logger.debug("Element type: " + element.asType());

				this.processDeclaration(element, 
						type,
						elementType.getParameterTypes(),
						elementType.getReturnType());
			}
			this.mExportedTypes.add(type);
		}else {
			logger.debug("No methods for type: " + typeCls.toString());
		}
	}

	private void addClassMethod(TypeElement type,
			ExecutableElement d) {
		List<ExecutableElement> methods = this.mExportedClassMethods.get(type);
		if (!this.mExportedClassMethods.containsKey(type)){
			methods = new ArrayList<ExecutableElement>();
			this.mExportedClassMethods.put(type, methods);
		}
		methods.add(d);
	}

	/**
	 * Recursively defines and exports S4 classes so that they are guaranteed to be written in order.
	 * @param output
	 * @param toExport
	 * @param type
	 * @throws IOException
	 */
	private void writeClassExport(Writer output, HashSet<TypeMirror> toExport,
			TypeMirror type) throws IOException{
		toExport.remove(type);
		logger.debug("Exporting S4 class : "+type.toString());
		//logger.debug("Exporting S4 class : "+((DeclaredType)type).asElement());
		ArrayList<TypeMirror> ancestors = new ArrayList<TypeMirror>();
		//ancestors.add(type.getSuperclass());
		//ancestors.addAll(type.getInterfaces());
		ancestors.addAll(this.mTypes.directSupertypes(type));
		ArrayList<TypeMirror> parents = new ArrayList<TypeMirror>();
		while (!ancestors.isEmpty()){
			TypeMirror next = ancestors.remove(0);
			//TypeElement nextType = (TypeElement) this.mTypes.asElement(next);
			// Only contain the first parent that you are also exporting.
			// Currently exports both class and interface parents, since S4 allows multiple inheritance
			// This is a little confusing, since ALL of the "contain"-ed R classes have an external jobjRef slot
			// However, since they're all named the same, there shouldn't be any problems.
			if (this.mExportedTypes.contains(next)){
				logger.debug("Adding parent : "+next.toString());
				parents.add(next);
				// Make sure it's exported first!
				if (toExport.contains(next)){
					this.writeClassExport(output, toExport, next);
				}
			}else {
				// If you're not exporting this parent, make sure to 
				// check ancestors for exporting
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

	/**
	 * Get or create an output Writer for the given input filename
	 * @param name input filename
	 * @return Writer for the output file
	 * @throws IOException if the file could not be created
	 */
	private Writer getWriter(String name) throws IOException {
		Writer output = this.mInputOutputMap.get(name);
		if (output != null) return output;
		FileObject fileObject = null;

		fileObject = this.mEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "R", name, new Element[0]);

		File file = FileUtils.toFile(fileObject.toUri().toURL());
		logger.debug("creating: " + file.getAbsolutePath());

		output = new PrintWriter(fileObject.openOutputStream());
		this.mInputOutputMap.put(name, output);

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
	 * @param execElem
	 */
	public void processDeclaration(ExecutableElement execElem, 
			TypeMirror callerType,
			List<? extends TypeMirror> paramTypes,
			TypeMirror returnType)
	{
		try
		{
			Set<Modifier> modifiers = execElem.getModifiers();

			boolean isConstructor = false;
			if (execElem.getKind() == ElementKind.CONSTRUCTOR) {
				isConstructor = true;
			}
			boolean isStatic = false;
			if (modifiers.contains(Modifier.STATIC)) {
				isStatic = true;
			}

			//  Looks awkward, but lots of naming stuff to get
			RJava annotation = execElem.getAnnotation(RJava.class);
			String typeName = callerType.toString();
			TypeElement typeElement = (TypeElement) this.mTypes.asElement(callerType);
			String cannonicalTypeName = typeElement.toString();
			String simpleName = execElem.getSimpleName().toString();

			// Figure out where to write output
			String filename = getDeclarationFilename(execElem);
			Writer output = getWriter(filename);
			logger.debug("Writing " +cannonicalTypeName+
					"." + simpleName + " to " + filename);

			if (isConstructor) { // constructor's have no "return type" so assign here
				returnType = callerType;
			}

			//  Allow for explicit assignment to method name
			String rName = annotation.rName();
			if (rName.equals("[default]")) {
				rName = simpleName;
				// Prepend "new" to typeName for constructors? No, it's goofy
				if (isConstructor) {
					rName = typeName;
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

			String javadoc = this.mElements.getDocComment(execElem);
			if (javadoc == null) {
				RJavaUtils.writeLine(output, "#' TODO:Function documentation");
			} else {
				RJavaUtils.writeLine(output, "#' " + javadoc.replaceAll(LINE_SEPARATOR, LINE_SEPARATOR+"#' "));
				RJavaUtils.writeLine(output, "#' Previous documentation autogenerated from javadoc");
			}
			if (!isStatic && !isConstructor){
				RJavaUtils.writeLine(output, "#' @param obj An rJava jobjRef of java type " + cannonicalTypeName + " to be operated on");
			}
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
				if (execElem.getParameters().size() > 0) {
					RJavaUtils.writeLine(output, ", ");
				}
				RJavaUtils.writeLine(output, "" + LINE_SEPARATOR + "### object of type " + cannonicalTypeName + " to be operated on");
			}

			writeParameters(execElem.getParameters(), output);
			RJavaUtils.writeLine(output, ") {");

			castParameters(execElem.getParameters(), paramTypes,output);

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
						String staticTypeStr = RJavaUtils.typeToRAbbreviation(execElem.getEnclosingElement().asType(), false);

						RJavaUtils.writeLine(output, "\"" + staticTypeStr + "\", ");
					} else {
						RJavaUtils.writeLine(output, "obj, ");
					}

					RJavaUtils.writeLine(output, "\t\t\t\"" + returnTypeStr + "\", ");
					RJavaUtils.write(output, "\t\t\t\"" + execElem.getSimpleName() + "\"");
				}

				if (execElem.getParameters().size() > 0) {
					RJavaUtils.writeLine(output, ", ");
				}
				writeParameters(execElem.getParameters(), output);
				RJavaUtils.writeLine(output, ")"); // end jcall/jnew

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
					this.mExportedGenerics.add(rName);
					RJavaUtils.writeLine(output, "#' @export"); 
					RJavaUtils.writeLine(output, "#' @rdname "+rName); 
					RJavaUtils.writeLine(output, "setMethod(" + rName + ", " + 
							"\""+cannonicalTypeName + "\""+ 
							", "+ rQualifiedName +")");
				}
				else
				{ 
					if (!this.mExportedGenerics.contains(rName)){
						RJavaUtils.writeLine(output, "#' @export");
						RJavaUtils.writeLine(output, "#' @rdname "+rName); 
						RJavaUtils.writeLine(output, rName + " <- function(jself,...) {" +
								LINE_SEPARATOR + "\t UseMethod(\"" + rName + "\")}");
						this.mExportedGenerics.add(rName);
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
	private void castParameters(
			List<? extends VariableElement> parameters,
			List<? extends TypeMirror> types,
			Writer output)
	{
		Iterator<? extends VariableElement> paramIter = parameters.iterator();
		Iterator<? extends TypeMirror> typeIter = types.iterator();
		while (paramIter.hasNext()) {
			VariableElement parameter = paramIter.next();
			TypeMirror type = typeIter.next();

			//Must explicitly cast R objects to REXP references in java
			if (type.toString().equals("org.rosuda.REngine.REXP")) {
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
		logger.debug("Wrapping object type: " + javaType.toString());
		if (RJavaUtils.canCastAsPrimitive(javaType)) {
			logger.debug("Casting as a primitive");
			RJavaUtils.writeLine(output, "\t" + robjName + "<-" + jobjName);
		} else if (!(javaType instanceof DeclaredType)) {
			logger.debug("Not a DeclaredType");
			RJavaUtils.writeLine(output, "\t" + robjName + "<-" + jobjName);
		} else {
			TypeElement javaTypeElement = (TypeElement) this.mTypes.asElement(javaType);
			if (this.mExportedTypes.contains(javaType)){
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
						TypeElement nextTypeElement = (TypeElement) this.mTypes.asElement(next);
						if (!visited.contains(next)) {
							// Only add S3 classes that are used
							if (this.mExportedTypes.contains(nextTypeElement)){
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

	private void writeParameters(Collection<? extends VariableElement> parameters, 
				Writer output) throws IOException
	{
		Iterator<? extends VariableElement> paramIter = parameters.iterator();
		while (paramIter.hasNext()) {
			VariableElement parameter = paramIter.next();
			RJavaUtils.write(output, this.INDENT + parameter.getSimpleName());
			if (paramIter.hasNext())
				RJavaUtils.writeLine(output, ", ");
			else {
				RJavaUtils.writeLine(output, "");
			}
		}
	}

	public void roundComplete()
	{
		logger.info("RoundComplete.  Closing files...");
		try {
			for (Map.Entry entry : this.mInputOutputMap.entrySet()) {
				logger.info("Closing file " + (String)entry.getKey());
				Writer output = (Writer)entry.getValue();
				output.close();
			}
			logger.info("Files closed.");
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		this.mInputOutputMap.clear();
		this.mExportedTypes.clear();
		this.mExportedGenerics.clear();
		this.mExportedSymbols.clear();
		this.mExportedClassMethods.clear();
	}
}