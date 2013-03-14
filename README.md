# Automatically generate R packages from Java using annotations

The [RJava](http://www.rforge.net/rJava/) project makes it quite easy to call java code from R, but it lacks some of the features of 
[RCpp modules](http://cran.r-project.org/web/packages/Rcpp/vignettes/Rcpp-modules.pdf).  Specifically, it utilizes reflection to dynamically
call java, rather than generate actual R code, which means that although the java functions are accessible, they are not documented or 
visible.  This project seeks to fill that void by allowing users to generate documented and exported S4 classes and methods that 
map onto the java class hierarchy simply by annotating the java source code.

There are three subprojects here:

* `jannotater-core` contains the definition of the `@RJava` annotation as well as the annotation processor.
* `gradle-plugin-jannotater` contains a gradle plugin to simplify the generation of R packages from annotationions.
* `jannotater-test` contains an example of java code properly annotated and built with the gradle plugin into an R package.


