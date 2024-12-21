package org.adamhearn

import kyo.{Absent, Maybe, Present}
import kyo.Result
import kyo.TypeMap
import kyo.Chunk
import kyo.test.KyoSpecDefault
import zio.test.assertTrue
import zio.test.Spec
import zio.test.TestAspect.ignore
import scala.annotation.tailrec
import zio.internal.ansi.Color.Red

/** kyo-data provides optimized collections for common data types.
  *
  * In these exercises, we'll explore Maybe, Result, Chunk, and TypeMap. These are an important
  * foundation, as many of Kyo's APIs use these structures to improve performance.
  */

object `00_Maybe` extends KyoSpecDefault:
  def spec =
    suite("Maybe[A]")(
      test("nested") {

        /** Exercise: Deep Pattern Matching
          *
          * Goal: Extract a deeply nested value using pattern matching Learning: Nested `Maybe`
          * values allocate at maximum 1 object.
          */
        extension [A](self: Maybe[Maybe[Maybe[Maybe[A]]]])
          def superFlat: Maybe[A] =
            self match
              case Present(Present(Present(maybe))) => maybe
              case _                                => Absent

        val present = Maybe(Maybe(Maybe(Maybe("real"))))
        val absent  = Maybe(Maybe(Maybe(Absent)))

        assertTrue(present.superFlat == Present("real")) &&
        assertTrue(absent.superFlat == Absent)
      },
      test("list") {

        /** Exercise: List[Maybe[A]] -> Maybe[List[A]]
          *
          * Goal: Implement a conversion from List[Maybe[A]] to Maybe[List[A]] Rules:
          *   - If ANY element is Absent, return Absent
          *   - If ALL elements are Present, return Present containing the list of values This
          *     demonstrates Maybe's strict handling of absence vs Option's propagation
          */
        extension [A](list: List[Maybe[A]])
          def sequence: Maybe[List[A]] =
            @tailrec
            def loop(input: List[Maybe[A]], output: List[A]): Maybe[List[A]] =
              input match
                case head :: remaining =>
                  head match
                    case Present(value) => loop(remaining, value :: output)
                    case Absent         => Absent
                case Nil => Present(output.reverse)
            loop(list, Nil)

        val mixed   = List(Present(1), Absent, Present(2))
        val present = List(1, 2, 3, 4, 5).map(Present(_))
        val empty   = List.empty[Maybe[Int]]

        assertTrue(mixed.sequence == Absent) &&
        assertTrue(present.sequence == Present(List(1, 2, 3, 4, 5))) &&
        assertTrue(empty.sequence == Present(Nil))
      },
    )

object `00_Result` extends KyoSpecDefault:
  def spec =
    suite("Result[E, A]")(
      test("catching") {

        /** Exercise: Result.catching offers a typesafe way to handle exceptions.
          *
          * It will catch all exceptions that are subtypes of the type parameter. If the exception
          * is not a subtype, it will be untracked (Panic).
          */
        case class InvalidRequest(message: String) extends Throwable
        case class SQLException()                  extends Throwable

        def impureLogic(request: String): Int =
          if request == "bad" then throw InvalidRequest(request)
          else throw SQLException()

        lazy val fail: Result[InvalidRequest, Int] =
          Result.catching[InvalidRequest](impureLogic("bad"))
        lazy val panic: Result[InvalidRequest, Int] =
          Result.catching[InvalidRequest](impureLogic("foo"))

        assertTrue(fail == Result.fail(InvalidRequest("bad"))) &&
        assertTrue(panic == Result.panic(SQLException()))
      },
      test("panic vs fail") {
        case class TrackedError()
        case class UntrackedError() extends Throwable

        /** Exercise 2: lift untracked errors to tracked errors
          *
          *   - `Fail` is a `Result[E, Nothing]`, and contains an error `E`.
          *   - `Panic` is a `Result[Nothing, Nothing]`, but contains a Throwable.
          *
          * Implement `resurrect` to convert a `Panic` to a `Fail`.
          */
        extension [E, A](self: Result[E, A])
          def resurrect: Result[E | Throwable, A] =
            self match
              case Result.Panic(throwable) => Result.fail(throwable)
              case other                   => other

        lazy val fail: Result[TrackedError, Nothing] = Result.fail(TrackedError())
        lazy val panic: Result[Nothing, Nothing]     = Result.panic(UntrackedError())
        lazy val success: Result[Nothing, Int]       = Result.success(42)

        assertTrue(fail.resurrect.isFail) &&
        assertTrue(panic.resurrect.isFail) &&
        assertTrue(success.resurrect.isSuccess)
      },
      test("error handling") {

        /** Exercise: Handling Errors with Result
          *
          * Goal: Demonstrate Result's ability to handle multiple error types
          */
        import ValidationError.*
        enum ValidationError:
          case EmptyInput
          case InvalidFormat

        import ProcessingError.*
        enum ProcessingError:
          case CreditCardDecline
          case Mismatch(input: String, expected: String)

        def validate(input: String): Result[ValidationError, Int] =
          if input.isEmpty then Result.fail(EmptyInput)
          else
            input.toIntOption match
              case Some(id) => Result.success(id)
              case None     => Result.fail(InvalidFormat)

        // If the user ID is `42`, succeed with "Approved"
        // If the user ID is `1`, fail with CreditCardDecline
        // Otherwise, fail with a Mismatch error
        def charge(id: Int): Result[ProcessingError, String] =
          id match
            case 42 => Result.success("Approved")
            case 1  => Result.fail(CreditCardDecline)
            case _  => Result.fail(Mismatch(id.toString, "42"))

        def process(input: String): Result[ValidationError | ProcessingError, String] =
          validate(input).flatMap(charge)

        // use pattern matching to convert the result to a string
        def handle(result: Result[ValidationError | ProcessingError, String]): String =
          result match
            case Result.Success(value)                  => value
            case Result.Fail(CreditCardDecline)         => "Transaction Declined"
            case Result.Fail(Mismatch(input, expected)) => s"Mismatch: $input <> $expected"
            case Result.Panic(error)                    => throw error

        assertTrue(handle(process("42")) == "Approved") &&
        assertTrue(handle(process("1")) == "Transaction Declined") &&
        assertTrue(handle(process("-1")) == "Mismatch: -1 <> 42")
      },
    )

