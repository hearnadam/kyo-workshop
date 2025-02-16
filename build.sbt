Global / onChangedBuildSource := ReloadOnSourceChanges

val kyoVersion = "0.16.2"

lazy val root = project
  .in(file("."))
  .settings(
    name         := "kyo-workshop",
    scalaVersion := "3.6.2",
    scalacOptions ++= Seq(
      "-encoding",
      "utf8",
      "-feature",
      "-unchecked",
      "-explain",
      "-deprecation",
      "-new-syntax",
      "-Wvalue-discard",
      "-Wnonunit-statement",
      "-Wconf:msg=(discarded.*value|pure.*statement):error",
    ),
    libraryDependencies ++= Seq(
      "io.getkyo"     %% "kyo-core"        % kyoVersion,
      "io.getkyo"     %% "kyo-direct"      % kyoVersion,
      "io.getkyo"     %% "kyo-combinators" % kyoVersion,
      "io.getkyo"     %% "kyo-sttp"        % kyoVersion,
      "io.getkyo"     %% "kyo-tapir"       % kyoVersion,
      "io.getkyo"     %% "kyo-zio"         % kyoVersion,
      "io.getkyo"     %% "kyo-zio-test"    % kyoVersion,
      "org.jline"      % "jline"           % "3.24.1",
      "ch.qos.logback" % "logback-classic" % "1.5.14",
    ),
    run / fork        := true,
    scalafmtOnCompile := true,
  )

addCommandAlias("fmt", "scalafmtAll; scalafmtSbt")
addCommandAlias("format", "scalafmtAll; scalafmtSbt")
