package com.iscs.releaseScraper

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.typesafe.scalalogging.Logger

object Main extends IOApp {
  private val L = Logger[this.type]

  def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO.delay(System.currentTimeMillis)
    serverStream = for {
      str <- Server.stream[IO]
    } yield str

    s <- serverStream
      .compile.drain.as(ExitCode.Success)
      .handleErrorWith(ex => IO {
        L.error("\"exception during stream startup\" exception={} ex={}", ex.toString, ex)
        ExitCode.Error
      })
  } yield s
}