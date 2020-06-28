package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._

/**
  * Akka HTTP relies on Akka Streams
  * Akka Streams relies on Akka Actors
  */
object LowLevelAPI extends App {

  implicit val system = ActorSystem("LowLevelServerAPI")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val serverSource = Http().bind("localhost", 8008)
  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connection from : ${connection.remoteAddress}")
  }

  val serverBindingFuture = serverSource.to(connectionSink).run()

  serverBindingFuture.onComplete {
    case Success(binding) =>
      // binding.terminate(2 seconds)
      println("Server binding successful")
    case Failure(ex) => println(s"Server binding failed: ${ex}")
  }

  /*
    Method 1: synchronously serve HTTP responses
   */
  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            | Hello from Akka HTTP
            |</body>
            |</html>
          """.stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            | OOPS! The resource can't be found
            |</body>
            |</html>
          """.stripMargin
        )
      )
  }

  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

  // Http().bind("localhost", 8009).runWith(httpSyncConnectionHandler)
  // or shorthand version:
  // Http().bindAndHandleSync(requestHandler, "localhost", 8009)

  /*
  Method 2: serve back HTTP responses asynchronouly
   */

  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>  // method, URI, HTTP headers, content and protocol(HTTP1.1/HTTP 2.0)
      Future(HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            | Hello from Akka HTTP
            |</body>
            |</html>
          """.stripMargin
        )
      ))
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            | OOPS! The resource can't be found
            |</body>
            |</html>
          """.stripMargin
        )
      ))
  }

  val httpAsyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithAsyncHandler(asyncRequestHandler)
  }

  // stream-based "manual" version
  // Http().bind("localhost", 8081).runWith(httpAsyncConnectionHandler)

  // shorthand version
  Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8081)

  /*
  Method 3: Async via Akka Streams
   */
  val streamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] =  Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>  // method, URI, HTTP headers, content and protocol(HTTP1.1/HTTP 2.0)
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            | Hello from Akka HTTP
            |</body>
            |</html>
          """.stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            | OOPS! The resource can't be found
            |</body>
            |</html>
          """.stripMargin
        )
      )
  }

  // ""manual" version
//  Http().bind("localhost", 8082).runForeach { connection =>
//    connection.handleWith(streamsBasedRequestHandler)
//  }

  // shorthand version
  Http().bindAndHandle(streamsBasedRequestHandler, "localhost", 8082)

  /**
    * Exercise: create your own HTTP Server running on localhost: 8388,
    * which replies:
    *   - with a welcome message on the "front door" localhost:8388,
    *   - with a proper HTML on localhost:8388/about
    *   - with a 404 message otherwise
    */
  val exerciseSyncRequestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(
        // status code OK (200) is default
        entity=HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Welcome to our lovely home!
            | </body>
            |</html>
          """.stripMargin
        )
      )

    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity=HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Shopping Website
            |   Here are details that will be filled soon
            | </body>
            |</html>
          """.stripMargin
        )
      )

    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   No results found... are you looking at right place
            | </body>
            |</html>
          """.stripMargin
        )
      )
  }

  Http().bindAndHandleSync(exerciseSyncRequestHandler, "localhost", 8388)

  val exerciseAsyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
      Future(HttpResponse(
        StatusCodes.OK,
        entity=HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Welcome to our lovely home!
            | </body>
            |</html>
          """.stripMargin
        )
      ))

    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) =>
      Future(HttpResponse(
        StatusCodes.OK,
        entity=HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Shopping Website
            |   Here are details that will be filled soon
            | </body>
            |</html>
          """.stripMargin
        )
      ))

    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   No results found... are you looking at right place
            | </body>
            |</html>
          """.stripMargin
        )
      ))
  }

  Http().bindAndHandleAsync(exerciseAsyncRequestHandler, "localhost", 8389)

  val exerciseFlowRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity=HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Welcome to our lovely home!
            | </body>
            |</html>
          """.stripMargin
        )
      )

    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity=HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Shopping Website
            |   Here are details that will be filled soon
            | </body>
            |</html>
          """.stripMargin
        )
      )

      // path /search redirects to some other part of our website
    case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      HttpResponse(
        StatusCodes.Found,
        headers = List(Location("http://google.com"))

      )

    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   No results found... are you looking at right place
            | </body>
            |</html>
          """.stripMargin
        )
      )
  }

  val bindingFuture = Http().bindAndHandle(exerciseFlowRequestHandler, "localhost", 8390)

  // shut down the server
  bindingFuture.flatMap(binding => binding.unbind())
    .onComplete(_ => system.terminate())
}
