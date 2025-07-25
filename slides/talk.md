build-lists: true
slidenumbers: true
autoscale: false

<!-- https://idiomaticsoft.com/post/2024-01-02-effect-systems/ -->

# [fit] Introduction to Kyo
## Adam Hearn

<!-- ---
# Agenda

- What is Kyo?
- Algebraic Effects
- Kyo
  - Syntax + Effects
  - Quirks + Features -->

---

# What is Kyo?

- Kyo is a powerful toolkit for developing with Scala
- Built from a series of standalone modules:
    - `kyo-data`: Low allocation, performant structures
    - `kyo-prelude`: Side-effect free **Algebraic effects**
    - `kyo-core`: Effects for IO, Async, & Concurrency
    - `kyo-scheduler`: high performance adaptive scheduler
      - `kyo-scheduler-zio`: boost your ZIO App!
    - `kyo-zio` & `kyo-cats` (& soon `kyo-monix`)

---
# What are Algebraic Effects?

^ Before we dive into what Algebraic effects, let's talk about Effects in general.

---
# What are ~~Algebraic~~ Effects?

* Effects
    * Descriptions of what you want
    * Produce what you want **when run**
    * Programs as values!
* Effects are backed by **suspension**
    * Suspension defers a computation until later
    * Separation of execution from definition:
        * Flexibility in execution (Retry, Delay, Interrupt)
        * Delayed implementation (`Clock.live` vs `Clock.withTimeControl`)

^ Effects are probably the most overloaded term in FP
^ Most people end up describing Side-Effects, not functional effects

---
<!-- # What's an Effect System?

* Framework for managing, composing, and using effects
* Tools for concurrency, scheduling, and building with effects

^ I like to think of Effect Systems as the 'brand' of effect you use

--- -->
# What are Algebraic Effects?
<!-- TODO: Revisit this; simplify it -->

* Extensible & Composable Effects!
  * Fine-grained control over effect handling
  * Trivial combination of various abilities
  * Separation of effect declaration and implementation
    * User defined effects!
* Handlers: Define how effects are interpreted

<!-- Programming with superpowers -->

^ Often you may see languages refer to specific Effects as Abilities
^ Since most of Kyo doesn't do that, I won't but it's a useful mental model.

---
# Why use Algebraic Effects?

---

# Why use Kyo?

- Includes flexible algebraic effects in **Scala**
- Designed for simplicity and performance
- Effect handling is not restricted to core effects

---
# Kyo Syntax

```scala
val _: String < Sync = Sync.defer("Hello scala.io!")
```

* Infix 'Pending' Type: `Result < Effects`
  * `String < Sync`
* Effects are represented as unordered set:
  * `File < (Sync & Resource)`'

---

#[fit] Effects!

---
### IO: Side-Effect Suspension

```scala
object DB:
  def query[A](sql: SQL[A]): Chunk[A] < IO = ???

object Query:
  val _: Chunk[Person] < Any =
    import AllowUnsafe.embrace.danger
    IO.Unsafe.run(DB.query(sql"select * from person limit 5"))
```

- `IO` are handled individually (`IO.Unsafe.run`)
  - Unsafe APIs require an `AllowUnsafe` evidence

^ The above expression is not fully evaluated and may be pending further suspensions.
^ You might note the `< Any` remaining.
  ^ These are any pending suspensions which are not yet handled or labeled.
  ^ Suspensions can be introduced internally by Kyo, or usages of APIs which introduce suspensions, eg Local.

---
## Abort: Short Circuit
```scala
case class User(email: String)
object User extends KyoApp:
  import UserError._
  enum UserError:
    case InvalidEmail
    case AlreadyExists

  def from(email: String): User < Abort[UserError] =
    if !email.contains('@') then Abort.fail(InvalidEmail)
    else User(email)

  val x: Unit < IO =
    Abort
      .run(from("adam@veak.co"))
      .map:
        case Result.Success(user)      => Console.println(s"Success! $user")
        case Result.Fail(InvalidEmail) => Console.println("Bad email!")
```

