package graalvm.demo.node

import akka.Done
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.event.Logging
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import graalvm.demo.common.ClusterMessage
import graalvm.demo.node.actors.{MetricsListener, NodeLeader}
import scaldi.Injectable.inject
import scaldi.Module
import scaldi.akka.AkkaInjectable

import scala.concurrent.{Await, ExecutionContext, Future, Promise, blocking}
import scala.concurrent.duration.Duration
import scala.io.StdIn

object NodeServer {

  // bin/node -agentlib:native-image-agent=config-output-dir=/Users/deanzhang/work/code/github/graalvm-demo/node/src/main/resources/
  def main(args: Array[String]): Unit ={
    implicit val injector: Module = new NodeModule()
    implicit val system = inject[ActorSystem]
    val management = inject[AkkaManagement]
    management.start()
    inject[ClusterBootstrap].start()
    val log = Logging(system, this.getClass)
    val cluster = Cluster(system)
    import system.dispatcher
    cluster.registerOnMemberUp({
      log.info("Pipeline discovery is ready to work.")
      val nodeLeader = AkkaInjectable.injectActorRef[NodeLeader]("node-leader")
      val metricsListener = AkkaInjectable.injectActorRef[MetricsListener]("metrics-listener")
      log.info("Pipeline manager is started")
      Await.ready(
        waitForShutdownSignal(system), // chaining both futures to fail fast
        Duration.Inf
      ) // It's waiting forever because maybe there is never a shutdown signal
      log.info("Pipeline discovery is ready to stop.")
      nodeLeader ! ClusterMessage.Stop
      management.stop()
    })
    cluster.registerOnMemberRemoved({
      log.info("Pipeline discovery is stopped after removed from cluster.")
      // !!! DO NOT explicit do system.terminate anywhere, wait for coordinated shutdown do this gracefully
      //            system.terminate()
    })

    system.registerOnTermination {
      log.info("Pipeline discovery is stopped and jvm exit.")
    }
  }

  protected def waitForShutdownSignal(system: ActorSystem)(implicit ec: ExecutionContext): Future[Done] = {
    val promise = Promise[Done]()
    sys.addShutdownHook {
      promise.trySuccess(Done)
    }
    Future {
      blocking {
        if (StdIn.readLine("Press RETURN to stop...\n") != null)
          promise.trySuccess(Done)
      }
    }
    promise.future
  }
}
