package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import part4_client.PaymentSystemDomain.PaymentRequest

import scala.util.{Failure, Success}
import spray.json._

object RequestLevel extends App with PaymentJsonProtocol {

  implicit val system = ActorSystem("RequestLevel")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val responseFuture = Http().singleRequest(HttpRequest(uri = "http://www.google.com"))
  responseFuture onComplete {
    case Success(response) =>
      // VERY IMPORTANT
      response.discardEntityBytes()
      println(s"The request was successful and returned: ${response}")
    case Failure(exception) =>
      println(s"The request failed with ${exception}")
  }

  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "rx-12-qa-test"),
    CreditCard("1234-1234-1234-1234", "123", "tx-suspicious-acc"),
    CreditCard("1234-5467-3456-1234", "533", "correct-ty-56")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "rtj-store-account", 99))

  val serverHttpRequests = paymentRequests.map(request =>
    HttpRequest(
      HttpMethods.POST,
      uri = "http://localhost:8080/api/payments",
      entity=HttpEntity(
        ContentTypes.`application/json`,
        request.toJson.prettyPrint
      )
    )
  )

  Source(serverHttpRequests)
    .mapAsync(10)(request => Http().singleRequest(request))
    .runForeach(println)

  /**
    * Best for: low-volume, low-latency requests
    *
    * do not use for:
    * high volume(use the host-level API)
    * long-lived requests(use the connection-level API)
    */
}
