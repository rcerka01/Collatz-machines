import sbt.Keys.libraryDependencies

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

val mainDependencies = Seq()
val testDependencies = Seq()

lazy val root = (project in file("."))
  .settings(
    name := "coding-exercise",
    libraryDependencies ++= mainDependencies ++ testDependencies
  )

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "3.5.1",
  "org.typelevel" %% "log4cats-slf4j" % "2.6.0",

  "ch.qos.logback" % "logback-classic" % "1.4.8",

  "com.github.pureconfig" %% "pureconfig-core" % "0.17.4",
  "com.github.pureconfig" %% "pureconfig-generic" % "0.17.4",

  "co.fs2" %% "fs2-core" % "3.7.0",
  "co.fs2" %% "fs2-io" % "3.7.0",

  "org.http4s" %% "http4s-dsl" % "0.23.18",
  "org.http4s" %% "http4s-jdk-http-client" % "0.9.1",
  "org.http4s" %% "http4s-blaze-server" % "0.23.14",
  "org.http4s" %% "http4s-circe" % "0.23.18",

  "io.circe" %% "circe-core" % "0.14.5",
  "io.circe" %% "circe-generic" % "0.14.5",
  "io.circe" %% "circe-parser" % "0.14.5",

  "org.scalatest" %% "scalatest" % "3.2.16" % Test,
  "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test,
  "org.scalatestplus" %% "mockito-4-6" % "3.2.15.0" % Test
)
