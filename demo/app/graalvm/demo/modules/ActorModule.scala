package graalvm.demo.modules

import com.google.inject.AbstractModule
import graalvm.demo.actors.{PingMan, PongMan}
import play.api.libs.concurrent.AkkaGuiceSupport

class ActorModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindActor[PingMan]("ping-man")
    bindActor[PongMan]("pong-man")
  }
}