^ Abort enables ZIO style short circuiting

---
## Env: Dependency Injection

```scala
abstract class Weather:
  def record(coordinates: Coordinates): Reading < IO

object Weather:
  val live: Weather < (Env[Drone] & Env[Sensor]) =
    for
      drone <- Env.get[Drone]
      sensor <- Env.get[Sensor]
    yield new Weather:
      def record(coordinates: Coordinates): Reading < IO =
        drone.fly(coordinates).andThen(sensor.read)
```

---
## Kyo: Effect Widening

```scala
val a: String < IO = "Hello"
val b: String < (IO & Abort[Exception]) = a
val c: String < (IO & Abort[Exception] & Resource) = b
```

- Computations can be widened to include more effects
- Allows for flexible and composable code
- Plain values can be widened to Kyo computation
  - Widened values are not suspended
  - Widened values do not allocate [^1]

[^1]: Primitives widened to Kyo will box as Scala 3 does not support proper specialization

---
# Kyo: Unnested encoding

```scala
object Write:
  def apply[S](v: String < S): Unit < (S & IO) =
    v.map(Buffer.write(_, "output.txt"))

object MyApp:
    val value: Unit < IO = Write("Hello, World!")
    val effect: Unit < (IO & Abort[IOException]) = Write(Console.readLine)
    val mapped: Unit < (IO & Abort[IOException]) = value.map(_ => effect)
```

* Widening pure values as effects enables fluent composition.
  * Functions can be defined to accept effects, and values can be passed in.
* `F.pure`/`ZIO.succeed` no more!

---
# Kyo: Unnested encoding

```scala
inline def flatMap[B, S2](inline f: Safepoint ?=> A => B < S2): B < (S & S2) =
    map(v => f(v))
```
- Effects can be easily combined using `map`... no need for `flatMap`
- Resulting type includes all unique pending effects

---

# Kyo: Effect Handling

```scala
val a: Int < Abort[Exception] = 42
val b: Result[Exception, Int] < Any = Abort.run(a)
val c: Result[Exception, Int] = b.eval
```

* Effects are handled explicitly
* Order of handling can affect the result type and value

---

# Direct Syntax in Kyo

```scala
val a: String < (Abort[Exception] & IO) =
    defer {
        val b: String = await(Sync.defer("hello"))
        val c: String = await(Abort.get(Right("world")))
        b + " " + c
    }
```

* `defer` and `await` provide a more intuitive syntax

---

## KyoApp: Running your App

```scala
object Main extends KyoApp:
  def app = defer:
    val port = await(System.property[Int]("PORT", 80))
    val options = NettyKyoServerOptions
      .default(enableLogging = false)
      .forkExecution(false)
    val config =
      NettyConfig.default.withSocketKeepAlive
        .copy(lingerTimeout = None)

    val server =
      NettyKyoServer(options, config)
        .host("0.0.0.0")
        .port(port)
    await(Console.println(s"Starting... 0.0.0.0:$port"))
    await(Routes.run(server):
      Routes.add(
        _.get
          .in("echo" / path[String])
          .out(stringBody)
      )(input => input)
    )

  run(app)
```

^ KyoApp is a simple way to run your application.
^ It will automatically handle all effects and suspend any remaining effects.

---

# Conclusion

- Kyo provides a powerful yet simple way to work with algebraic effects
- Offers composability, type safety, and performance
- Enables cleaner, more modular functional programming in Scala

---

# Questions?

<!-- 
- !all effects suspend!
- Suspension + Handling
- ZIO each map/flatMap introduces suspension/resumption

- Kyo continues execution until an effect needs to be handled
https://koka-lang.github.io/koka/doc/index.html


Kyo supports a wide set of audience
- Library Authors
- Novices -->



---
<!-- 
Wrap up:

- Effects
- Kyo:
  - map vs flatMap
  - widening
  - IO/Abort/Async/Env/
  - Running your App

TODO: Kyo Data
kyo-scheduler -->
