package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, MissingQueryParamRejection, Rejection, RejectionHandler}

object HandlingRejections extends App {

  implicit val system = ActorSystem("HandlingRejections")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleRoute =
    path("api" / "myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
      parameter('id) { _ =>
        complete(StatusCodes.OK)
      }
    }

  /*
  Suppose, we have route defined
  When a request arrives, it goes through each one of the route in order,
  if route is not able to fulfill request, it will add RejectionObject to RejectionList

  Objects are added in this rejectionList every time a route is unable to fulfill a request

  By default, if none of the route can fulfill a request, 404 will be thrown
  but we can change the way this rejections are handled

  RejectionList is cleared as soon as some route is able to fulfill request
   */
  // Rejection Handlers
  val badRequestHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: ${rejections}")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: ${rejections}")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers =
    handleRejections(badRequestHandler) { // handle Rejections from the top level
      // define server logic inside
      path("api" / "myEndpoint") {
        get {
          complete(StatusCodes.OK)
        } ~
        post {
          handleRejections(forbiddenHandler) {  // handle rejection WITHIN
            parameter('myParam) { _ =>
              complete(StatusCodes.OK)
            }
          }
        }
      }
    }
  // default rejection
  // RejectionHandler.default

  // Http().bindAndHandle(simpleRouteWithHandlers, "localhost", 8081)

  // if you don't want to wrap route with RejectionHandler, make it implicit

  // List(Method Rejection, Query Param Rejection): if more than 1 rejection, all rejections will be tested
  // with first `handle`... followed by next `handle`... until one of it succeeds
  implicit val customRejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case m: MissingQueryParamRejection =>
        println(s"I got a query param rejection: ${m}")
        complete("Rejected Query Param")
    }
    .handle {
      case m: MethodRejection =>
        println(s"I got a method rejection: ${m}")
        complete("Rejected Method")
    }
    .result()

  // having implicit rejection handler: sealing a route

  // will use `customRejectionHandler` since it is implicit
  Http().bindAndHandle(simpleRoute, "localhost", 8081)
}
