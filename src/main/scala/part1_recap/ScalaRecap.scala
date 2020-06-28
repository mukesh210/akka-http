package part1_recap

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ScalaRecap extends App {

  val aCondition: Boolean = false
  def myFunction(x: Int) = {
    // code
    if(x > 4) 42 else 65
  }
  // instructions vs expressions
  // types + type inference

  // OO feature
  class Animal
  trait Carnivore {
    def eat(a: Animal): Unit
  }

  object Carnivore

  // generics
  abstract class MyList[+A]

  // method notations
  1 + 2 // infix notation
  1.+(2)

  // FP
  val anIncrementer: Int => Int = (x: Int) => x + 1
  anIncrementer(1)

  List(1, 2, 3).map(anIncrementer)
  // HOF: map, flatMap, filter
  // for-comprehension
  // Monads: Option, Try

  // Pattern matching!
  val unknown: Any = 2
  val order = unknown match {
    case 1 => "first"
    case 2 => "second"
    case _ => "unknown"
  }

  try {
    // code that throws an exception
    throw new RuntimeException
  } catch {
    case e: Exception => println("I caught one!")
  }

  /**
    * Scala advanced
    */
  // multi-threading
  import scala.concurrent.ExecutionContext.Implicits.global
  val future = Future {
    // long computation
    // executed on some other thread
    42
  }
  // map, flatMap, filter + other niceties e.g. recover, recoverWith

  future.onComplete {
    case Success(value) =>  println(s"Future completed with ${value}")
    case Failure(exception) =>  println(s"Future failed with exception: ${exception}")
  } // executed on some thread... not sure which one

  val partialFunction: PartialFunction[Int, Int] = {
    case 1 => 42
    case 2 => 65
    case _ => 999
  }
  // based on pattern matching!

  // type aliases
  type AkkaReceive = PartialFunction[Any, Unit]
  def receive: AkkaReceive = {
    case 1 => println("Hello!")
    case _ => println("Confused...")
  }

  // Implicits
  implicit val timeout = 3000
  def setTimeout(f: () => Unit)(implicit timeout: Int) = f()

  setTimeout(() => println("timeout"))  // other arg list is injected by the compiler

  // conversions
  // 1) implicit methods
  case class Person(name: String) {
    def greet: String = s"Hi, My name is ${name}"
  }

  implicit def fromStringToPerson(name: String) = Person(name)

  "Mukesh".greet
  // fromStringToPerson("Mukesh").greet

  // 2) Implicit classes
  implicit class Dog(name: String) {
    def bark = println("Bark!")
  }
  "Lassie".bark
  // new Dog("Lassie").bark

  // implicit organization
  // local scope
  implicit val numberOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)
  List(1, 2, 3).sorted//(numberOrdering) => List(3, 2, 1)

  // imported scope
  // importing ExecutionContext for Future

  // companion objects of the types involved in the call
  object Person {
    implicit val personOrdering: Ordering[Person] = Ordering.fromLessThan((a, b) => a.name.compareTo(b.name) < 0)
  }

  // compiler looks for implicit ordering in List and Person companion object
  List(Person("Bob"), Person("Alice")).sorted // (Person.personOrdering)
  // List(Person("Alice"), Person("Bob"))
}
