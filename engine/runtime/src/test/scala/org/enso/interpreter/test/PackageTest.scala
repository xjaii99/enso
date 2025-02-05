package org.enso.interpreter.test

import org.enso.pkg.PackageManager
import org.enso.polyglot.{LanguageInfo, PolyglotContext, RuntimeOptions}
import org.graalvm.polyglot.{Context, Value}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayOutputStream, File}
import java.nio.file.Paths

trait PackageTest extends AnyFlatSpec with Matchers with ValueEquality {
  val output = new ByteArrayOutputStream()

  def evalTestProject(name: String): Value = {
    val pkgPath =
      new File(getClass.getClassLoader.getResource(name).getPath)
    val pkg        = PackageManager.Default.fromDirectory(pkgPath).get
    val mainFile   = pkg.mainFile
    val mainModule = pkg.moduleNameForFile(mainFile)
    val context = Context
      .newBuilder(LanguageInfo.ID)
      .allowExperimentalOptions(true)
      .allowAllAccess(true)
      .option(RuntimeOptions.PROJECT_ROOT, pkgPath.getAbsolutePath)
      .option(
        RuntimeOptions.LANGUAGE_HOME_OVERRIDE,
        Paths
          .get("../../test/micro-distribution/component")
          .toFile
          .getAbsolutePath
      )
      .option(RuntimeOptions.EDITION_OVERRIDE, "0.0.0-dev")
      .option(RuntimeOptions.STRICT_ERRORS, "true")
      .option(RuntimeOptions.DISABLE_IR_CACHES, "true")
      .out(output)
      .in(System.in)
      .option(RuntimeOptions.LOG_LEVEL, "WARNING")
      .logHandler(System.err)
      .build()
    context.initialize(LanguageInfo.ID)
    val executionContext = new PolyglotContext(context)
    InterpreterException.rethrowPolyglot(
      {
        val topScope        = executionContext.getTopScope
        val mainModuleScope = topScope.getModule(mainModule.toString)
        val assocCons       = mainModuleScope.getAssociatedType
        val mainFun         = mainModuleScope.getMethod(assocCons, "main").get
        mainFun.execute()
      },
      output = Some(output)
    )
  }

  def consumeOut: List[String] = {
    val result = output.toString
    output.reset()
    result.linesIterator.toList
  }
}
