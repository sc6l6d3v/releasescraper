import Dependencies._

val Http4sVersion = "0.21.3"
val CirceVersion = "0.13.0"
val Specs2Version = "4.9.3"
val LogbackVersion = "1.2.3"
val catsRetryVersion = "1.1.0"
val fs2Version = "2.2.2"
val loggingVersion = "3.9.2"
val jsoupVersion = "1.13.1"

lazy val root = (project in file("."))
  .settings(
    organization := "com.iscs",
    name := "releasescraper",
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.13.3",
    libraryDependencies ++= Seq(
      http4s.server,
      http4s.client,
      http4s.circe,
      http4s.dsl,
      http4s.asyncClient,
      circe.generic,
      circe.parser,
      circe.optics,
      scalaTest,
      logback.classic,
      logback.logging,
      cats.retry,
      fs2.core,
      fs2.io,
      fs2.streams,
      jsoup.base,
      ammonite.main
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
    Revolver.enableDebugging(5060, true)
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature"
  //"-Xfatal-warnings",
)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
