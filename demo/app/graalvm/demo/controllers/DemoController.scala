package graalvm.demo.controllers

import akka.actor.ActorRef
import graalvm.demo.DeanLib
import graalvm.demo.actors.PingMan.Ping
import play.api.Configuration
import play.api.mvc.{AbstractController, ControllerComponents}
import javax.inject.{Inject, Named}
import akka.pattern.ask
import akka.util.Timeout
import graalvm.demo.actors.PongMan.Pong

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DemoController @Inject() (configuration: Configuration,
                                @Named("ping-man") pingMan: ActorRef)(implicit cc: ControllerComponents) extends AbstractController(cc){
  val lib = new DeanLib
  implicit val ec = cc.executionContext
  implicit val timeout = Timeout(10.seconds)

  def index = Action.async{
    implicit req =>
      Future(
        Ok(s"hello ${lib.getName}")
      )
  }

  def ping(s: String) = Action.async{
    implicit req =>
      (pingMan ? Ping(s)).map{
        res =>
          val pong = res.asInstanceOf[Pong]
          Ok(s"got ${pong.s}")
      }
  }
}
