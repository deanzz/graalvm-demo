package graalvm.demo.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Terminated}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberExited, MemberRemoved, ReachabilityEvent, ReachableMember, UnreachableMember}
import akka.util.Timeout
import graalvm.demo.actors.PipelineRegistry.{GetPipeServers, NodeState, PipelineSubmitResponse}
import graalvm.demo.actors.PipelineStateForQuery.{GetEnoughResourceNode, NodeJoined, NodeRemoved}
import graalvm.demo.common.ClusterRole
import graalvm.demo.node.{NodeServerRegistered, NodeServerResource, NodeServerState, NodeUnregister, StopTask, TaskInfo, TaskState, TaskStatus}
import javax.inject.Inject
import play.api.inject.Injector
import akka.pattern.ask

import scala.concurrent.duration.DurationInt

class NodeRegistry @Inject()(injector: Injector, nodeStateQuery: ActorRef, monitor: ActorRef) extends Actor with ActorLogging {

  val nodes = scala.collection.mutable.Map.empty[Address, NodeState]
  val unreachable = scala.collection.mutable.Map.empty[Address, NodeState]
  val pipeMap = scala.collection.mutable.Map.empty[String, ActorRef]
  val cluster = Cluster(context.system)

  implicit val timeout = Timeout(10.seconds)
  implicit val ec = context.dispatcher

  override def preStart(): Unit = {
    super.preStart()
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[ReachabilityEvent])
    log.info("The main pipeline registry is started")
    // Tell cluster monitor to broadcast GetPipeState for fetching running states, recover from all the pipeline managers
    monitor ! GetPipeServers
  }

  override def receive: Receive = {
    case state: NodeServerState =>
      log.info("New node {} registered", sender().path.toSerializationFormat)
      // sync to query
      nodeStateQuery forward state
      nodes += sender().path.address -> NodeState(
        sender(),
        collection.mutable.Map(state.pipes.toSeq: _*),
        state.getTotal,
        state.getFree,
        state.load
      )
      context.watch(sender())
      state.pipes.values.foreach{
        ps =>
          pipeMap += (ps.id -> sender())
      }
      sender() ! NodeServerRegistered(state.getFree.slots)

    case info: TaskInfo =>
      log.info(s"receive pipe info: ${info.toProtoString}")
      val theSender = sender()
      if (nodes.isEmpty) {
        theSender ! PipelineSubmitResponse(info.id, TaskStatus.FAILED, "No Node server available")
      } else {
        // get pipeline that remaining enough resource from query
        (nodeStateQuery ? GetEnoughResourceNode).map {
          p =>
            val state = p.asInstanceOf[Option[NodeState]]
            if (state.isEmpty) {
              log.error("No Node server available!!")
              theSender ! PipelineSubmitResponse(info.id, TaskStatus.FAILED, "No Node server available")
            } else {
              state.get.node ! info
              theSender ! PipelineSubmitResponse(info.id, TaskStatus.RUNNING, "Submit successful")
            }
        }.recover{
          case t: Throwable => log.error(t, "GetAvailablePipeline and submit PipeInfo failed")
        }
      }
    case state: TaskState =>
      // sync to query
      nodeStateQuery forward state
      state.status match {
        case TaskStatus.RUNNING =>
          log.info("Pipeline {} is running", state.id)
          pipeMap += (state.id -> sender())
          if (nodes.contains(sender().path.address)) {
            nodes(sender().path.address).tasks.put(state.id, state)
          } else {
            nodes.put(
              sender().path.address,
              NodeState(sender(), collection.mutable.Map(state.id -> state), NodeServerResource(), NodeServerResource())
            )
          }
        case TaskStatus.COMPLETE =>
          log.info("Pipeline {} is {}", state.id, state.status)
          pipeMap -= state.id
          if (nodes.contains(sender().path.address)) {
            nodes(sender().path.address).tasks.remove(state.id)
          }

        case TaskStatus.TERMINATED =>
          log.info("Pipeline {} is {}", state.id, state.status)
          pipeMap -= state.id
          if (nodes.contains(sender().path.address)) {
            nodes(sender().path.address).tasks.put(state.id, state)
          }

        case TaskStatus.FAILED =>
          log.info("Pipeline {} is {}", state.id, state.status)
          pipeMap -= state.id
          if (nodes.contains(sender().path.address)) {
            nodes(sender().path.address).tasks.put(state.id, state)
          }
      }

    case stop @ StopTask(id) =>
      pipeMap.get(id) match {
        case Some(actor) => actor ! stop
        case _ => log.info(s"not exist running pipe: ${id}")
      }


    case UnreachableMember(member) if member.hasRole(ClusterRole.pipeline.toString) =>
      log.info("Unreachable Node server address {}", member.address.toString)
      unreachable += (member.address -> nodes(member.address))
      nodes -= member.address
      // sync to query
      nodeStateQuery ! NodeRemoved(member.address)

    case ReachableMember(member) if member.hasRole(ClusterRole.pipeline.toString) && unreachable.contains(member.address) =>
      log.info("Unreachable Node server address {} become reachable", member.address.toString)
      nodes += (member.address -> unreachable(member.address))
      // sync to query
      nodeStateQuery ! NodeJoined(member.address, unreachable(member.address))
      unreachable -= member.address

    case MemberExited(member) if member.hasRole(ClusterRole.pipeline.toString) =>
      log.info("Node server {} is exiting", member.address)
      nodes -= member.address
      // sync to query
      nodeStateQuery ! NodeRemoved(member.address)

    case MemberRemoved(member, pre) if member.hasRole(ClusterRole.pipeline.toString) =>
      log.info("Node server {} is removed from pre status {}, restart running jobs", member.address.toString, pre)
      // Resend running jobs, run in other reachable Node server
      if (unreachable.contains(member.address)) {
        unreachable(member.address).tasks.foreach(p => {
          log.info("Restarting job {}", p._1)
          self ! p._2.getTask
        })
        unreachable -= member.address
      }
      nodes -= member.address
      // sync to query
      nodeStateQuery ! NodeRemoved(member.address)

    case GetPipeServers =>
      sender() ! nodes.keySet.toSeq

    case NodeUnregister(_) =>
      log.info("Receive unregister message from {}", sender().path.toSerializationFormat)
      if (unreachable.contains(sender().path.address)) {
        unreachable(sender().path.address).tasks.foreach(p => {
          log.info("Restarting job {}", p._1)
          self ! p._2.getTask
        })
        unreachable -= sender().path.address
      } else if (nodes.contains(sender().path.address)) {
        val restartPipelines = nodes(sender().path.address).tasks
        nodes -= sender().path.address
        // sync to query
        nodeStateQuery ! NodeRemoved(sender().path.address)
        restartPipelines.foreach {
          case (_, state) =>
            log.info(s"Restarting pipe [${state.id}] from Node server [${sender().path.address}]")
            self ! state.task.get
        }
      }
    case Terminated(actor) =>
      log.info("Remote pipeline manager actor {} terminated.", actor.path.toSerializationFormat)
      cluster.leave(actor.path.address)

  }
}

object PipelineRegistry {

  case class NodeState(
                            node: ActorRef,
                            tasks: collection.mutable.Map[String, TaskState],
                            total: NodeServerResource,
                            free: NodeServerResource,
                            load: Double = 0d
                          )

  case class PipelineSubmitResponse(id: String, pipeStatus: TaskStatus, response: String)

  case object GetPipeServers

}