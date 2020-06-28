package part3_highlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._


case class Person(pin: Int, name: String)

trait PersonJsonProtocol extends DefaultJsonProtocol {
  implicit val personFormat = jsonFormat2(Person)
}

object PersonActor {
  case object GetAllPersons
  case class GetPersonWithPIN(pin: Int)
  case class AddPerson(person: Person)
}

class PersonActor extends Actor with ActorLogging {
  import PersonActor._

  val personList: List[Person] = List()

  override def receive: Receive = mainReceive(personList)

  def mainReceive(personList: List[Person]): Receive = {
    case GetAllPersons =>
      log.info("Getting all registered persons")
      sender() ! personList

    case GetPersonWithPIN(pin) =>
      log.info(s"Getting Person with PIN: ${pin}")
      sender() ! personList.find(_.pin == pin)

    case AddPerson(person) =>
      log.info(s"Adding Person: ${person} to DB")
      context.become(mainReceive(personList :+ person))
      sender() ! true
  }
}

object HighLevelExercise extends App with PersonJsonProtocol {

  implicit val system = ActorSystem("HighLevelExercise")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  /**
    * Exercise:
    * - GET /api/people retrieves all the people registered
    * - GET /api/people/pin retrieve person with pin, return as JSON
    * - GET /api/people?pin=X (same)
    * - POST /api/people with a JSON payload denoting a Person, add that person to your db
    *   - extract the HTTP request's payload (entity)
    *     - extract the request
    *     - process the entity's data
    */
    import PersonActor._
  val personActor = system.actorOf(Props[PersonActor], "personActor")

  var people = List(
    Person(1, "Alice"),
    Person(2, "Bob"),
    Person(3, "Charlie")
  )
  people.foreach(person => personActor ! AddPerson(person))
  implicit val timeout = Timeout(2 seconds)

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val peopleRoute =
    pathPrefix("api" / "people") {
      ((path(IntNumber) | parameter('pin.as[Int])) & get) { pin =>
        val personFuture = (personActor ? GetPersonWithPIN(pin))
          .mapTo[Option[Person]]
          .map(_.toJson.prettyPrint)
          .map(toHttpEntity)

        complete(personFuture)
      } ~
        (pathEndOrSingleSlash & get) {
          val personFuture = (personActor ? GetAllPersons)
            .mapTo[List[Person]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity)

          complete(personFuture)
      } ~
        (post & pathEndOrSingleSlash & extractRequest & extractLog) { (request, log) =>
          val entity = request.entity
          val strictEntityFuture = entity.toStrict(2 seconds)
          val personFuture = strictEntityFuture.map { strictEntity =>
            strictEntity.data.utf8String.parseJson.convertTo[Person]
          }

          val personAddedFuture = personFuture.flatMap { person =>
            (personActor ? AddPerson(person))
              .mapTo[Boolean]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          }
          complete(personAddedFuture)
        }
      }

  Http().bindAndHandle(peopleRoute, "localhost", 8081)
}
