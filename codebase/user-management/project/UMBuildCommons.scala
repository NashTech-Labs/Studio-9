import sbt.Keys._
import sbt._

object UMBuildCommons {
  val umOrganization = "com.sentrana"

  val playVersion = "2.4.6"

  val ScalaVersion = "2.11.7"

  val settings: Seq[Setting[_]] = Seq(
    scalaVersion := ScalaVersion,
    organization := umOrganization,
    resolvers ++= Seq(Resolver.jcenterRepo)
  )

  val publishingSettings: Seq[Setting[_]] =  Seq(publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.um-dc/libs"))))
}