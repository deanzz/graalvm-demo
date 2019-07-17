
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
    libraryDependencies ++= Seq(

    )
  )

lazy val demo = (project in file("demo"))
  .enablePlugins(PlayScala, GraalVMNativeImagePlugin)
  .disablePlugins(PlayLogback)
  .dependsOn(common % "compile->compile;test->test")
  .settings(
    libraryDependencies ++= Seq(
      guice
    )
  )