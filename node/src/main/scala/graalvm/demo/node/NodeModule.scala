package graalvm.demo.node

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.typesafe.config.{Config, ConfigFactory}
import graalvm.demo.common.ClusterRole
import graalvm.demo.node.actors.{MetricsListener, NodeLeader, NodeWorker}
import scaldi.Module

class NodeModule extends Module {
  val config = ConfigFactory.load()
  bind[Config] to config
  bind[ActorSystem] to ActorSystem("Demo-Cluster", inject[Config])
  bind[AkkaManagement] to AkkaManagement.get(inject[ActorSystem]) destroyWith (_.stop())
  bind[ClusterBootstrap] to ClusterBootstrap(inject[ActorSystem])

  binding toProvider new NodeLeader
  binding toProvider new NodeWorker
  binding toProvider new MetricsListener

  bind[ActorRef] identifiedBy 'NodeRegistryProxy to {
    implicit val actorSystem: ActorSystem = inject[ActorSystem]
    actorSystem.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = "/user/node-registry",
        settings = ClusterSingletonProxySettings(actorSystem).withRole(ClusterRole.manager.toString)
      ),
      "node-registry-proxy"
    )
  }
}