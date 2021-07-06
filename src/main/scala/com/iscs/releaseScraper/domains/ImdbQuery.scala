package com.iscs.releaseScraper.domains

import cats.effect.{Concurrent, ConcurrentEffect, Sync}
import cats.implicits._
import com.iscs.releaseScraper.model.Requests.ReqParams
import com.iscs.releaseScraper.util.DbClient
import com.typesafe.scalalogging.Logger
import fs2.{Pipe, Stream}
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.generic.semiauto._
import io.circe.parser._
import org.bson.conversions.Bson
import org.http4s.circe._
import org.http4s.{EntityDecoder, EntityEncoder}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{and, exists, gte, regex, text, elemMatch => elemMatchFilter, eq => mdbeq, ne => mdne}
import org.mongodb.scala.model._

trait ImdbQuery[F[_]] {
  def getByTitle(title: String, rating: Double, params: ReqParams): Stream[F,Json]
  def getByName(name: String, rating: Double, params: ReqParams): Stream[F,Json]
}

object ImdbQuery {
  private val L = Logger[this.type]

  def apply[F[_]](implicit ev: ImdbQuery[F]): ImdbQuery[F] = ev

  final case class TitleRec(averageRating: Option[Double], numVotes: Option[Int],
                            titleType: String, primaryTitle: String, originalTitle: String,
                            isAdult: Int, startYear: Int, endYear: String, runTimeMinutes: Int,
                            genresList: List[String])

  final case class NameTitleRec(primaryName: String, birthYear: Int,
                                matchedTitles: List[TitleRec])

  object TitleRec {
    implicit val titleRecDecoder: Decoder[TitleRec] = deriveDecoder[TitleRec]
    implicit def titleRecEntityDecoder[F[_]: Sync]: EntityDecoder[F, TitleRec] = jsonOf
    implicit val titleRecEncoder: Encoder[TitleRec] = deriveEncoder[TitleRec]
    implicit def titleRecEntityEncoder[F[_]: Sync]: EntityEncoder[F, TitleRec] = jsonEncoderOf
  }

  object NameTitleRec {
    implicit val nameTitleRecDecoder: Decoder[NameTitleRec] = deriveDecoder[NameTitleRec]
    implicit def nameTitleRecEntityDecoder[F[_]: Sync]: EntityDecoder[F, NameTitleRec] = jsonOf
    implicit val nameTitleRecEncoder: Encoder[NameTitleRec] = deriveEncoder[NameTitleRec]
    implicit def nameTitleRecEntityEncoder[F[_]: Sync]: EntityEncoder[F, NameTitleRec] = jsonEncoderOf
  }

