lazy val akkaHttpVersion = "10.1.10"
lazy val akkaVersion = "2.5.25"

resolvers += Resolver.bintrayRepo("dnvriend", "maven")

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.13.1",
      version := "0.1"
    )),
    name := "group-feed",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-remote" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",

      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.2" % Test
    ),
    // When running tests, we use this configuration
    javaOptions in Test += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.test.conf",
    // We need to fork a JVM process when testing so the Java options above are applied
    fork in Test := true,
  )


