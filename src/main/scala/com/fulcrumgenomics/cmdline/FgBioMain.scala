/*
 * The MIT License
 *
 * Copyright (c) 2016 Fulcrum Genomics LLC
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

package com.fulcrumgenomics.cmdline

import java.net.InetAddress
import java.nio.file.Paths
import java.text.DecimalFormat

import com.fulcrumgenomics.bam.api.{SamSource, SamWriter}
import com.fulcrumgenomics.cmdline.FgBioMain.FailureException
import com.fulcrumgenomics.commons.CommonsDef._
import com.fulcrumgenomics.commons.util.{LazyLogging, LogLevel, Logger}
import com.fulcrumgenomics.sopt.{Sopt, arg}
import com.fulcrumgenomics.sopt.cmdline.CommandLineProgramParserStrings
import com.fulcrumgenomics.util.Io
import com.intel.gkl.compression.{IntelDeflaterFactory, IntelInflaterFactory}
import htsjdk.samtools.{SAMFileWriterFactory, ValidationStringency}
import htsjdk.samtools.util.{BlockCompressedOutputStream, BlockGunzipper, IOUtil, SnappyLoader}


/** A little class so that we don't have to rely on SystemUtils from org.apache.commons:commons-lang3. */
private[cmdline] object SystemUtils {
  /** The OS name prefixes for Linux */
  private val LinuxNamePrefixes: Seq[String] = Seq("Linux", "LINUX")
  /** The OS name prefixes for Mac */
  private val MacNamePrefixes: Seq[String]   = Seq("Mac")
  /** The current OS name. */
  private val OsName: Option[String]         = getSystemProperty("os.name")
  /** The current OS architecture */
  private val OsArch: Option[String]         = getSystemProperty("os.arch")

  /** Gets a system property.  Returns None if not found or not allowed to look at. */
  private def getSystemProperty(property: String): Option[String] = {
    try {
      Option(System.getProperty(property))
    } catch { case _: SecurityException => None } // not allowed to look at this property
  }

  /** True if this OS is Linux, false otherwise. */
  private val IsOsLinux: Boolean = LinuxNamePrefixes.exists(prefix => OsName.exists(_.startsWith(prefix)))
  /** True if this OS is Mac, false otherwise. */
  private val IsOsMac: Boolean   = MacNamePrefixes.exists(prefix => OsName.exists(_.startsWith(prefix)))
  /** Returns true if the architecture is the given name, false otherwise. */
  private def IsOsArch(name: String): Boolean = OsArch.contains(name)

  /** True if the current system supports the Intel Inflater and Deflater, false otherwise. */
  val IntelCompressionLibrarySupported: Boolean = {
    if (!SystemUtils.IsOsLinux && !SystemUtils.IsOsMac) false
    else if (SystemUtils.IsOsArch("ppc64le")) false
    else true
  }
}

/**
  * Main program for fgbio that loads everything up and runs the appropriate sub-command
  */
object FgBioMain {
  /** The main method */
  def main(args: Array[String]): Unit = new FgBioMain().makeItSoAndExit(args)

  /**
    * Exception class intended to be used by [[FgBioMain]] and [[FgBioTool]] to communicate
    * non-exceptional(!) failures when running a tool.
    */
  case class FailureException private[cmdline] (exit:Int = 1, message:Option[String] = None) extends RuntimeException
}

class FgBioCommonArgs
( @arg(doc="Use asynchronous I/O where possible, e.g. for SAM and BAM files.") val asyncIo: Boolean = false,
  @arg(doc="Default GZIP compression level, BAM compression level.")           val compression: Int = 5,
  @arg(doc="Directory to use for temporary files.")                            val tmpDir: DirPath  = Paths.get(System.getProperty("java.io.tmpdir")),
  @arg(doc="Minimum severity log-level to emit.")                              val logLevel: LogLevel = LogLevel.Info,
  @arg(doc="Validation stringency for SAM/BAM reading.")                       val samValidationStringency: Option[ValidationStringency] = None
) {

  SamSource.DefaultUseAsyncIo = asyncIo
  SamWriter.DefaultUseAsyncIo = asyncIo

  SamWriter.DefaultCompressionLevel = compression
  BlockCompressedOutputStream.setDefaultCompressionLevel(compression)
  IOUtil.setCompressionLevel(compression)
  Io.compressionLevel = compression

  Io.tmpDir = tmpDir
  System.setProperty("java.io.tmpdir", tmpDir.toAbsolutePath.toString)

  Logger.level = this.logLevel

  samValidationStringency.foreach { stringency => SamSource.DefaultValidationStringency = stringency }
}

