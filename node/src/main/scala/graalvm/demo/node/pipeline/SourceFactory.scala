package graalvm.demo.node.pipeline

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source}
import graalvm.demo.common.UserDefinedSource
import graalvm.demo.node.sources.mongo.MongoQuerySource
import graalvm.demo.node.{ConnectionType, MongoConfig, MongoSourceParam, SourceInfo}
import org.apache.avro.generic.IndexedRecord
import scaldi.Injector

object SourceFactory {
  def create(id: String, leader: ActorRef, source: SourceInfo)(
      implicit mat: ActorMaterializer,
      system: ActorSystem,
      injector: Injector
  ): (Source[IndexedRecord, NotUsed], UserDefinedSource[_, IndexedRecord, NotUsed], Option[org.apache.avro.Schema]) = {
    source.connType match {
      case ConnectionType.MONGO =>
        val wrapper = new MongoQuerySource(source.getConnConfig.unpack[MongoConfig])
        (wrapper.create(source.getParam.unpack[MongoSourceParam]), wrapper, Option(wrapper.schema))
      /*case ConnectionType.KAFKA =>
        val connConfig = source.getConnConfig.unpack[KafkaConfig]
        val consumerParam = source.getParam.unpack[KafkaConsumerParam]
        val wrapper = KafkaSourceFactory(connConfig.consumerType, connConfig)
        (wrapper.create(consumerParam), wrapper, Option(wrapper.getSchema))*/
    }
  }
}
