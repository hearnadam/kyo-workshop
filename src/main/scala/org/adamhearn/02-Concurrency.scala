package org.adamhearn

import kyo.*
import kyo.Result.*
import kyo.test.KyoSpecDefault
import zio.test.assertTrue
import zio.test.TestAspect.ignore
import zio.test.TestResult

/** Async effect allows for the asynchronous execution of computations via a managed thread pool.
  *
  * The core function, `Async.run`, forks a new 'green thread', known as a fiber, to execute the
  * computation.
  */
object `02_Async` extends KyoSpecDefault {
  def spec = suite("Async")(
    test("delay") {

      /** Exercise: Async.delay
        *
        * Async.delay executes a computation after waiting for the specified duration. This is
        * useful for testing timeouts and simulating long operations.
        *
        * Implement computation to:
        *   - Take an Int input
        *   - Delay for 50.millis
        *   - Return the input multiplied by 2
        */
      def computation(i: Int): Int < Async =
        Async.delay(50.millis)(i * 2)

      computation(21).map(result => assertTrue(result == 42))
    },
    test("parallel") {

      /** Exercise: Async.parallel
        *
        * Async.parallel executes computations in parallel and collects their results into a tuple.
        * If any computation fails, the error is propagated and remaining computations are
        * interrupted.
        *
        * Implement computation to:
        *   - Take an Int input and return it doubled after a delay
        *   - Fail with Abort if the input is negative
        *   - Use a 100.millis delay
        */
      def computation(i: Int): Int < (Async & Abort[String]) =
        Async.delay(100.millis):
          if i < 0 then Abort.fail("negative")
          else i * 2

      lazy val combined: (Int, Int, Int) < (Abort[String] & Async) =
        Async.parallel(
          computation(1),
          computation(2),
          computation(3),
        )

      Abort.run(combined).map(result => assertTrue(result == Success((2, 4, 6))))
    },
    test("race") {

      /** Exercise: Async.race
        *
        * Async.race evaluates multiple computations concurrently and returns the first successful
        * result. When one computation succeeds, all others are interrupted.
        *
        * Implement computation to:
        *   - Delay proportionally to the input (50.millis * i)
        *   - Fail with `Abort` if i is even
        *   - Return i if odd
        */
      def computation(i: Int): Int < (Async & Abort[String]) =
        Async.delay(50.millis * i):
          if i % 2 == 0 then Abort.fail("even")
          else i

      val racing: Int < (Abort[String] & Async) =
        Async.race(
          computation(3),
          computation(5),
          computation(7),
        )

      // note: `kyo-test` currently doesn't support `Abort[String]`, so we handle it directly
      Abort.run(racing).map(result => assertTrue(result == Success(3)))
    },
    test("timeout") {

      /** Exercise: Async.timeout
        *
        * Async.timeout interrupts a computation after a specified duration with a Timeout error.
        * The error gets added to the Abort effect's error type.
        *
        * Create a computation that:
        *   - Takes longer than the timeout (use Async.delay with 100.millis)
        *   - Has a timeout of 50 milliseconds
        *   - Should result in a Timeout error
        */
      val computation: Int < (Abort[Timeout] & Async) =
        val slow = Async.delay(100.millis)(42)
        Async.timeout(50.millis)(slow)

      Abort.run(computation).map(result => assertTrue(result.isFail))
    },
    test("run") {

      /** Exercise: Async.run
        *
        * Async.run forks a computation on a separate fiber, allowing for concurrent execution. The
        * returned Fiber can be used to monitor or control the computation.
        *
        * Note: Fiber[E, A] is parameterized by error type E and success type A. The E type
        * parameter corresponds to the error type in Abort[E] in the forked computation.
        *
        * Implement work to:
        *   - Take an Int input
        *   - Delay for 50 milliseconds
        *   - Fail with "negative not allowed" if input is negative
        *   - Return input * 2 if positive
        *   - Fork the computation with Async.run
        */
      def computation(i: Int): Fiber[String, Int] < IO =
        val async = Async.delay(50.millis):
          if i < 0 then Abort.fail("negative not allowed")
          else i * 2
        Async.run(async)

      // `fiber#get` awaits the result, translating the `Fiber` error channel back to `Abort `
      // Note how Fiber's `E` channel is translated to/from Abort.
      Abort
        .run(computation(21).map(_.get))
        .map(result => assertTrue(result == Success(42)))
    },
    test("interruption") {

      /** Exercise: Fiber Interruption
        *
        * Running fibers can be interrupted, causing them to complete with a panic. Interruption is
        * useful for cancelling long-running operations.
        *
        * Complete the program to:
        *   - Start a fiber that delays for 100ms returning 42
        *   - Interrupt it immediatelly
        *   - Return the result via fiber.get
        */
      lazy val computation: Int < Async =
        for
          fiber  <- Async.run(Async.delay(100.millis)(42))
          _      <- fiber.interrupt
          result <- fiber.get
        yield result

      Abort.run(computation).map(result => assertTrue(result.isPanic))
    },
  )
}

object `02_Structures` extends KyoSpecDefault {
  def spec =
    suite("structures")(
      suite("Atomic")(
        test("Ref") {

          /** Exercise: AtomicRef
            *
            * AtomicRef provides thread-safe mutable references. Operations on AtomicRef are atomic,
            * preventing race conditions.
            *
            * Create a program that:
            *   - Initializes an AtomicRef with 0
            *   - Updates it by adding 1
            *   - Returns the new value
            */
          lazy val computation: Int < IO =
            for
              ref    <- AtomicRef.init(0)
              result <- ref.updateAndGet(_ + 1)
            yield result

          computation.map(v => assertTrue(v == 1))
        }
      ),
      suite("Queue")(
        test("bounded") {

          /** Exercise: Bounded Queue
            *
            * A bounded queue has a maximum capacity. Once full, offers will fail. This provides
            * natural backpressure for producer-consumer scenarios.
            *
            * Create a program that demonstrates:
            *   - Creating a queue with capacity 2
            *   - Filling it to capacity
            *   - Checking it's full
            *   - Taking an element
            *   - Verifying it's no longer full
            */
          lazy val computation: (Boolean, Maybe[Int], Boolean) < (IO & Abort[Closed]) =
            for
              queue     <- Queue.init[Int](2)
              _         <- queue.offer(1)
              _         <- queue.offer(2)
              full      <- queue.full
              first     <- queue.poll
              afterPoll <- queue.full
            yield (full, first, afterPoll)

          computation.map((full, first, afterPoll) =>
            assertTrue(
              full &&
                first == Maybe(1) &&
                !afterPoll
            )
          )
        }
      ),
    )
}
