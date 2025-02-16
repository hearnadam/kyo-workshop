package org.adamhearn

import kyo.*
import kyo.Result.*
import kyo.test.KyoSpecDefault
import zio.test.assertTrue
import zio.test.TestAspect.ignore
import zio.test.TestResult

/** Kyo Streams provide a powerful mechanism for processing sequences of data in a memory-conscious
  * and composable manner.
  *
  * They offer a rich set of operations for transforming, filtering, and combining streams of data,
  * all while maintaining laziness and ensuring stack safety.
  */
object `03_Streams` extends KyoSpecDefault {
  def spec = suite("Streams")(
    test("init/take/drop") {

      /** Exercise: Basic Stream Operations
        *
        *   - Initialize a Stream from a given Seq
        *   - Take the first 5 elements, then run + eval to a Chunk
        *   - Drop the first 5 elements, then run + eval to a Chunk
        */
      val seq: Seq[Int]                 = 1 to 10
      lazy val stream: Stream[Int, Any] = Stream.init(seq)
      lazy val first5: Stream[Int, Any] = ???
      lazy val last5: Stream[Int, Any]  = ???
      assertTrue(first5.run.eval == Chunk(1, 2, 3, 4, 5)) &&
      assertTrue(last5.run.eval == Chunk(6, 7, 8, 9, 10))
    } @@ ignore,
    test("effectful") {

      /** Exercise: Effectful Streams
        *
        *   - Initialize a Stream from a given Seq
        *   - use `Stream#map` to update a `Var[Int]`, then return the original element
        *   - Run the Stream to a Chunk, then run the Pending effects to get the final sum & Chunk.
        */
      val stream: Stream[Int, Any]           = Stream.init(0 until 1000 by 100)
      lazy val summed: Stream[Int, Var[Int]] = ???
      lazy val effect: Chunk[Int] < Var[Int] = ???
      lazy val (sum, chunk)                  = Var.runTuple(0)(effect).eval
      assertTrue(sum == 4500) &&
      assertTrue(chunk == Chunk(0, 100, 200, 300, 400, 500, 600, 700, 800, 900))
    } @@ ignore,
    test("run*") {
      import AllowUnsafe.embrace.danger // for running IO unsafely

      /** Exercise: run*
        *
        *   - Stream includes several run related methods for different use cases
        *   - `run` & `runFold`, generally useful for collecting the result of the Stream
        *   - `runDiscard` is useful for effectfully processing the Stream without retaining the
        *     result
        *   - `runForEach` is useful for peaking at each element of the Stream.
        */
      val stream = Stream.init(1 to 10)

      // run: collect a Chunk of the results
      lazy val run: Chunk[Int] < Any = ???

      // runFold: collect a List of the results, reversing the order of the values.
      lazy val runFold: List[Int] < Any = ???

      // runDiscard: run the Stream, not for the Result but for the Effects
      // Update a `Var`, summing the values of the Stream
      lazy val runDiscard: Unit < Var[Int] = ???

      // runForeach: run the Stream, applying the effect to each element
      // Use `Console.print` to print each element
      lazy val runForeach: Unit < (Abort[java.io.IOException] & IO) = ???

      assertTrue(run.eval == Chunk(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)) &&
      assertTrue(runFold.eval == List(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)) &&
      assertTrue(Var.runTuple(0)(runDiscard).eval == (55, ())) &&
      assertTrue(Abort.run(IO.Unsafe.run(runForeach)).eval == Result.unit)
    } @@ ignore,
  )
}
