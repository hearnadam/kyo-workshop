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
      lazy val first5                   = stream.take(5).run.eval
      lazy val last5                    = stream.drop(5).run.eval
      assertTrue(first5 == Chunk(1, 2, 3, 4, 5)) &&
      assertTrue(last5 == Chunk(6, 7, 8, 9, 10))
    },
    test("effectful") {

      /** Exercise: Effectful Streams
        *
        *   - Initialize a Stream from a given Seq
        *   - use `Stream#map` to update a `Var[Int]`, then return the original element
        *   - Run the Stream to a Chunk, then run the Pending effects to get the final sum & Chunk.
        */
      val stream: Stream[Int, Any] = Stream.init(0 until 1000 by 100)
      lazy val summed: Stream[Int, Var[Int]] =
        stream.map(i => Var.update[Int](acc => acc + i).map(_ => i))
      lazy val effect: Chunk[Int] < Var[Int] = summed.run
      lazy val (sum, chunk)                  = Var.runTuple(0)(effect).eval
      assertTrue(sum == 4500) &&
      assertTrue(chunk == Chunk(0, 100, 200, 300, 400, 500, 600, 700, 800, 900))
    },
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
      lazy val run: Chunk[Int] < Any = stream.run

      // runFold: collect a List of the results, reversing the order of the values.
      lazy val runFold: List[Int] < Any = stream.runFold(List.empty[Int])((a, b) => b :: a)

      // runDiscard: run the Stream, not for the Result but for the Effects
      // Update a `Var`, summing the values of the Stream
      lazy val runDiscard: Unit < Var[Int] = stream.map(i => Var.update[Int](_ + i)).runDiscard

      // runForeach: run the Stream, applying the effect to each element
      // Use `Console.print` to print each element
      lazy val runForeach: Unit < (Abort[java.io.IOException] & IO) =
        stream.runForeach(Console.print)

      assertTrue(run.eval == Chunk(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)) &&
      assertTrue(runFold.eval == List(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)) &&
      assertTrue(Var.runTuple(0)(runDiscard).eval == (55, ())) &&
      assertTrue(IO.Unsafe.run(Abort.run(runForeach)).eval == Result.unit)
    },
  )
}
