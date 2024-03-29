package graalvm.demo.actors

import akka.actor.Actor
import graalvm.demo.actors.PingMan.Ping
import graalvm.demo.actors.PongMan.Pong
import javax.inject.Inject

class PongMan @Inject()() extends Actor{
  private val runtime = Runtime.getRuntime
  import runtime.{ totalMemory, freeMemory }

  override def receive: Receive = {
    case Ping(n) =>
      val res = (0 to 1000000).foldLeft(0L){
        case (sum, i) =>
          val s = fibSeq(i.toLong)
          val r = sum + s.sum
          val total = totalMemory.toDouble / 1024 / 1024
          val free = freeMemory.toDouble / 1024 / 1024
          if (i % 100000 == 0) println(s"total memory = ${total}MB, freeMemory = ${free}MB, usedMemory = ${total - free}MB")
          r
      }
      sender() ! Pong(res.toString)
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
