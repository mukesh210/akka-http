package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler

object HandlingExceptions extends App {

  implicit val system = ActorSystem("HandlingExceptions")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleRoute =
    path("api" / "people") {
      get {
        // directive that might throw some exception
        throw new RuntimeException("Getting all (people took too long!")
      } ~
      post {
        parameter('id) { id =>
          if(id.length > 2)
            throw new NoSuchElementException(s"Parameter ${id} cannot be found in database, TABLE FLIP!")
          else
            complete(StatusCodes.OK)
        }
      }
    }
  /*
    By default, if our route failed with an error, default exception handler
    responds with 500(Internal Server Error) to client

    But, just the way we can handle Rejections, we can also handle Exceptions

    If the customExceptionHandler is not handling the error, default exception handler
    will handle it(i.e. 500 Internal Server Error)
   */

  // if you make it implicit, it will work without any other setting
  implicit val customExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException =>
      complete(StatusCodes.NotFound, e.getMessage)
    case e: IllegalArgumentException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }

  // Http().bindAndHandle(simpleRoute, "localhost", 8081)

  // explicit exception handling (when we want to set different exception handler for some part and different for other)
  val runtimeExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException =>
      complete(StatusCodes.NotFound, e.getMessage)
  }

  val noSuchElementExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: NoSuchElementException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }

  val delicateHandleRoute =
    handleExceptions(runtimeExceptionHandler) {
      path("api" / "people") {
        get {
          // directive that might throw some exception
          throw new RuntimeException("Getting all (people took too long!")
        } ~
          handleExceptions(noSuchElementExceptionHandler) {
            post {
              parameter('id) { id =>
                if(id.length > 2)
                  throw new NoSuchElementException(s"Parameter ${id} cannot be found in database, TABLE FLIP!")
                else
                  complete(StatusCodes.OK)
              }
            }
          }
      }
    }

  Http().bindAndHandle(delicateHandleRoute, "localhost", 8081)
}
