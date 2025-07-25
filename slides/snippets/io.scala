//> using dep io.getkyo::kyo-core:0.13.2

import kyo.*

case class Person(name: String, age: Int)
class SQL[A](sql: String) extends AnyVal

extension (sc: StringContext)
  def sql[A](args: Any*): SQL[A] = new SQL(sc.s(args*))

object DB:
  private val local = Local.init(())
  def query[A](sql: SQL[A]): Chunk[A] < Sync = local.get.map(_ => Sync.defer(println(s"querying $sql"))).as(Chunk.empty)

object MyApp extends KyoApp:
  val x: Chunk[Person] < Any = 
    import AllowUnsafe.embrace.danger
    IO.Unsafe.run(DB.query(sql"select * from person limit 5"))
  run {42}