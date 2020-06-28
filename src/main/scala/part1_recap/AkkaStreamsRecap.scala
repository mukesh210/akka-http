package part1_recap

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object AkkaStreamsRecap extends App {

  implicit val system = ActorSystem("AkkaStreamsRecap")
  implicit val materializer = ActorMaterializer() // allocates resource for constructing akka streams components
  import system.dispatcher

  val source = Source(1 to 100)
  val sink = Sink.foreach[Int](println)
  val flow = Flow[Int].map(_ + 1)

  val runnableGraph = source.via(flow).to(sink)
  val simpleMaterializedValue = runnableGraph
    // .run()

  // MATERIALIZED VALUE
  val sumSink = Sink.fold[Int, Int](0)((currentSum, element) => currentSum + element)
//  val sumFuture: Future[Int] = source.runWith(sumSink)
//
//  sumFuture onComplete {
//    case Success(value) => println(s"Sum is: ${value}")
//    case Failure(ex)  => println(s"Sum failed with $ex")
//  }
//
//  val anotherMaterializedValue = source.viaMat(flow)(Keep.right).toMat(sink)(Keep.left)
//    //.run()

  /*
    1 - materializing a graph means materializing ALL the components
    2 - a materialized value can be ANYTHING AT ALL
   */

  /*
  BACKPRESSURE actions
    - buffers elements
    - apply a strategy in case the buffer overflows
    - fail the entire stream
   */

  val bufferedFlow = Flow[Int].buffer(10, OverflowStrategy.dropHead)
  source.async
    .via(bufferedFlow).async
    .runForeach { e =>
      Thread.sleep(100)
      println(e)
    }

}
