package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import part4_client.PaymentSystemDomain.PaymentRequest

import scala.concurrent.Future
import scala.util.{Failure, Success}
import spray.json._

object ConnectionLevel extends App with PaymentJsonProtocol {

  implicit val system = ActorSystem("ConnectionLevel")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val connectionFlow = Http().outgoingConnection("www.google.com")

  def oneOffRequest(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(connectionFlow).runWith(Sink.head)

  oneOffRequest(HttpRequest()) onComplete {
    case Success(response) =>
      println(s"Got successful response: ${response}")

    case Failure(exception) =>
      println(s"Sending the request failed with error: ${exception}")
  }

  /*
  Use connection level API, when you want to send lots of request(from a source)
   */
  /*
    A small payment system: `PaymentSystem.scala`
  */
  // received from front-end
  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "rx-12-qa-test"),
    CreditCard("1234-1234-1234-1234", "123", "tx-suspicious-acc"),
    CreditCard("1234-5467-3456-1234", "533", "correct-ty-56")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "rtj-store-account", 99))

  val serverHttpRequests = paymentRequests.map(request =>
    HttpRequest(
      HttpMethods.POST,
      uri = Uri("/api/payments"),
      entity=HttpEntity(
        ContentTypes.`application/json`,
        request.toJson.prettyPrint
      )
    )
  )

  Source(serverHttpRequests)
    .via(Http().outgoingConnection("localhost", 8080))
    .to(Sink.foreach[HttpResponse](println))
    .run()
}

