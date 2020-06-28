package part3_highlevelserver
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import spray.json._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._
/**
  * Digital server for books
  */

case class Book(id: Int, author: String, title: String)

trait BookJsonProtocol extends DefaultJsonProtocol {
  implicit val bookFormat = jsonFormat3(Book)
}

class RouteDSLSpec extends WordSpec with Matchers with ScalatestRouteTest with BookJsonProtocol {
  import RouteDSLSpec._

  "A digital library backend" should {
    "return all the books in the library" in {
      // send a HTTP request through an endpoint that you want to test
      // inspect the response
      Get("/api/book") ~> libraryRoute ~> check {
        // assertions
        status shouldBe StatusCodes.OK

        entityAs[List[Book]] shouldBe books
      }
    }

    "return a book by hitting the query parameter endpoint" in {
      Get("/api/book?id=2") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK
        // same as entityAs
        responseAs[Option[Book]] shouldBe Some(Book(2, "JRR Tolkien", "The Lord of the rings"))
      }
    }

    "return a book by calling the endpoint with the id in path" in {
      Get("/api/book/2") ~> libraryRoute ~> check {
        response.status shouldBe StatusCodes.OK

        // similar to above 2 tests, but we can do manually too
        val strictEntityFuture = response.entity.toStrict(1 second)
        val strictEntity = Await.result(strictEntityFuture, 1 second)

        strictEntity.contentType shouldBe ContentTypes.`application/json`

        val book = strictEntity.data.utf8String.parseJson.convertTo[Option[Book]]
        book shouldBe Some(Book(2, "JRR Tolkien", "The Lord of the rings"))
      }
    }

    "insert a book in to database" in {
      val newBook = Book(5, "Steven PressField", "The War of Art")
      Post("/api/book", newBook) ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK

        assert(books.contains(newBook)) // same as
        books should contain(newBook)
      }
    }

    "not accept other methods than POST and GET" in {
      Delete("/api/book") ~> libraryRoute ~> check {
        rejections should not be empty  // "natural language" style
        rejections.should(not).be(empty)  // same

        val methodRejections = rejections.collect {
          case rejection: MethodRejection => rejection
        }

        methodRejections.length shouldBe 2
      }
    }

    "return all books of author" in {
      Get("/api/book/author/JRR%20Tolkien") ~> libraryRoute ~> check {
        status shouldBe StatusCodes.OK

        entityAs[List[Book]] shouldBe books.filter(_.author == "JRR Tolkien")
      }
    }
  }

}

object RouteDSLSpec
  extends BookJsonProtocol
  with SprayJsonSupport
{

  var books = List(
    Book(1, "Harper Lee", "To kill a Mockingbird"),
    Book(2, "JRR Tolkien", "The Lord of the rings"),
    Book(3, "GRR Martin", "A song of ice and fire"),
    Book(4, "Tony Robbins", "Awaken the Giant within")
  )

  /*
    GET /api/book - return all books in the library
    GET /api/book/{id} - return book with id
    GET /api/book?id=X  same
    POST /api/book  - adds new book to library

    GET /api/book/author/X  - return all the books from the same author
   */
  val libraryRoute =
    pathPrefix("api" / "book") {
      (path("author" / Segment) & get) { author =>
        complete(books.filter(_.author == author))
      } ~
      get {
        (path(IntNumber) | parameter('id.as[Int])) { id =>
          complete(books.find(_.id == id))
        } ~
          pathEndOrSingleSlash {
            complete(books)
          }
      } ~
      post {
        entity(as[Book]) { book =>
          books = books :+ book
          complete(StatusCodes.OK)
        }
      }
//      ~
//      complete(StatusCodes.BadRequest)
    }

}
