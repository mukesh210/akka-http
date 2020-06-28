package part3_highlevelserver

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

object DirectivesBreakdown extends App {
  implicit val system = ActorSystem("DirectivesBreakdown")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  /**
    * Type #1: Filtering directives
    */
  val simpleHttpMethodRoute =
    post {  // equivalent directives: get, put, patch, delete, head, options
      complete(StatusCodes.Forbidden)
    }

  val simplePathRoute: Route =
    path("about") {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            | Hello from the about page!
            |</body>
            |</html>
          """.stripMargin
        )
      )
    }

  val complexPathRoute =
    path("api" / "myEndpoint") {  // /api/myEndpoint
      complete(StatusCodes.OK)
    }

  val dontConfuse =
    path("api/myEndpoint") {  // / will be encoded, so we will have to hit "/api%2fmyEndpoint"
      complete(StatusCodes.OK)
    }

  val pathEndRoute =
    pathEndOrSingleSlash {  // localhost:9090 or localhost:9090/
      complete(StatusCodes.OK)
    }

  // Http().bindAndHandle(complexPathRoute, "localhost", 8081)

  /**
    * Type #2: extraction directives
    */
  // GET on /api/items/42
  val pathExtractionRoute =
    path("api" / "items" / IntNumber) { itemNumber =>
      // other directives
      println(s"I have got a number in my path: ${itemNumber}")
      complete(StatusCodes.OK)
    }

  val pathMultiExtractRoute =
    path("api" / "orders" / IntNumber / IntNumber) { (id, inventory) =>
      println(s"I have got 2 numbers in my path: ${id} :: ${inventory}")
      complete(StatusCodes.OK)
    }

  // Http().bindAndHandle(pathMultiExtractRoute, "localhost", 8081)

  val queryParamExtractionRoute: Route =
    // /api/item?id=45
  path("api" / "item") {
    parameter("id".as[Int]) { (itemId: Int) =>
      println(s"I have extracted the id as: ${itemId}")
      complete(StatusCodes.OK)
    }
  }

  val extractRequestRoute =
    path("controlledEndpoint") {
      extractRequest { httpRequest: HttpRequest =>  // extract from RequestContext
        extractLog { (log: LoggingAdapter) =>
          log.info(s"I got httpRequest: ${httpRequest}")
          complete(StatusCodes.OK)
        }
      }
    }

  Http().bindAndHandle(queryParamExtractionRoute, "localhost", 8082)

  /**
    * Type #3: composite directives
    */

  val simpleNestedRoute =
    path("api" / "item") {
      get {
        complete(StatusCodes.OK)
      }
    }

  // filter directive: whose path is "/api/item" and it is GET request
  val compactSimpleNestedGraph = (path("api" / "item") & get) {
    complete(StatusCodes.OK)
  }

  // filter and extraction directive: endpoint is "controlledEndpoint" and extract request and log
  val compactExtractRequestRoute =
    (path("controlledEndpoint") & extractRequest & extractLog) { (request, log) =>
      log.info(s"I got httpRequest: ${request}")
      complete(StatusCodes.OK)
    }

  // want to support /about and /aboutUs page with the same logic for both
  val repeatedRoute =
    path("about") {
      complete(StatusCodes.OK)
    } ~ path("aboutUs") {
      complete(StatusCodes.OK)
    }

  // how to put same server logic under same directive
  val dryRoute =
    (path("about") | path("aboutUs")) {
      complete(StatusCodes.OK)
    }

  // yourblog.com/42 AND yourblog.com?postId=42 : how to unify both of these into one... since code is same
  val blogById =
    path(IntNumber) { (blogId: Int) =>
      complete(StatusCodes.OK)
    }

  val blogByQueryParamRoute =
    parameter('postId.as[Int]) { (blogPostId: Int) =>
      // same server logic
      complete(StatusCodes.OK)
    }

  // when chaining 2 extraction types with OR operator,
  // make sure that both the extraction types return same type
  val combinedBlogByIdRoute =
    (path(IntNumber) | parameter('postId.as[Int])) { (blogPostId: Int) =>
      complete(StatusCodes.OK)
    }

  /**
    * Type #4: "actionable" directives
    */
  val completeOkRoute = complete(StatusCodes.OK)

  val failedRoute =
    path("notSupported") {
      failWith(new RuntimeException("Unsupported")) // complete with HTTP 500
    }

  // rejecting a request means it will be fulfilled by next directive in the routing tree
  // but we can reject a request manually too
  val routeWithRejection =
    path("home") {
      reject
    } ~
    path("index") {
      completeOkRoute
    }

  /**
    * Exercise: can you spot the mistake
    */
  val getOrPutPath =
    path("api" / "myEndpoint") {
      get {
        completeOkRoute
      } ~           // ~ is used to chain Directives
      post {
        complete(StatusCodes.Forbidden)
      }
    }

  Http().bindAndHandle(getOrPutPath, "localhost", 8089)
}

