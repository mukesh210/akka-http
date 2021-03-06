package part3_highlevelserver

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import com.typesafe.config.Optional
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}
import spray.json._

import scala.util.{Failure, Success}

object SecurityDomain extends DefaultJsonProtocol {
  case class LoginRequest(username: String, password: String)
  implicit val loginRequestFormat = jsonFormat2(LoginRequest)


}



object JWTAuthorization extends App with SprayJsonSupport {

  implicit val system = ActorSystem("JWTAuthorization")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import SecurityDomain._

  val superSecretPassowordDb = Map(
    "admin" -> "admin",
    "mukesh" -> "curiousGuy@1"
  )

  val algorithm = JwtAlgorithm.HS256
  val secretKey = "rockthejvmsecret"

  def checkPassword(username: String, password: String): Boolean =
    superSecretPassowordDb.contains(username) && superSecretPassowordDb(username) == password

  def createToken(username: String, expirationPeriodsInDays: Int): String = {
    val claims = JwtClaim(
      expiration = Some(System.currentTimeMillis()/1000 + TimeUnit.DAYS.toSeconds(expirationPeriodsInDays)),
      issuedAt = Some(System.currentTimeMillis()/100),
      issuer = Some("rockthejvm.com"),
    )

    JwtSprayJson.encode(claims, secretKey, algorithm) // JWT String
  }

  def isTokenExpired(token: String): Boolean= JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
    case Success(claims) => claims.expiration.getOrElse(0L) < System.currentTimeMillis()/1000
    case Failure(ex) => true
  }

  def isTokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(algorithm))

  val loginRoute =
    post {
      entity(as[LoginRequest]) {
        case LoginRequest(username, password) if checkPassword(username, password) =>
          val token = createToken(username, 1)
          respondWithHeader(RawHeader("Access-Token", token)) {
            complete(StatusCodes.OK)
          }
        case _ => complete(StatusCodes.Unauthorized)
      }
    }

  val authenticatedRoute =
    (path("secureEndpoint") & get) {
      optionalHeaderValueByName("Authorization") {
        case Some(token) =>
          if(isTokenValid(token)) {
            if(isTokenExpired(token))
              complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token expired."))
            else
              complete("User accessed authorized endpoint")
          }
          else
            complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token is invalid, or has been tempered with"))

        case _ => complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "No Token provided"))
      }
    }

  val route = loginRoute ~ authenticatedRoute

  Http().bindAndHandle(route, "localhost", 8081)

}