class FgBioMain extends LazyLogging {
  /** A main method that invokes System.exit with the exit code. */
  def makeItSoAndExit(args: Array[String]): Unit = System.exit(makeItSo(args))

  /** A main method that returns an exit code instead of exiting. */
  def makeItSo(args: Array[String]): Int = {
    // Turn down HTSJDK logging
    htsjdk.samtools.util.Log.setGlobalLogLevel(htsjdk.samtools.util.Log.LogLevel.WARNING)

    // Use the Intel Inflater/Deflater if available
    if (SystemUtils.IntelCompressionLibrarySupported) {
      BlockCompressedOutputStream.setDefaultDeflaterFactory(new IntelDeflaterFactory)
      BlockGunzipper.setDefaultInflaterFactory(new IntelInflaterFactory)
    }

    val startTime = System.currentTimeMillis()
    val exit      = Sopt.parseCommandAndSubCommand[FgBioCommonArgs,FgBioTool](name, args, Sopt.find[FgBioTool](packageList)) match {
      case Sopt.Failure(usage) =>
        System.err.print(usage())
        1
      case Sopt.CommandSuccess(cmd) =>
        unreachable("CommandSuccess should never be returned by parseCommandAndSubCommand.")
      case Sopt.SubcommandSuccess(commonArgs, subcommand) =>
        val name = subcommand.getClass.getSimpleName
        try {
          printStartupLines(name, args, commonArgs)
          subcommand.execute()
          printEndingLines(startTime, name, true)
          0
        }
        catch {
          case ex: FailureException =>
            val banner = "#" * ex.message.map(_.length).getOrElse(80)
            logger.fatal(banner)
            logger.fatal("Execution failed!")
            ex.message.foreach(msg => msg.lines.foreach(logger.fatal))
            logger.fatal(banner)
            printEndingLines(startTime, name, false)
            ex.exit
          case ex: Throwable =>
            printEndingLines(startTime, name, false)
            throw ex
        }
    }

    exit
  }

  /** The name of the toolkit, used in printing usage and status lines. */
  protected def name: String = "fgbio"

  /** Prints a line of useful information when a tool starts executing. */
  protected def printStartupLines(tool: String, args: Array[String], commonArgs: FgBioCommonArgs): Unit = {
    val version    = CommandLineProgramParserStrings.version(getClass, color=false).replace("Version: ", "")
    val host       = InetAddress.getLocalHost.getHostName
    val user       = System.getProperty("user.name")
    val jreVersion = System.getProperty("java.runtime.version")
    val snappy     = if (new SnappyLoader().isSnappyAvailable) "with snappy" else "without snappy"
    val inflater   = if (SystemUtils.IntelCompressionLibrarySupported) "JdkInflater" else "IntelInflater"
    val deflater   = if (SystemUtils.IntelCompressionLibrarySupported) "JdkDeflater" else "IntelDeflater"
    logger.info(s"Executing $tool from $name version $version as $user@$host on JRE $jreVersion $snappy, $inflater, and $deflater")
  }

  /** Prints a line of useful information when a tool stops executing. */
  protected def printEndingLines(startTime: Long, name: String, success: Boolean): Unit = {
    val elapsedMinutes: Double = (System.currentTimeMillis() - startTime) / (1000d * 60d)
    val elapsedString: String = new DecimalFormat("#,##0.00").format(elapsedMinutes)
    val verb = if (success) "completed" else "failed"
    logger.info(s"$name $verb. Elapsed time: $elapsedString minutes.")
  }

  /** The packages we wish to include in our command line **/
  protected def packageList: List[String] = List[String]("com.fulcrumgenomics")
}
