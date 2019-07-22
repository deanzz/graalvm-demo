package graalvm.demo.node.actors


import akka.Done
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import akka.stream.Supervision
import akka.stream.scaladsl.RunnableGraph
import graalvm.demo.common.UserDefinedSource
import graalvm.demo.node.TaskInfo
import graalvm.demo.node.pipeline.PipelineFactory
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success


class NodeWorker(implicit injector: Injector) extends Actor with ActorLogging with AkkaInjectable {
  import NodeWorker._
  implicit val system = inject[ActorSystem]
  import system.dispatcher
  val decider: Supervision.Decider = {
    case t: Throwable ⇒
      log.error(t, s"decider got error")
      Supervision.stop
    case _ ⇒ Supervision.Stop
  }

  implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))
  override def receive: Receive = {
    case CreatePipeline(info) =>
      try {
        log.info("Start to create stream for pipeline {}", info.id)
        val (graph, controllers) = PipelineFactory.create(info, sender())
        graph.run() onComplete {
          case Success(value) =>
            log.info("Pipeline {} complete", info.id)
          case Failure(exception) =>
            log.error(exception, "Pipeline {} execute failed", info.id)
        }
        //Start to push message to sources
        info.source.foreach(s => controllers(s.id).start())
        sender() ! PipelineCreationResult(true, info, graph, controllers, None)
      } catch {
        case t: Throwable =>
          log.error(t, "Initial pipeline {} failed", info.id)
          sender() ! PipelineCreationResult(false, info, null, null, Some(t))
      }

    case StopSource(id, c) =>
      try {
        c.close()
        log.info(s"Pipeline source ${id} is closed")
      } catch {
        case ex: Throwable =>
          log.error(ex, "Error close source {}", id)
      }
  }
}

object NodeWorker {
  case class CreatePipeline(info: TaskInfo)
  case class PipelineCreationResult(
                                     success: Boolean,
                                     info: TaskInfo,
                                     graphs: RunnableGraph[Future[Done]],
                                     controllers: Map[String, UserDefinedSource[_, _, _]],
                                     ex: Option[Throwable]
                                   )
  case class StopSource(id: String, controller: UserDefinedSource[_, _, _])
}
