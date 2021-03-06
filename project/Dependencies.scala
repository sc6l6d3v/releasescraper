import sbt._

object Dependencies {
  object Versions {
    val Http4sVersion = "0.21.23"
    val CirceVersion = "0.13.0"
    val Specs2Version = "4.9.3"
    val LogbackVersion = "1.2.3"
    val catsRetryVersion = "1.1.0"
    val fs2Version = "2.2.2"
    val loggingVersion = "3.9.2"
    val jsoupVersion = "1.13.1"
    val scalatestVersion = "3.2.2"
    val ammoniteVersion = "2.3.8-4-88785969"
  }

  object http4s {
    val server = "org.http4s"  %% "http4s-blaze-server" % Versions.Http4sVersion
    val client = "org.http4s"  %% "http4s-blaze-client" % Versions.Http4sVersion
    val circe  = "org.http4s"  %% "http4s-circe"        % Versions.Http4sVersion
    val dsl =    "org.http4s"  %% "http4s-dsl"          % Versions.Http4sVersion
    val asyncClient = "org.http4s" %% "http4s-async-http-client" % Versions.Http4sVersion
  }

  object fs2 {
    val core = "co.fs2"        %% "fs2-core"            % Versions.fs2Version
    val io =    "co.fs2"       %% "fs2-io"              % Versions.fs2Version
    val streams = "co.fs2"     %% "fs2-reactive-streams" % Versions.fs2Version
  }

  object circe {
    val generic = "io.circe"   %% "circe-generic"       % Versions.CirceVersion
    val parser = "io.circe"    %% "circe-parser"        % Versions.CirceVersion
    val optics = "io.circe"    %% "circe-optics"        % Versions.CirceVersion
  }

  lazy val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalatestVersion % Test

  object ammonite {
    val main = "com.lihaoyi" % "ammonite_2.13.4" % Versions.ammoniteVersion
  }

  object logback {
    val classic = "ch.qos.logback"   %  "logback-classic"     % Versions.LogbackVersion
    val logging = "com.typesafe.scala-logging" %% "scala-logging" % Versions.loggingVersion
  }

  object cats {
    val retry = "com.github.cb372" %% "cats-retry"          % Versions.catsRetryVersion
  }

  object jsoup {
    val base = "org.jsoup"        %  "jsoup"               % Versions.jsoupVersion
  }
}
