package graalvm.demo.actors

import akka.actor.{Actor, ActorLogging, Address, RootActorPath}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import graalvm.demo.common.ClusterRole
import graalvm.demo.node.GetNodeServerStates
import javax.inject.Inject

/**
 * Description: Construct argument is used in Prod mode for registry early initialize
 *
 * @author wanglei
 * @since 2019-03-12 17:26
 * @version 1.0.0
 */
class ClusterMonitor @Inject()()
    extends Actor
    with ActorLogging {

  AkkaManagement.get(context.system).start()
  ClusterBootstrap(context.system).start()
  val cluster = Cluster(context.system)
  private var nodes = Set.empty[Address]

  override def preStart(): Unit = {
    super.preStart()
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember], classOf[ReachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    cluster.leave(cluster.selfAddress)
  }

  override def receive: Receive = {
    case MemberUp(member) => {
      if (member.roles.contains(ClusterRole.pipeline.toString)) {
        nodes += member.address
//        context.actorSelection(RootActorPath(member.address) / "user" / "pipeline-manager") ! GetPipeServerStates(true)
        log.info("New pipeline server is up at {}", member.address)
      }
      log.info(s"Cluster member up: ${member.address} with role ${member.roles}")
    }
    case UnreachableMember(member) =>
      log.warning(s"Cluster member unreachable: ${member.address}")
      if (member.roles.contains(ClusterRole.pipeline.toString)) {
        nodes -= member.address
      }
    case ReachableMember(member) =>
      log.info(s"Cluster member became reachable: ${member.address}")
      if (member.roles.contains(ClusterRole.pipeline.toString)) {
        nodes += member.address
      }
    case MemberRemoved(member, previousStatus) => {
      nodes -= member.address
      log.info(s"Cluster member removed: ${member.address} with previous status $previousStatus")
    }
    case MemberExited(member) => log.info(s"Cluster member exited: ${member.address}")
    case e: MemberEvent =>
      log.info("Event: {}", e)
    case PipelineRegistry.GetPipeServers =>
      log.info("Ready to broad GetPipeServers message to pipeline server")
      nodes.foreach(address => {
        log.info("Send GetPipeServerStates message to address {}", address.toString)
        context.actorSelection(RootActorPath(address) / "user" / "node-leader") ! GetNodeServerStates(true)
      })
  }
}
