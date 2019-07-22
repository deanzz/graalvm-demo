package graalvm.demo.actors

import akka.actor.Actor
import graalvm.demo.actors.PingMan.Ping
import graalvm.demo.actors.PongMan.Pong
import javax.inject.Inject

class PongMan @Inject()() extends Actor{
  override def receive: Receive = {
    case Ping(n) =>
      val res = (0 to 10000).flatMap(i => fibSeq(i.toLong))
      sender() ! Pong(res.sum.toString)
  }

  def fibSeq(n: Long): List[Long] = {
    var ret = scala.collection.mutable.ListBuffer[Long](1, 2)
    while (ret.last < n) {
      val temp = ret.last + ret(ret.length - 2)
      if (temp >= n) {
        return ret.toList
      }
      ret += temp
    }
    ret.toList
  }
}

object PongMan{
  case class Pong(s: String)
}
