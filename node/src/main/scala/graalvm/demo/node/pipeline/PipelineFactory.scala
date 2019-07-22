package graalvm.demo.node.pipeline

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.stream.scaladsl.{Broadcast, GraphDSL, Keep, Merge, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, SinkShape}
import com.google.protobuf.any.Any
import com.typesafe.config.ConfigFactory
import graalvm.demo.common.UserDefinedSource
import graalvm.demo.node.pipeline.flow.FinalStateOnComplete
import graalvm.demo.node.{ConnectionType, DataFormat, TaskInfo}
import org.apache.avro.generic.IndexedRecord
import scaldi.Injector

import scala.concurrent.Future

object PipelineFactory {

  def create(info: TaskInfo, leader: ActorRef, processStep: Long = 1000L)(
    implicit mat: ActorMaterializer,
    system: ActorSystem,
    injector: Injector
  ): (RunnableGraph[Future[Done]], Map[String, UserDefinedSource[_, _, _]]) = {
    implicit val ec = system.dispatcher
    val log = Logging(system, this.getClass)
    val controllers = collection.mutable.Map.empty[String, UserDefinedSource[_, _, _]]
    var finalSchema: Option[org.apache.avro.Schema] = None
    val source = {
      val (shape, wrapper, schema) = SourceFactory.create(info.id, leader, info.source.get)
      finalSchema = schema
      controllers += (info.source.get.id -> wrapper)
      shape
    }

    val srcWithTrans = info.transformers.foldLeft((source, finalSchema))((s, t) => {
      val (flow, outSchema) = TransformerFactory.create(info.id, leader, t, s._2)
     (s._1.viaMat(flow)(Keep.right), outSchema)
    })

    val statisticSink = FinalStateOnComplete.createSink(info.id, processStep, leader)
    (srcWithTrans._1.toMat(statisticSink)(Keep.right), controllers.toMap)
  }
}
