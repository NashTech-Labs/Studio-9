package com.sentrana.sbt.common.plugins

import sbt._

import com.typesafe.sbt.SbtScalariform._

import scalariform.formatter.preferences._

object Scalariform {

  lazy val settings: Seq[Setting[_]] =
    scalariformSettings ++
      setPreferences(basePreferences)

  lazy val basePreferences: IFormattingPreferences =
    defaultPreferences
      .setPreference(AlignArguments, true)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(CompactControlReadability, true)
      .setPreference(FormatXml, false)
      .setPreference(PreserveSpaceBeforeArguments, true)

  def setPreferences(preferences: IFormattingPreferences): Seq[Setting[_]] =
    Seq(ScalariformKeys.preferences := preferences)
}
