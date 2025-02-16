package org.adamhearn.app

import kyo.*
import sttp.tapir.*
import sttp.tapir.server.netty.*

object Main extends KyoApp:
  val app = defer:
    val port = System.property[Int]("PORT", 80).now
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
    Console.printLine(s"Starting... 0.0.0.0:$port").now
    Routes
      .run(server)(
        Routes.add(
          _.get
            .in("echo" / path[String])
            .out(stringBody)
        )(input => input)
      )
      .now

  run(app)
