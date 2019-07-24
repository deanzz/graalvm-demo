import sbt.Keys.mappings
import sbtprotoc.ProtocPlugin.ProtobufConfig
import sbtprotoc.ProtocPlugin.autoImport.PB

name := "graalvm-demo"

version := "0.1"

scalaVersion := "2.12.8"

lazy val graalvm = (project in file(".")).aggregate(
  common,
  demo
)

lazy val common = (project in file("common"))
  .enablePlugins(GraalVMNativeImagePlugin)
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    ),
    PB.targets in Test := Seq(
      scalapb.gen() -> (sourceManaged in Test).value
    ),
    scalaSource in ProtobufConfig := sourceManaged.value,
    unmanagedResourceDirectories in Compile ++= (PB.protoSources in Compile).value,
    libraryDependencies ++= Seq(
      "org.mongodb.scala" %% "mongo-scala-driver" % "2.6.0",
      "com.typesafe.akka" %% "akka-cluster" % "2.5.21",
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
      "com.google.protobuf" % "protobuf-java" % "3.7.1" % "protobuf",
      "io.github.scalapb-json" %% "scalapb-playjson" % "0.11.0-M3",
      "com.typesafe.akka" %% "akka-discovery" % "2.5.21",
      "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % "1.0.0",
      "com.lightbend.akka.management" %% "akka-management" % "1.0.0",
      "com.lightbend.akka.management" %% "akka-management-cluster-http" % "1.0.0",
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "1.0.0",
      "com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % "1.0.0",
      "com.lightbend.akka" %% "akka-stream-alpakka-avroparquet" % "1.0.0",
      "com.typesafe.akka" %% "akka-cluster-metrics" % "2.5.21",
      "io.kamon" % "sigar-loader" % "1.6.6-rev002",
      "com.typesafe.akka" %% "akka-slf4j" % "2.5.21",
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.4.1",
      "org.apache.logging.log4j" % "log4j-api" % "2.4.1",
      "org.apache.logging.log4j" % "log4j-core" % "2.4.1",
      "org.scalatest" %% "scalatest" % "3.0.1" % Test,
    )
  )

//sbt 'show graalvm-native-image:packageBin'
lazy val demo = (project in file("demo"))
  .enablePlugins(PlayScala, GraalVMNativeImagePlugin)
  .disablePlugins(PlayLogback)
  .dependsOn(common % "compile->compile;test->test")
  .settings(
    libraryDependencies ++= Seq(
      guice
    ),
    //mainClass in Compile := Some("play.core.server.ProdServerStart"),
    graalVMNativeImageOptions ++= Seq(
      //"-H:+ReportUnsupportedElementsAtRuntime",
      "--report-unsupported-elements-at-runtime",
      "-H:+ReportExceptionStackTraces",
      "--verbose",
      "--allow-incomplete-classpath",
      "--initialize-at-build-time",
      "--no-fallback",
      "-H:ConfigurationFileDirectories=/Users/deanzhang/work/code/github/graalvm-demo/demo/conf/",
      //"-H:+PrintClassInitialization",
      //"-H:ClassInitialization=org.apache.logging.log4j.core.appender.AbstractManager:run_time,org.apache.logging.log4j.core.appender.rolling.RollingFileManager:build_time"
      //"-H:PrintFlags=+",
      //"-H:Class=play.core.server.ProdServerStart"
      //"--force-fallback"
    )
  )

lazy val node = (project in file("node"))
  .enablePlugins(GraalVMNativeImagePlugin)
  .dependsOn(common % "compile->compile;test->test")
  .settings(
    libraryDependencies ++= Seq(
      "org.scaldi" %% "scaldi" % "0.5.8",
      "org.scaldi" %% "scaldi-akka" % "0.5.8"
    ),
    //unmanagedResourceDirectories in Compile <+= resourceDirectory.value / "reflectConfig.json",
    mainClass in Compile := Some("graalvm.demo.node.NodeServer"),
    /*mappings in(Compile, packageBin) ~= { t =>
      t.filter(f => !(f._1.getName.endsWith(".conf") || f._1.getName.endsWith(".xml")))
    },*/
    //mappings in Universal ++= (resourceDirectory in Compile).value.listFiles().toSeq.map(f => (f, f.name)),
    //scriptClasspath := Seq("*", "../conf"),
    graalVMNativeImageOptions ++= Seq(
      "--report-unsupported-elements-at-runtime",
      //"-H:+ReportUnsupportedElementsAtRuntime",
      "-H:+ReportExceptionStackTraces",
      "--verbose",
      "--allow-incomplete-classpath",
      "--initialize-at-build-time",
      "--no-fallback",
      //"--static",
      "-H:ConfigurationFileDirectories=/Users/deanzhang/work/code/github/graalvm-demo/node/src/main/resources/"
      //"-H:ReflectionConfigurationFiles=reflectConfig.json"

      //"--force-fallback"
    )
  )
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(UniversalPlugin)