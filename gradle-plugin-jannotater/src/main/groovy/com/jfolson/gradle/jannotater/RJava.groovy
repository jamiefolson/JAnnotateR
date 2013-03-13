package com.sonamine.gradle

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.plugins.BasePlugin
import com.sonamine.gradle.RPackagePlugin

class RJavaPluginExtension {
  def String packageDir = "src"
  def String packageName = null
  def String buildDir = null
}

class RJavaPlugin implements Plugin<Project> {
  void apply(Project project) {
    project.apply(plugin: RPackagePlugin.class)
    project.extensions.create("rjava",RJavaPluginExtension.class)
    project.gradle.projectsEvaluated {

    project.task('rjava',type:JavaCompile, dependsOn: project.tasks.DESCRIPTION) {
      project.gradle.projectsEvaluated({
        inputs.dir "${project.projectDir}/${project.rpackage.packageDir}"
        outputs.dir "${project.rpackage.buildDir}/"
        })
      doFirst {
        commandLine = ['R','-e',
          "library(roxygen2,quietly=TRUE,verbose=FALSE);"+
          "roxygenize(package.dir='${project.rpackage.packageDir}',"+
          "roxygen.dir='${project.rpackage.buildDir}',"+
          "roclets = c(\"collate\", \"namespace\", \"rd\", \"testthat\"))"]
        workingDir project.projectDir
      }
    }
    
    project.task('buildRPackage',type:Exec, dependsOn: project.roxygenize) {
      project.gradle.projectsEvaluated({
        inputs.dir "${project.rpackage.buildDir}"
        outputs.file "${project.buildDir}/${project.rpackage.packageName}_${project.version}.tar.gz"
        })
      doFirst {
        commandLine = ['R','CMD','build',
                    "${project.rpackage.buildDir}"]
        workingDir project.buildDir
        println commandLine.join(" ")
      }
    }
    project.build.dependsOn project.buildRPackage

    if (project.tasks.findByPath('test')==null) {
      project.task('test') << {
        println "Check the validity of the build"
      }
    }
    project.task('checkRPackage',type:Exec, dependsOn: project.build) {
      project.gradle.projectsEvaluated({
        inputs.dir "${project.rpackage.buildDir}/"
        outputs.dir "${project.buildDir}/${project.rpackage.packageName}.Rcheck/"
        })
      doFirst {
        commandLine = ['R','CMD','check',"-o",
                    "${project.buildDir}",
                    "${project.rpackage.buildDir}"]
        workingDir project.projectDir
        println commandLine.join(" ")
      }
    }
    project.test.dependsOn project.checkRPackage

    project.assemble.dependsOn project.build
//    project.task('assembleRPackage',type: Tar, dependsOn: project.build) {
//
//        baseName = project.name
//        version = project.version
//        classifier = "src"
//        extension = "tar.gz"
//        compression = Compression.GZIP
//        project.gradle.projectsEvaluated({
//          destinationDir = project.file(project.buildDir)
//          baseName = "${project.rpackage.packageName}"
//          from "${project.rpackage.buildDir}"
//          into "${project.rpackage.packageName}/"
//          inputs.dir "${project.rpackage.buildDir}/"
//          outputs.file "${project.buildDir}/${archiveName}"
//          })
//    }
//    project.assemble.dependsOn project.assembleRPackage

    if (project.tasks.findByPath('install')==null) {
      project.task('install', dependsOn: project.build) << {
        println "Install any installable files"
      }
    }
    project.task('installRPackage',type:Exec) {
      project.gradle.projectsEvaluated({
        inputs.file "${project.buildDir}/${project.rpackage.packageName}_${project.version}.tar.gz"
        outputs.file "${project.buildDir}/${project.rpackage.packageName}_${project.version}.tar.gz"
        })
      doFirst {
        commandLine = ['R','CMD','INSTALL',"${project.rpackage.packageName}_${project.version}.tar.gz"]
        workingDir = project.file(project.buildDir)
        println commandLine.join(" ")
      }
    }
    project.install.dependsOn project.installRPackage
  }
}
