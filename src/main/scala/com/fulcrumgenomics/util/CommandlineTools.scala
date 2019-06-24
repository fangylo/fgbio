/*
 * The MIT License
 *
 * Copyright (c) 2017 Fulcrum Genomics LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.fulcrumgenomics.util

import java.nio.file.Path

import com.fulcrumgenomics.commons.util.LazyLogging

import scala.util.{Success, Try}

trait CommandLineTool extends LazyLogging {
  /** The name of the executable such as Rscript or gs. */
  val Executable: String

  /** Command used for the above executable. */
  val TestCommand: Seq[String]

  /** Exception class that holds onto an executable's exit/status code. */
  case class ToolException(status: Int) extends RuntimeException {
    override def getMessage: String = s"$Executable failed with exit code $status."
  }

  /** Returns true if the command can execute with no error. */
  def canExecute(command: String*): Boolean = {
    try {
      val process = new ProcessBuilder(command: _*).redirectErrorStream(true).start()
      process.waitFor() == 0
    }
    catch { case e: Exception => false }
  }

  /** Returns true if the tool is available and false otherwise. */
  lazy val Available: Boolean = {
    canExecute(Executable +: TestCommand:_*)
  }
}

trait CanRunScript {
  self: CommandLineTool =>
  /** Suffix for scripts that can be run by this tool. */
  val Suffix: String

  /** Executes a script from the classpath if the tested executable is available. */
  def execIfAvailable(scriptResource: String, args: String*): Try[Unit] =
    if (Available) exec(scriptResource, args:_*) else Success(Unit)

  /** Executes from a script stored at a Path if the tested executable is available. */
  def execIfAvailable(script: Path, args: String*): Try[Unit] =
    if (Available) exec(script, args:_*) else Success(Unit)

  /** Executes a script from the classpath. */
  def exec(scriptResource: String, args: String*): Try[Unit] =
    Try { writeResourceToTempFile(scriptResource) }.map(path => exec(path, args:_*))

  /** Executes from a script stored at a Path. */
  def exec(script: Path, args: String*): Try[Unit] = Try {
    val command = Executable +: script.toAbsolutePath.toString +: args
    val process = new ProcessBuilder(command:_*).redirectErrorStream(false).start()
    val pipe1   = Io.pipeStream(process.getErrorStream, logger.info)
    val pipe2   = Io.pipeStream(process.getInputStream, logger.debug)
    val retval  = process.waitFor()
    pipe1.close()
    pipe2.close()

    if (retval != 0) throw ToolException(retval)
  }

  /** Extracts a resource from the classpath and writes it to a temp file on disk. */
  private def writeResourceToTempFile(resource: String): Path = {
    val lines = Io.readLinesFromResource(resource).toSeq
    val path = Io.makeTempFile("script.", suffix = Suffix)
    path.toFile.deleteOnExit()
    Io.writeLines(path, lines)
    path
  }
}

trait Versioned {
  // Test version of the tool
  self: CommandLineTool =>
  val VersionFlag: String      = "--version"
  val TestCommand: Seq[String] = Seq(VersionFlag)
}

/** Test if specified module(s) are included with the tested executable. */
trait Modular {
  self: CommandLineTool =>
  def TestModuleCommand(module: String): Seq[String]
  def TestModuleCommand(modules: Seq[String]): Seq[Seq[String]] = modules.map(TestModuleCommand)
  def IsModuleAvailable(module: String): Boolean =
    canExecute(TestModuleCommand(module): _*)
  def IsModuleAvailable(modules: Seq[String]): Boolean =
    // Only returns true is all modules exist
    modules.map(IsModuleAvailable).forall(x => x == true)
}

object Rscript extends CommandLineTool with Versioned with Modular with CanRunScript {
  val Executable: String      = "Rscript"
  val Suffix: String          = ".R"
  def TestModuleCommand(module: String): Seq[String] = Seq(Executable, "-e", s"stopifnot(require('$module'))")
  override lazy val Available: Boolean = {
    // Only returns true if R executable exists and ggplot2 is installed
    val ToolAvailable: Boolean = canExecute(Executable +: Seq(VersionFlag):_*)
    val ModuleAvailable : Boolean = IsModuleAvailable(module = "ggplot2")
    Seq(ToolAvailable, ModuleAvailable).forall( _ == true)
  }
}

object GhostScript extends CommandLineTool with Versioned {
  val Executable: String = "gs"
}

object Python3 extends CommandLineTool with Versioned with Modular with CanRunScript {
  val Executable: String = "python3"
  val Suffix: String = ".py"
  def TestModuleCommand(module: String): Seq[String] = Seq(Executable, "-c", s"'import $module'")
  override val TestCommand: Seq[String] = Seq(VersionFlag)
//  val ModuleAvailable : Boolean = IsModuleAvailable(module = "numpy")
}
