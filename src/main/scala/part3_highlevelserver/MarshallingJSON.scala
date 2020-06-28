package part3_highlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout

// step 1
import spray.json._
import scala.concurrent.duration._

case class Player(nickName: String, character: String, level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickName: String)
  case class GetPlayerByClass(character: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
  case object OperationSuccess
}

class GameAreaMap extends Actor with ActorLogging {
  import GameAreaMap._

  var players = Map[String, Player]()

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info("Getting all players")
      sender ! players.values.toList

    case GetPlayer(nickName) =>
      log.info(s"Getting player with ${nickName}")
      sender() ! players.get(nickName)

    case GetPlayerByClass(character) =>
      log.info(s"Getting all players with the character class: ${character}")
      sender() ! players.values.toList.filter(_.character == character)

    case AddPlayer(player) =>
      log.info("Trying to add player")
      players = players + (player.nickName -> player)
      sender() ! OperationSuccess

    case RemovePlayer(player) =>
      log.info(s"Trying to remove player: ${player}")
      players = players - player.nickName
      sender() ! OperationSuccess
  }
}

// step 2
trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerFormat = jsonFormat3(Player)
}

object MarshallingJSON extends App
  // step 3
  with PlayerJsonProtocol
  // step 4
  with SprayJsonSupport
{

  implicit val system = ActorSystem("MarshallingJSON")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import GameAreaMap._

  val rtjGameMap = system.actorOf(Props[GameAreaMap], "rockTheJVMAreaMap")
  val playersList = List(
    Player("Martin killz u", "Warrior", 70),
    Player("rolandbraveheart007", "Elf", 67),
    Player("daniel_rock03", "Wizard", 30)
  )
  playersList.foreach { player =>
    rtjGameMap ! AddPlayer(player)
  }

  /*
    1. GET /api/player returns all players in the map, as JSON
    2. GET /api/player/{nickname}, returns the player with the given nickname(as JSON)
    3. GET /api/player?nickname=X, does the same
    4. GET /api/player/class/{charClass}, return all players with given character class
    5. POST /api/player with JSON payload, adds the player to the map
    6. DELETE /api/player with JSON payload, removes the player from the map
   */
  implicit val timeout = Timeout(2 seconds)

  val rtjGameRouteSkeleton =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment) { characterClass =>
          val playersByFuture = (rtjGameMap ? GetPlayerByClass(characterClass)).mapTo[List[Player]]
          complete(playersByFuture)
        } ~
        (path(Segment) | parameter('nickname.as[String])) { nickName =>
          val playerOptFuture = (rtjGameMap ? GetPlayer(nickName)).mapTo[Option[Player]]
          complete(playerOptFuture)
        } ~
        pathEndOrSingleSlash {
          complete((rtjGameMap ? GetAllPlayers).mapTo[List[Player]])
        }
      } ~
      post {
        entity(as[Player]) { player =>
          complete((rtjGameMap ? AddPlayer(player)).map(_ => StatusCodes.OK))
        }
      } ~
      delete {
        entity(as[Player]) { player =>
          complete((rtjGameMap ? RemovePlayer(player)).map(_ => StatusCodes.OK))
        }
      }

    }

  Http().bindAndHandle(rtjGameRouteSkeleton, "localhost", 8081)
}
