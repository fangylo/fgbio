package com.fulcrumgenomics.util

import java.nio.file.Path

import com.fulcrumgenomics.commons.io.{Rscript => NewRscipt}

import scala.util.Try

object Rscript extends {

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  val Executable: String = NewRscipt.Executable

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  val Suffix: String = NewRscipt.Suffix

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  val TestCommand: Seq[String] = NewRscipt.TestCommand

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  val VersionFlag: String = NewRscipt.VersionFlag

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  lazy val Available: Boolean = NewRscipt.Available

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  lazy val Version: String = NewRscipt.Version

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  def execIfAvailable(script: Path, args: String*): Unit = NewRscipt.execIfAvailable(script, args: _*)

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  def execIfAvailable(scriptResource: String, args: String*): Unit = NewRscipt.execIfAvailable(scriptResource, args: _*)

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  def exec(scriptResource: String, args: String*): Unit = NewRscipt.exec(scriptResource, args: _*)

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  def exec(script: Path, args: String*): Unit = NewRscipt.exec(script, args: _*)

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  def TestModuleCommand(module: String): Seq[String] = NewRscipt.TestModuleCommand(module)

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  def TestModuleCommand(modules: Seq[String]): Seq[Seq[String]] = NewRscipt.TestModuleCommand(modules)

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  def IsModuleAvailable(module: String): Boolean = NewRscipt.IsModuleAvailable(module)

  @deprecated(since="v1.21", message = "Use `com.fulcrumgenomics.commons.io.CommandLineTool.Rscript` instead.")
  def IsModuleAvailable(modules: Seq[String]): Boolean = NewRscipt.IsModuleAvailable(modules)
}