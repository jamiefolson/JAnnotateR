# Automatically generate R packages from Java using annotations

The [RJava](http://www.rforge.net/rJava/) project makes it quite easy to call java code from R, but it lacks some of the features of 
[RCpp modules](http://cran.r-project.org/web/packages/Rcpp/vignettes/Rcpp-modules.pdf).  Specifically, it utilizes reflection to dynamically
call java, rather than generate actual R code, which means that although the java functions are accessible, they are not documented or 
visible.  This project seeks to fill that void by allowing users to generate documented and exported S4 classes and methods that 
map onto the java class hierarchy simply by annotating the java source code.

There are three subprojects here:

* `jannotater-rjava` contains the definition of the `@RJava` annotation as well as the annotation processor.
* `gradle-plugin-rjava` contains a gradle plugin to simplify the generation of R packages from annotationions.
* `jannotater-example` contains an example of java code properly annotated and built with the gradle plugin into an R package.


Here's the configuration for the `jannotater-example` project:
```
buildscript {
	repositories {
	  mavenLocal()
	  mavenCentral()
	}
	dependencies {
		classpath 'com.jfolson:gradle-plugin-rjava:0.1'
	}
}
  
apply plugin: 'java'
apply plugin: 'rjava'

version = "0.1"
repositories {
    mavenCentral()
    mavenLocal()
	flatDir(dirs: 'lib')
}

dependencies {
    compile 'com.jfolson:jannotater-rjava:0.1'
    rjava(project){ transitive = true }
	compile "org.rosuda.JRI:JRIEngine:0.9-3"
	compile "org.rosuda.JRI:JRI:0.9-3"
	compile "org.rosuda.JRI:REngine:0.9-3"
}

rpackage {
  srcDir = project.file('pkg')
  name = 'HelloWorld'
}
```

In this case, I had to add a directory of `RJava` artifacts, which are not
currently available on Maven Central.  The basic usage of these annotations is just:

```
	@RJava
	public static String staticHello() {
		return "Static Hello Java World!";
	}
```

This is all you need to do, and for this, you don't even need to include the `RJava` jars.  However, you can also specify what to return:

```
	@RJava(rReturn="{t(sapply(jreturnobj,.jevalArray))}")
	public int[][] sayMatrix()
	{
		return new int[][] { { 1, 2, 3 }, { 4, 5, 6 } };
	}
```

Or even get crazy and create R objects in Java (This is what requires the `RJava` dependencies):

```
	@RJava(rReturn=".robj",rBefore=".jengine(TRUE)")
	public static REXP testREngineCreate()
			throws REngineException, REXPMismatchException
			{
		REXP obj = new REXPString("created in java");
		REngine engine = new JRIEngine(Rengine.getMainEngine());
		engine.assign(".robj", obj);
		return obj;
	}
```

