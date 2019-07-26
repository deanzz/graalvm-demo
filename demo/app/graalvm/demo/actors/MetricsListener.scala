package graalvm.demo.actors

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.metrics.StandardMetrics.{Cpu, HeapMemory}
import akka.cluster.metrics.{ClusterMetricsChanged, ClusterMetricsExtension, NodeMetrics}
import akka.routing.{Broadcast, FromConfig}
import graalvm.demo.node.NodeStateChanged


class MetricsListener extends Actor with ActorLogging {
  val selfAddress = Cluster(context.system).selfAddress
  val extension = ClusterMetricsExtension(context.system)

  // Subscribe unto ClusterMetricsEvent events.
  override def preStart(): Unit = extension.subscribe(self)

  // Unsubscribe from ClusterMetricsEvent events.
  override def postStop(): Unit = extension.unsubscribe(self)

  def receive = {
    case ClusterMetricsChanged(clusterMetrics) =>
      clusterMetrics.filter(_.address == selfAddress) foreach { nodeMetrics =>
        val (usedHeap, maxHeap) = heap(nodeMetrics)
        val (load, totalCpuNum, freeCpuNum) = cpu(nodeMetrics)
      }
  }

  private def heap(nodeMetrics: NodeMetrics) = nodeMetrics match {
    case HeapMemory(address, timestamp, used, committed, max) =>
      val usedHeap = math.round(used.floatValue() / 1024 / 1024)
      val maxHeap = math.round(max.getOrElse(0L).floatValue() / 1024 / 1024)
      log.info("Used heap: {} MB, Max heap: {} MB", usedHeap, maxHeap)
      (usedHeap, maxHeap)
    case _ => (0, 0)
  }

  private def cpu(nodeMetrics: NodeMetrics) = nodeMetrics match {
    case Cpu(address, timestamp, Some(systemLoadAverage), cpuCombined, cpuStolen, processors) =>
      val load = (systemLoadAverage * 100).round / 100d
      val freeCpuNumTmp = processors - math.round(cpuCombined.getOrElse(0d) * processors).toInt
      val freeCpuNum = if (freeCpuNumTmp < 0) 0 else freeCpuNumTmp
      log.info(s"Average Load: {} ({} processors), Cpu usage: {}%", load, processors, freeCpuNum)
      (load, processors, freeCpuNum)
    case _ => (0d, 0, 0)
  }

}