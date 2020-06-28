package part3_highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.stream.ActorMaterializer
import part2_lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

object HighLevelExample extends App with GuitarStoreJsonProtocol {

  implicit val system = ActorSystem("HighLevelExample")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  /*
    GET /api/guitar fetches ALL guitars in the store
    GET /api/guitar?id=x fetches the guitar with id x
    GET /api/guitar/x fetches guitar with id x
    GET /api/guitar/inventory?inStock=true
   */
  import GuitarDB._
  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )

  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }

  def toHttpEntity(payload: String) = HttpEntity(
    ContentTypes.`application/json`, payload
  )

  implicit val timeout = Timeout(2 seconds)
  val guitarServerRoute =
    (pathPrefix("api" / "guitar") & get) {
      // ALWAYS PUT MORE SPECIFIC CASE FIRST
      (path(IntNumber) | parameter('id.as[Int])) { guitarId =>
          val entityFuture: Future[HttpEntity.Strict] = (guitarDb ? FindGuitar(guitarId))
            .mapTo[Option[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity)

          complete(entityFuture)
      } ~
      pathEndOrSingleSlash {
        val entityFuture = (guitarDb ? FindAllGuitars)
          .mapTo[List[Guitar]]
          .map(_.toJson.prettyPrint)
          .map(toHttpEntity)

        complete(entityFuture)
      } ~
      path("inventory") {
          parameter('inStock.as[Boolean]) { inStock =>
            val entityFuture = (guitarDb ? FindGuitarsInStock(inStock))
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)

            complete(entityFuture)
          }
      }
    }

  Http().bindAndHandle(guitarServerRoute, "localhost", 8081)

}
