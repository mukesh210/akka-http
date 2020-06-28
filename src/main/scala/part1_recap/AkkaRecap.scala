package part1_recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, PoisonPill, Props, Stash, SupervisorStrategy}
import akka.util.Timeout

object AkkaRecap extends App {

  class SimpleActor extends Actor with Stash with ActorLogging {
    override def receive: Receive = {
      case "createChild" =>
        val childActor = context.actorOf(Props[SimpleActor], "myChild")
        childActor ! "Hello"
      case "stashThis" =>
        stash()
      case "change handler now" =>
        unstashAll()
        context.become(anotherHandler)
      case "change" => context.become(anotherHandler)
      case message => println(s"I received: ${message}")
    }

    def anotherHandler: Receive = {
      case message => println(s"In another receive handler: ${message}")
    }

    override def preStart(): Unit = {
      log.info("I am starting...")
    }

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: RuntimeException => Restart
      case _ => Stop
    }
  }

  // actor encapsulation
  val system = ActorSystem("AkkaRecap")
  // #1: you can only instantiate actor through the actor system
  // new SimpleActor // not allowed
  val actor = system.actorOf(Props[SimpleActor], "simpleActor")
  // #2: can communicate with actor only through ! or ?
  actor ! "Hello"
  /*
    - messages are sent asynchronously
    - many actors (in the millions) can share a few dozen threads
    - each message is processed and handled ATOMICALLY
    - no need for locking
   */

  // changing actor behaviour + stashing
  // actors can spawn other actors
  // guardians: /system, /user, / = root guardian

  // actors have a defined lifecycle: they can be started, stopped, suspended, resumed, restarted

  // stopping actors - context.stop()
  actor ! PoisonPill  // handled in separate mailbox

  // logging
    // extend ActorLogging

  // supervision
  // when child throws some error, it is suspended and parent is informed about this
  // parent will see the exceptionType and decide what to do with child

  // configure akka infrastructure: dispatchers, routers, mailboxes

  // schedulers
  import scala.concurrent.duration._
  import system.dispatcher
  system.scheduler.scheduleOnce(2 seconds) {
    actor ! "delayed Happy Birthday"
  }

  // Akka Patterns included FSM + Ask pattern
  import akka.pattern.ask
  implicit val timeout = Timeout(3 seconds)

  val future = actor ? "question"

  // the pipe pattern
  import akka.pattern.pipe
  val anotherActor = system.actorOf(Props[SimpleActor], "anotherSimpleActor")
  future.mapTo[String].pipeTo(anotherActor)
}
