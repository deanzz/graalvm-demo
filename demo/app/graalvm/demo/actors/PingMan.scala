package graalvm.demo.actors

import akka.actor.{Actor, ActorRef}
import akka.pattern._
import akka.util.Timeout
import graalvm.demo.actors.PingMan.Ping
import javax.inject.{Inject, Named}

import scala.concurrent.duration.DurationInt

class PingMan @Inject() (@Named("pong-man") pongMan: ActorRef) extends Actor{
  implicit val ec = context.dispatcher
  implicit val timeout = Timeout(10.seconds)
  override def receive: Receive = {
    case p@Ping(n) => (pongMan ? p).pipeTo(sender())
  }
}

object PingMan{
  case class Ping(n: Long)
}
