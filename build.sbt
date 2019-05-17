name := "aurora"
organization in ThisBuild := "net.asynchorswim"
scalaVersion in ThisBuild := "2.12.8"

lazy val global = project
  .in(file("."))
  .settings(settings)
  .aggregate(
    common,
    clusteredConfigService
  )

lazy val common = project
  .settings(
    name := "common",
    settings,
    libraryDependencies ++= commonDependencies
  )

lazy val clusteredConfigService = project
  .settings(
    name := "clusteredConfigService",
    settings,
    assemblySettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      "com.jason-goodwin" %% "authentikat-jwt" % "0.4.5",
    )
  )
  .dependsOn(
    common
  )

val akkaVersion     = "2.5.22"
val akkaHttpVersion = "10.1.8"

lazy val commonDependencies = Seq(
  "com.typesafe"         % "config"                    % "1.3.3",
  "com.typesafe.akka"    %% "akka-actor-typed"         % akkaVersion,
  "com.typesafe.akka"    %% "akka-stream-typed"        % akkaVersion,
  "com.typesafe.akka"    %% "akka-cluster-typed"       % akkaVersion,
  "com.typesafe.akka"    %% "akka-cluster-typed"       % akkaVersion,
  "com.typesafe.akka"    %% "akka-cluster-typed"       % akkaVersion,
  "com.typesafe.akka"    %% "akka-persistence-typed"   % akkaVersion,
  "com.typesafe.akka"    %% "akka-http"                % akkaHttpVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime"          % scalapb.compiler.Version.scalapbVersion % "protobuf",

  "org.scalatest"        %% "scalatest"                % "3.0.7" % Test,
  "com.typesafe.akka"    %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "com.typesafe.akka"    %% "akka-http-testkit"        % akkaHttpVersion % Test
)

lazy val settings =
commonSettings ++
wartremoverSettings ++
scalafmtSettings

lazy val compilerOptions = Seq(
  "-unchecked",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-deprecation",
  "-encoding",
  "utf8"
)

lazy val commonSettings = Seq(
  scalacOptions ++= compilerOptions,
  resolvers ++= Seq(
    "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  )
)

lazy val wartremoverSettings = Seq(
  wartremoverWarnings in (Compile, compile) ++= Warts.allBut(Wart.Throw)
)

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

lazy val scalafmtSettings =
  Seq(
    scalafmtOnCompile := true,
    scalafmtTestOnCompile := true,
    scalafmtVersion := "1.2.0"
  )

lazy val assemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case _                             => MergeStrategy.first
  }
)
