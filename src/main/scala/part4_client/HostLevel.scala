package part4_client

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import part4_client.PaymentSystemDomain.PaymentRequest

import scala.util.{Failure, Success}
import spray.json._

object HostLevel extends App with PaymentJsonProtocol{

  implicit val system = ActorSystem("HostLevel")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val poolFlow = Http().cachedHostConnectionPool[Int]("www.google.com")
  /*
    We don't manage individual connection, they are used from cached pool

    We can attach additional data with each request(majorly original data),
    and then we will get response and associated data passed

    There is no order in which the response arrives, so this additional data helps us
   */

  Source(1 to 10)
    .map(x => (HttpRequest(), x))
    .via(poolFlow)
    .map {
      case (Success(response), value) =>
        // VERY IMPORTANT
        response.discardEntityBytes() // will lead to blockage, memory leak
        s"Request ${value} has received response: ${response}"
      case (Failure(ex), value) =>
        s"Request ${value} has failed: ${ex}"
    }
    //.runWith(Sink.foreach[String](println))

  val creditCards = List(
    CreditCard("4242-4242-4242-4242", "424", "rx-12-qa-test"),
    CreditCard("1234-1234-1234-1234", "123", "tx-suspicious-acc"),
    CreditCard("1234-5467-3456-1234", "533", "correct-ty-56")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "rtj-store-account", 99))

  val serverHttpRequests: List[(HttpRequest, String)] = paymentRequests.map(request =>
    (HttpRequest(
      HttpMethods.POST,
      uri = Uri("/api/payments"),
      entity=HttpEntity(
        ContentTypes.`application/json`,
        request.toJson.prettyPrint
      )
    ), UUID.randomUUID().toString
    )
  )

  Source(serverHttpRequests)
    .via(Http().cachedHostConnectionPool[String]("localhost", 8080))
    .runForeach { // (Try[HttpResponse], String)
      case (Success(response@HttpResponse(StatusCodes.Forbidden, _, _, _)), orderId) =>
        println(s"The OrderId: ${orderId} was forbidden")
      case (Success(response), orderId) =>
        println(s"The OrderId: ${orderId} was successful and returned the response: ${response}")
        // do something with the orderId
      case (Failure(ex), orderId) =>
        println(s"The OrderId: ${orderId} could not be completed: ${ex}")

    }

  // use Host-level API for high-volume and low latency request
}
