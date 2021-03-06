package com.iscs.releaseScraper.routes

import cats.effect._
import cats.implicits._
import com.iscs.releaseScraper.domains.ReleaseDatesScraperService
import com.iscs.releaseScraper.model.ScrapeResult.Scrape._
import com.typesafe.scalalogging.Logger
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.{CORS, CORSConfig}

import scala.concurrent.duration._
import scala.util.Try

object Routes {
  private val L = Logger[this.type]
  private val protos = List("http", "https")
  private val reactDeploys = sys.env.getOrElse("ORIGINS", "localhost")
    .split(",")
    .toList
    .map(host =>
      protos.map(proto => s"$proto://$host")
    ).flatten
  private val reactOrigin = "http://localhost:3000"
  private val methods = Set("GET")
  private def checkOrigin(origin: String): Boolean =
    allowedOrigins.contains(origin)
  private val allowedOrigins = Set(reactOrigin) ++ reactDeploys
  private val methodConfig = CORSConfig(
    anyOrigin = false,
    allowCredentials = true,
    maxAge = 1.day.toSeconds,
    allowedMethods = Some(methods),
    allowedOrigins = checkOrigin
  )

  def scrapeRoutes[F[_]: Sync: Concurrent](R: ReleaseDatesScraperService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    val service = HttpRoutes.of[F] {
      case _ @ GET -> Root / "reldate" / year / month / rating =>
        for {
          _ <- Concurrent[F].delay(L.info(s""""request" date=$year/$month rating=$rating"""))
          ratingVal <- Concurrent[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          scrape <- Concurrent[F].delay(R.findReleases("rel", year, month, ratingVal))
          respList <- scrape.compile.toList
          resp <- Ok(respList)
        } yield resp
      case _ @ GET -> Root / "top" / year / rating =>
        for {
          _ <- Concurrent[F].delay(L.info(s""""request" date=$year rating=$rating"""))
          ratingVal <- Concurrent[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          scrape <- Concurrent[F].delay(R.findMovies("top", year, ratingVal))
          respList <- scrape.compile.toList
          resp <- Ok(respList)
        } yield resp
      case _ @ GET -> Root / "new" / year / rating =>
        for {
          _ <- Concurrent[F].delay(L.info(s""""request" date=$year rating=$rating"""))
          ratingVal <- Concurrent[F].delay(Try(rating.toDouble).toOption.getOrElse(5.0D))
          scrape <- Concurrent[F].delay(R.findMovies("new", year, ratingVal))
          respList <- scrape.compile.toList
          resp <- Ok(respList)
        } yield resp
    }
    CORS(service, methodConfig)
  }
}
