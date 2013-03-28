package com.jfolson.gradle.jannotater

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.plugins.BasePlugin
import com.jfolson.gradle.r.RPackagePlugin
import org.gradle.api.tasks.compile.JavaCompile

class RJavaPluginExtension {
  def String packageDir = "src"
  def String packageName = null
  def String buildDir = null
}

class RJavaPlugin implements Plugin<Project> {
  void apply(Project project) {
    project.apply(plugin: RPackagePlugin.class)
	if (project.configurations.findByName("rjava")==null){
		project.configurations.add("rjava")
	}
    project.dependencies {
        rjava "com.jfolson:gradle-plugin-rjava:0.1"
    }
    //project.extensions.create("rjava",RJavaPluginExtension.class)
	project.task("downloadJars", type:Copy){
		project.afterEvaluate {
			from project.configurations.rjava
			into "${project.rpackage.srcDir}/inst/java"
		}
	}
	
	project.task("copyRPackageSource", type:Copy, dependsOn: project.downloadJars) {
		project.afterEvaluate {
			from project.rpackage.srcDir
			into project.rpackage.buildDir
		}
	}
	
	project.task("generateRJava", type: JavaCompile, 
		dependsOn :project.copyRPackageSource) {
		project.afterEvaluate {
			source = project.sourceSets.main.java
            classpath = project.files(
                    project.configurations.rjava,
                    project.configurations.compile)
			destinationDir = project.rpackage.buildDir
		}
		options.compilerArgs = ["-processor",
		"com.jfolson.jannotater.AnnotateRProcessor","-proc:only"]
		doFirst {
			//classpath = project.configurations.compile
			//classpath += project.configurations.annotations
		}
	}
	
	project.roxygenize.rpackage.srcDir = project.rpackage.buildDir
	project.roxygenize.dependsOn project.copyRPackageSource
	project.roxygenize.dependsOn project.generateRJava
  }
}
