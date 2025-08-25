package org.adamhearn

import kyo.*
import kyo.Result.*
import kyo.test.KyoSpecDefault
import zio.test.assertTrue
import zio.test.assertCompletes
import zio.test.TestAspect.ignore
import zio.test.TestResult
import java.io.IOException

/** kyo-prelude & kyo-core contain Algebraic/Functional Effects.
  *
  * These exercises are intented to give a strong intuition about effects. Since effects can be
  * implemented outside the core library (in your codebase), a comprehensive description of all
  * effects is not possible. For that reason, this section is focused on intuition and examples.
  */
object `01_Composition` extends KyoSpecDefault {
  def spec =
    suite("Composition")(
      test("branching") {

        /** Exercise: Branching
          *
          * Kyo enables effectful branching via implicit widening. Pure values can be widened to Kyo
          * Computations.
          *
          * Write a function `even`:
          *   - If the input is even, returns the input.
          *   - If the input is odd, aborts with "odd".
          */
        def even(i: Int): Int < Abort[String] = ???

        for
          e <- Abort.run(even(42))
          o <- Abort.run(even(43))
        yield assertTrue(e == Result.succeed(42)) &&
          assertTrue(o == Result.fail("odd"))
      } @@ ignore,
      test("multiple") {

        /** Exercise: multiple effects
          *
          * Try combining multiple effects in a `for`.
          *
          * What is the combined type? Can the test run this effect?
          */
        val aborting: Int < Abort[Throwable] = 42
        val sideEffecting: Unit < Sync       = Sync.defer(println("hello"))
        val mutating: Int < Var[Int]         = Var.update((i: Int) => i + 42)
        val result: TestResult               = assertCompletes

        lazy val combined: TestResult < ??? = ???
        // note: `kyo-test` cannot run all effects, specifically those that require input (Var, Env, etc).
        // To get the test to compile, we can manually 'run' Var[Int]:
        lazy val withoutVar = Var.run(0)(combined)
        withoutVar
      } @@ ignore,
    )
}

object `01_Effects` extends KyoSpecDefault {
  def spec =
    suite("effects")(
      test("Abort") {

        /** Exercise: Abort
          *
          * Abort is short circuiting effect that can fail with a value of type `E`.
          *
          * Effects generally include a `run` method that will handle that effect, returning a new
          * Kyo computation. When all effects are handled, the final result is `A < Any`. `.eval`
          * will convert a `A < Any` to `A`, evaluating any remaining suspensions.
          */
        case class Fatal() extends Exception
        lazy val fail: Int < Abort[String]  = ???
        lazy val panic: Int < Abort[String] = ???

        assertTrue(Abort.run(fail).eval == Result.fail("fail")) &&
        assertTrue(Abort.run(panic).eval == Result.panic(Fatal()))
      } @@ ignore,
      test("Env") {

        /** Exercise: Env
          *
          * Env is an effect that allows dependency injection of values. It can inject single values
          * or multiple values using a typemap.
          *
          * Try providing a single value, then multiple with a TypeMap. What happens if you don't
          * provide all the dependencies?
          */
        val single: Int < Env[Int] = Env.get[Int]
        val multiple: String < (Env[Boolean] & Env[String]) =
          for
            b <- Env.get[Boolean]
            s <- Env.get[String]
          yield s"$b $s"

        lazy val singleProvided: Int < Any      = ???
        lazy val multipleProvided: String < Any = ???

        for
          a <- singleProvided
          b <- multipleProvided
        yield assertTrue(a == 24) && assertTrue(b == "true hello")
      } @@ ignore,
      test("Var") {

        /** Exercise: Var
          *
          * Var enables maintaining updatable state without mutation. Var will maintain state
          * throughout the computation.
          *
          * Use var to write a recursive method to compute the nth fibonacci number.
          */
        def fib(n: Int): Long < Var[Chunk[Long]] = ???

        lazy val fifty: Long = Var.run(Chunk(0L, 1L))(fib(50)).eval
        assertTrue(fifty == 12586269025L)
      } @@ ignore,
      test("Resource") {

        /** Exercise: Scope
          *
          * Scope is an effect that manages a resource. It can be used to manage lifetime of
          * resourceful values like Files.
          *
          * Scope is acquired when Scope.acquireRelease is called and released when the computation
          * completes. This allows for flexible control over resource lifetimes.
          */

        // used to peak at files after they are closed.
        val files = AtomicRef.Unsafe.init(Chunk.empty[File])(using AllowUnsafe.embrace.danger).safe

        case class File(path: String, closed: AtomicBoolean, reads: AtomicInt):
          def read: String < (Sync & Abort[IOException]) =
            for
              c <- closed.get
              _ <- Abort.when(c)(new IOException("File already closed"))
              r <- reads.incrementAndGet
            yield s"$path read $r times"
          def close: Unit < Sync = Sync.defer(closed.set(true))
          def state: (String, Boolean, Int) < Sync =
            for
              c <- closed.get
              r <- reads.get
            yield (path, c, r)

        object File:
          def open(path: String): File < Sync =
            for
              closed <- AtomicBoolean.init(false)
              reads  <- AtomicInt.init
              file = File(path, closed, reads)
              _ <- files.updateAndGet(_.append(file)) // for testing
            yield file

        // define a function to open a file, acquiring and releasing a resource.
        def open(path: String): File < (Sync & Scope) =
          Scope.acquireRelease(File.open(path))(_.close)

        // Open 2 files:
        // `first`, open a file named 'one', then invoke 'read' wrapping full expression in `Scope.run`.
        // `second`, open file named 'two' wrapped in `Scope.run`, then invoke 'read'

        lazy val one = ???
        lazy val two = ???

        for
          o  <- Abort.run(one)
          t  <- Abort.run(two)
          fs <- files.get.map(chunk => Kyo.foreach(chunk)(_.state))
        yield assertTrue(fs == Chunk(("one", true, 1), ("two", true, 0))) &&
          assertTrue(o.isSuccess) &&
          assertTrue(t.isFailure)
      } @@ ignore,
      test("Emit") {

        /** Exercise: Emit
          *
          * Emit is an effect that is used to accumulate values. It's useful to maintain a record of
          * the computation you create.
          *
          * Write a function to emit `n` numbers, doubling each number and looping until `n` is less
          * than or equal to 0.
          */
        def emitN(n: Int): Unit < Emit[Int] = ???

        Emit
          .run(emitN(10))
          .map:
            case (chunk, _) =>
              assertTrue(chunk == Chunk(20, 18, 16, 14, 12, 10, 8, 6, 4, 2))
      } @@ ignore,
    )
}
