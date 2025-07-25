package org.adamhearn.app

import kyo.*
import sttp.tapir.*
import sttp.tapir.server.netty.*

object Main extends KyoApp:
  val app =
    for {
      port <- System.property[Int]("PORT", 80)
      options = NettyKyoServerOptions
        .default(enableLogging = false)
        .forkExecution(false)
      config = NettyConfig.default.withSocketKeepAlive
        .copy(lingerTimeout = None)
      server = NettyKyoServer(options, config)
        .host("0.0.0.0")
        .port(port)
      _ <- Console.printLine(s"Starting... 0.0.0.0:$port")
      _ <- Routes
        .add(
          _.get
            .in("echo" / path[String])
            .out(stringBody)
        )(s => s)
    } yield ()

  run(Routes.run(app))