object `00_Chunk` extends KyoSpecDefault:
  def spec =
    suite("Chunk[A]")(
      test("apply") {

        /** Exercise: Chunk.apply
          *
          * Chunks are a specialized version of Seq that optimizes for performance. You can use a
          * Chunk wherever you would use a Seq.
          *
          * Chunks can be created using a varargs constructor, or from an Array or Seq.
          */
        lazy val chunk: Chunk[Int] = Chunk(1, 2, 3, 4, 5)
        lazy val seq: Seq[Int]     = chunk

        assertTrue(chunk == Chunk(1, 2, 3, 4, 5)) &&
        assertTrue(chunk == seq)
      },
      test("from") {

        /** Exercise: Chunk.from (Array vs Seq)
          *
          * Chunk.from offers a safe way to convert an Array or Seq to a Chunk.
          *
          * Note: elements of an Array must be a subtype of AnyRef.
          */
        lazy val array: Array[String]      = Array("a", "b", "c")
        lazy val chunkArray: Chunk[String] = Chunk.from(array)
        lazy val seq: Seq[Int]             = 0 to 100
        lazy val chunkSeq: Chunk[Int]      = Chunk.from(seq)

        // Since `Chunk` extends `Seq`, you can check equality with other Seq implementations
        assertTrue(chunkArray == array.toSeq) &&
        assertTrue(chunkSeq == seq)
      },
      test("flattenChunk") {

        /** Exercise 3: Chunk#flattenChunk
          *
          *   - While Chunk does extends Seq, it sometimes offers more efficient implementations
          *   - `flattenChunk` is one such method.
          *   - This method will only work for `Chunk[A]` where `A` is a subtype of `Chunk[_]`.
          */
        lazy val chunk: Chunk[Chunk[Int]] = Chunk(Chunk(1, 2), Chunk(3, 4), Chunk(5, 6))
        lazy val flattened: Chunk[Int]    = chunk.flattenChunk

        assertTrue(flattened == chunk.flatten) && // flatten & flattenChunk produce the same result
        assertTrue(flattened == Chunk(1, 2, 3, 4, 5, 6))
      },
    )
object `00_TypeMap` extends KyoSpecDefault:
  sealed trait DBConnection:
    def maxConnections: Int

  case class Postgres() extends DBConnection:
    def maxConnections = 10

  case class Redis() extends DBConnection:
    def maxConnections = 50

  case class Mongo() extends DBConnection:
    def maxConnections = 20

  def spec =
    suite("TypeMap[A]")(
      test("add/get") {

        /** Exercise: Create a TypeMap with a Postgres connection.
          *
          *   - What can you get from the map?
          *     - What happens if you ascribe the type `TypeMap[DBConnection]`?
          *   - If you add `42`, what's the type of the map?
          */
        lazy val connections: TypeMap[Postgres] = TypeMap.empty.add(Postgres())
        lazy val widened: TypeMap[DBConnection] = connections
        lazy val andInt                         = connections.add(42)

        assertTrue(connections.get[Postgres] == Postgres()) &&
        assertTrue(
          widened.get[DBConnection].isInstanceOf[Postgres]
        ) && // note: cannot request a specific type because we erased that information by widening to the `DBConnection` trait.
        assertTrue(andInt.get[Int] == 42)
      },
      test("prune") {

        /** Exercise: Experiment with `TypeMap#prune`
          *
          * First prune to just `Redis`. Then prune to just `DBConnection`.
          */
        val original = TypeMap.empty
          .add(Postgres())
          .add(Redis())
          .add(Mongo())

        lazy val redis: TypeMap[Redis]                = original.prune[Redis]
        lazy val dbConnections: TypeMap[DBConnection] = original.prune[DBConnection]

        assertTrue(redis.get[Redis] == Redis()) &&
        assertTrue(redis.size == 1) &&
        assertTrue(dbConnections.get[DBConnection].isInstanceOf[DBConnection])
      },
      test("union") {

        /** Exercise: Create and combine two TypeMaps
          */
        lazy val dbs: TypeMap[Postgres] = TypeMap(Postgres())

        lazy val config: TypeMap[Int & String & Boolean] = TypeMap(42, "str!", true)

        lazy val combined = dbs.union(config)

        assertTrue(combined.size == dbs.size + config.size)
      },
    )
