name := "sentinel"

description := "reactive file watchers"

version := "0.1.0"

libraryDependencies ++= Seq(
  "org.reactivestreams" % "reactive-streams" % "1.0.0",
  "io.reactivex" %% "rxscala" % "0.26.3",
  "io.reactivex" % "rxjava" % "1.2.1",
  "io.reactivex" % "rxjava-reactive-streams" % "1.2.0",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
)
