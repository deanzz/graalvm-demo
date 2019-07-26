package graalvm.demo.modules

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.singleton
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.google.inject.{AbstractModule, Provides}
import graalvm.demo.actors._
import graalvm.demo.common.ClusterMessage.End
import graalvm.demo.common.ClusterRole
import javax.inject.{Named, Singleton}
import play.api.inject.Injector
import play.api.libs.concurrent.AkkaGuiceSupport

class DemoModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindActor[PingMan]("ping-man")
    bindActor[PongMan]("pong-man")
    bindActor[ClusterMonitor]("cluster-monitor")
    bindActor[NodeStateQuery]("node-state-query")
    bindActor[MetricsListener]("metrics-listener")
  }

  @Provides
  @Singleton
  @Named("node-registry")
  def provideNodeRegistry(
                               system: ActorSystem,
                               injector: Injector,
                               @Named("node-state-query") pipeStateQuery: ActorRef,
                               @Named("cluster-monitor") monitor: ActorRef
                             ): ActorRef =
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props.create(classOf[NodeRegistry], injector, pipeStateQuery, monitor),
        terminationMessage = End,
        settings = singleton.ClusterSingletonManagerSettings(system).withRole(ClusterRole.manager.toString)
      ),
      "node-registry"
    )

  @Provides
  @Singleton
  @Named("node-registry-proxy")
  def provideNodeRegistryProxy(system: ActorSystem, injector: Injector): ActorRef =
    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = "/user/node-registry",
        settings = ClusterSingletonProxySettings(system).withRole(ClusterRole.manager.toString)
      ),
      "node-registry-proxy"
    )

}