  def impl[F[_]: Concurrent: Sync: ConcurrentEffect](dbClient: DbClient[F]): ImdbQuery[F] =
    new ImdbQuery[F] {
      val averageRating = "averageRating"
      val primaryTitle = "primaryTitle"
      val primaryName = "primaryName"
      val primaryProfession = "primaryProfession"
      val primaryProfessionList = "primaryProfessionList"
      val knownForTitles = "knownForTitles"
      val knownForTitlesList = "knownForTitlesList"
      val titleCollection = "title_basics_ratings"
      val nameCollection = "name_basics"
      val birthYear = "birthYear"
      val deathYear = "deathYear"
      val matchedTitles = "matchedTitles"
      val startYear = "startYear"
      val genres = "genres"
      val genresList = "genresList"
      val matchedTitles_averageRating = "matchedTitles.averageRating"
      val matchedTitles_genres = "matchedTitles.genres"
      val titleType = "titleType"
      val isAdult = "isAdult"
      private val titleFx = dbClient.fxMap(titleCollection)
      private val nameFx = dbClient.fxMap(nameCollection)

      private def docToJson[F[_]: ConcurrentEffect]: Pipe[F, Document, Json] = strDoc => {
        for {
          doc <- strDoc
          json <- Stream.eval(Concurrent[F].delay(parse(doc.toJson()) match {
            case Right(validJson) => validJson
            case Left(parseFailure) =>
              L.error(""""Parsing failure" exception={} """, parseFailure.toString)
              Json.Null
          }))
        } yield json
      }

/*      private def filterMatches[F[_]: ConcurrentEffect](rating: Double): Pipe[F, Json, Json] = jsonStr => {
        for {
          json <- jsonStr
          matched <- Stream.eval(Concurrent[F].delay((json \\ matchedTitles).head))
          _ <- Stream.eval(Concurrent[F].delay(L.info(s"got $matched")))
          filteredList <- Stream.eval(Concurrent[F].delay{
            val matchedList = matched.asArray.getOrElse(Vector.empty[Json])
            matchedList.filter { js =>
                root.averageRating.double.getOption(js).map { xx =>
                  xx >= rating
                }.getOrElse(false)
            }
          })
          _ <- Stream.eval(Concurrent[F].delay(L.info(s"got $filteredList")))
          newJson <- Stream.emits(filteredList)
        } yield newJson
      }*/

      def condenseSingleLists(bsonList: List[Bson]): F[Bson] =
        for {
          bson <- Concurrent[F].delay{
            if (bsonList.size == 1) bsonList.head
            else and(bsonList: _*)
          }
        } yield bson

      def getTitleModelFilters(title: String): F[Bson] = for {
        parts <- Concurrent[F].delay(title.split(" ").toList)
        regexList <- Concurrent[F].delay(parts.map(part =>
          regex(primaryTitle, s"/$part/i")
        ))
        regex <- condenseSingleLists(regexList)
        textBson <- Concurrent[F].delay(text(title))
        bson <- Concurrent[F].delay(and(textBson, regex))
      } yield textBson

      def getParamList(params: ReqParams): List[Bson] =
        List(
          params.year.map(yr => mdbeq(startYear, yr)),
          params.genre.map(genre => elemMatchFilter(genresList, mdbeq(genresList, genre))),
          params.titleType.map(tt => mdbeq(titleType, tt)),
          params.isAdult.map(isadult => mdbeq(isAdult, isadult))
        ).flatten

      def getBsonList(params: ReqParams): F[List[Bson]] = Concurrent[F].delay(getParamList(params))

      def getParamModelFilters(params: ReqParams): F[Bson] = condenseSingleLists(getParamList(params))

      override def getByTitle(title: String, rating: Double, params: ReqParams): Stream[F, Json] = for {
        paramBson <- Stream.eval(getParamModelFilters(params))
        ratingBson <- Stream.eval(Concurrent[F].delay(gte(averageRating, rating)))
        titleBson <- Stream.eval(getTitleModelFilters(title))
        dbList <- Stream.eval(titleFx.find(
          and(titleBson, ratingBson, paramBson),
          /*Map(genres -> false)*/
        20, 0, Map(genres -> false))
          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json

      /**
       * {
       *    $match: { $and: [
       *                { 'knownForTitles': { $exists: true, $ne: "" }},
       *                { 'primaryName': 'Tom Cruise' }
       *              ]
       *            }
       * }, {
       *    $lookup: {
       *       'from': 'title_basics_ratings',
       *       'localField': 'knownForTitlesList',
       *       'foreignField': '_id',
       *       'as': 'matchedTitles'
       * }, {
       *       $match: {
       *         "matchedTitles": {
       *            $elemMatch: {
       *              "startYear": 1986
       *             }
       *         }
       *       }
       * }, {
       *    $project: { primaryProfession: 0, deathYear: 0, knownForTitles: 0, primaryProfessionList: 0, knownForTitlesList: 0,
       *        "matchedTitles.genres": 0  }
       * }, {
       *    $project: {
       *       "primaryName": 1, birthYear: 1,
       *       "matchedTitles": {
       *          $filter: {
       *             "input": "$matchedTitles",
       *             "as": "matchedTitles",
       *             "cond": {
       *                "$and": [
       *                    { $gte: [ "$$matchedTitles.averageRating", 7.0 ] },
       *                    { $eq: [ "$$matchedTitles.startYear": 1986 ] },
       *                    { $eq: [ "$$matchedTitles.genresList": "Drama" ] },
       *                    { $eq: [ "$$matchedTitles.titleType": "movie" ] },
       *                    { $eq: [ "$$matchedTitles.isAdult": 1 ] },
       *                ]
       *             }
       *          }
       *       }
       *    }
       * }
       *
       * @param name of actor
       * @param rating IMDB
       * @param params body
       * @return
       */
      override def getByName(name: String, rating: Double, params: ReqParams): Stream[F, Json] = for {
        paramsList <- Stream.eval(getBsonList(params))
        ratingBson <- Stream.eval(Concurrent[F].delay(gte("averageRating", rating)))
        bsonCondensedList <- Stream.eval(condenseSingleLists(paramsList :+ ratingBson))
        matchTitleWithName <- Stream.eval(Concurrent[F].delay(
          and(
            exists(knownForTitles, exists = true),
            mdne(knownForTitles, ""),
            mdbeq(primaryName, name)
          )
        ))
        titleMatchFilter <- Stream.eval(Concurrent[F].delay(
          Aggregates.filter(matchTitleWithName)
        ))
        matchLookupsFilter <- Stream.eval(Concurrent[F].delay(
          Aggregates.filter(elemMatchFilter(matchedTitles, bsonCondensedList))
        ))
        lookupFilter <- Stream.eval(Concurrent[F].delay(
          Aggregates.lookup(titleCollection,
            knownForTitlesList,
            "_id",
            matchedTitles)
        ))
        initialProjectionsList <- Stream.eval(Concurrent[F].delay(
          nameFx.getProjectionFields(Map(
            primaryProfession -> false,
            deathYear -> false,
            knownForTitles -> false,
            primaryProfessionList -> false,
            knownForTitlesList -> false,
            matchedTitles_genres -> false,
          ))
        ))
        initialProjectFilter <- Stream.eval(Concurrent[F].delay(
          Aggregates.project(nameFx.getProjections(initialProjectionsList))
        ))
        finalProjectionsList <- Stream.eval(Concurrent[F].delay(
          nameFx.getProjectionFields(Map(
            primaryName -> true,
            birthYear -> true
          ))
        ))
        condCondensedList <- Stream.eval(condenseSingleLists(
          paramsList :+
          nameFx.getGTEFilter(s"$$$matchedTitles_averageRating", rating))
        )
        condFilter <- Stream.eval(Concurrent[F].delay(
          nameFx.getCondFilter("matchedTitles", "matchedTitles", condCondensedList)
        ))
        combinedProjections <- Stream.eval(Concurrent[F].delay(
          finalProjectionsList :+ condFilter
        ))
        finalProjectFilter <- Stream.eval(Concurrent[F].delay(
          Aggregates.project(nameFx.getProjections(combinedProjections))
        ))
        dbList <- Stream.eval(nameFx.aggregate(
          Seq(
            titleMatchFilter,
            lookupFilter,
            matchLookupsFilter,
            initialProjectFilter,
            finalProjectFilter
          )
        )
          .through(docToJson)
          .compile.toList)
        json <- Stream.emits(dbList)
      } yield json
    }

}