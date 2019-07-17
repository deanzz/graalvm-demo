package graalvm.demo.actors

import akka.actor.Actor
import graalvm.demo.actors.PingMan.Ping
import graalvm.demo.actors.PongMan.Pong
import javax.inject.Inject

class PongMan @Inject()() extends Actor{
  override def receive: Receive = {
    case Ping(s) =>
      sender() ! Pong(s"Pong($s)")
  }
}

object PongMan{
  case class Pong(s: String)
}
