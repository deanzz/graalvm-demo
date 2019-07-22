package graalvm.demo.node.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import akka.cluster.Cluster
import akka.routing.{FromConfig, RoundRobinPool}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.util.Timeout
import graalvm.demo.common.{ClusterMessage, UserDefinedSource}
import graalvm.demo.node.{GetNodeServerStates, NodeServerRegistered, NodeServerResource, NodeServerState, NodeUnregister, StopTask, TaskComplete, TaskInfo, TaskState, TaskStatus}
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class NodeLeader(implicit injector: Injector) extends Actor with ActorLogging with AkkaInjectable {

  // pipeId -> Map(subPipeId -> Controller)
  val running = collection.mutable.Map.empty[String, Map[String, UserDefinedSource[_, _, _]]]
  val nodeRegistry = inject[ActorRef]('NodeRegistryProxy)
  val worker = context.actorOf(RoundRobinPool(50).props(AkkaInjectable.injectActorProps[NodeWorker]), "node-worker-router")
  val resource = NodeServerResource(8, 2048, 0, 4096, 10)
  val cluster = Cluster(context.system)
  val decider: Supervision.Decider = {
    case _: Throwable ⇒ Supervision.stop
    case _ ⇒ Supervision.Stop
  }
  implicit val system = inject[ActorSystem]

  import NodeWorker._

  implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

  implicit val ec = system.dispatcher
  implicit val timeout = Timeout(10.seconds)
  var state = NodeServerState(Map.empty[String, TaskState], Some(resource), Some(resource), 0)

  var waitingForStop = false

  override def preStart(): Unit = {
    super.preStart()
    log.info("Ready to register itself")
    nodeRegistry ! state
  }

  override def postStop(): Unit =
    super.postStop()

  override def receive: Receive = {
    case info: TaskInfo =>
      log.info("Receive pipe info {}", info.toProtoString)
      worker ! CreatePipeline(info)

    case PipelineCreationResult(true, info, graphs, controllers, _) =>
      running += (info.id -> controllers)
      val runningState = TaskState(info.id, TaskStatus.RUNNING, Some(info))
      nodeRegistry ! runningState
      state = state.copy(pipes = state.pipes + (info.id -> runningState))

    case PipelineCreationResult(false, info, _, _, thOpt) =>
      val msg = if (thOpt.isEmpty || thOpt.get == null)  "Unknown exception message" else thOpt.get.getLocalizedMessage
      val (pid, infoOpt) = if (info == null) ("Unknown pipeId", None) else (info.id, Some(info))
      val failState = TaskState(pid, TaskStatus.FAILED, infoOpt, msg)
      nodeRegistry ! failState

    case _: GetNodeServerStates =>
      nodeRegistry ! state

    case registered: NodeServerRegistered =>
      log.info("Pipeline manager register {} slots successful to {}", registered.slots, sender().path.toSerializationFormat)

    case StopTask(id) =>
      log.info("Ready to stop pipeline {}", id)
      Try {
        running(id).foreach(c => worker ! StopSource(c._1, c._2))
      } match {
        case Success(res) => log.info(s"Pipeline $id is stopping")
        case Failure(e) => log.error(e, s"stop pipeline $id failed")
          self ! TaskComplete(id, TaskStatus.FAILED, e.getLocalizedMessage)
      }

    case c @ TaskComplete(id, status, msg, count, res) =>
      log.info(s"Pipe $id is $status with final count $count, final message $msg")
      val message = if (msg == null) "Unknown exception message" else msg
      //TODO send final state to registry and persist result, remove id from state
      state.pipes.get(id) match {
        case Some(oldTaskState) =>
          val taskState = oldTaskState.copy(status = status, message = message, result = res)
          nodeRegistry ! taskState
          state = state.copy(pipes = state.pipes - id)
        case None => log.info(s"not exist id: ${id} in state map.")
      }
      running -= id

      if (running.isEmpty && waitingForStop) {
        log.info("All running pipes is stopped, ready to leave the cluster and stop itself")
        cluster.leave(cluster.selfAddress)
      }

    case ClusterMessage.Stop =>
      // Close all pipes and then leave the cluster
      waitingForStop = true
      context.system.scheduler.scheduleOnce(60.seconds) {
        cluster.leave(cluster.selfAddress)
      }
      nodeRegistry ! NodeUnregister(running.keys.toList)
      if(running.isEmpty) {
        cluster.leave(cluster.selfAddress)
      }else{
        running.keys.foreach(id => self ! StopTask(id))
      }

  }

}