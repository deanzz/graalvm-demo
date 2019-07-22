package graalvm.demo.node.sources.mongo

import java.util.Date

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.KillSwitches
import akka.stream.UniqueKillSwitch
import akka.stream.alpakka.mongodb.scaladsl.MongoSource
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.mongodb.ConnectionString
import com.mongodb.reactivestreams.client.MongoClients
import graalvm.demo.common.UserDefinedSource
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic.IndexedRecord
import org.bson.Document
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import scaldi.Injectable
import scaldi.Injector

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import DocumentUtils._
import graalvm.demo.common.utils.SchemaUtils
import graalvm.demo.node.{MongoConfig, MongoSourceParam}


class MongoQuerySource(config: MongoConfig)(implicit injector: Injector, system: ActorSystem, mat: ActorMaterializer)
    extends UserDefinedSource[MongoSourceParam, IndexedRecord, NotUsed]
    with Injectable {
  val log = LoggerFactory.getLogger(this.getClass)
  //mongodb://kmtongji:9Nmg4rfBF55fgNGfbGBe3UFgf24Zx3@z008.kmtongji.com:27017/admin?connectTimeoutMS=30000&wTimeoutMS=360000
  implicit val connectionString = new ConnectionString(config.uri.head)
  implicit val client = MongoClients.create(connectionString)
  implicit val db = client.getDatabase(config.db)
  // system.registerOnTermination(() => client.close())

  implicit val table = config.table
  val schema = config.schema match {
    case Some(definedSchema) if(definedSchema.name.nonEmpty) =>
      log.info(s"definedSchema: $definedSchema")
      val s = SchemaUtils.apply(definedSchema.name, definedSchema.`type`, table)
      log.info(s"schema: $s")
      s
    case _ =>
      val s = inferSchema
      log.info(s"inferSchema: $s")
      s
  }

  val batchSize = 1000

  var controller: UniqueKillSwitch = _

  def query(filterJson: Option[String], skip: Int = 0, limit: Option[Int] = None) = {
    implicit val findResult = if (limit.isDefined) {
      db.getCollection(table).find(Document.parse(filterJson.getOrElse("{}"))).skip(skip).limit(limit.get).batchSize(batchSize)
    } else {
      db.getCollection(table).find(Document.parse(filterJson.getOrElse("{}"))).skip(skip).batchSize(batchSize)
    }

    //build source
    implicit val recordSrc = MongoSource(findResult).map { doc =>
      val record = new Record(schema)
      log.info(s"mongo schema: $schema")
      log.info(s"mongo doc: $doc")
      schema.getFields.foreach { f =>
        val fieldName = f.name
        if (doc.containsKey(fieldName) && null != doc.get(fieldName)) {
          val expectType = f.schema.getTypes.get(1).getType
          try {
            val expectValue = doc.get(fieldName) match {
              case v: java.lang.String => dealStringValue(v, expectType)
              case v: java.lang.Integer => dealIntegerValue(v, expectType)
              case v: java.lang.Long => dealLongValue(v, expectType)
              case v: java.lang.Double => dealDoubleValue(v, expectType)
              case v: java.lang.Boolean => dealBooleanValue(v, expectType)
              case v: java.util.List[_] => dealListValue(v, expectType)
              case v: Date => dealDateValue(v, expectType)
              case v: ObjectId => dealObjectIdValue(v, expectType)
              case v: Document => dealDocumentValue(v, expectType)
              case _ =>
            }
            record.put(fieldName, expectValue)
          } catch {
            case t: Throwable => log.debug("get value error!", t)
          }
        }
      }
      log.info(s"Mongo got record $record")
      record
    }
    recordSrc.log("mongo").viaMat(KillSwitches.single)(Keep.right)
  }

  def inferSchema: Schema = {
    val findResult = db.getCollection(table).find().limit(10).batchSize(batchSize)
    val schemaMap = scala.collection.mutable.LinkedHashMap.empty[String, Schema.Type]
    val recordSrc = MongoSource(findResult)
    recordSrc.runWith(Sink.ignore)

    val sink = Sink.fold(schemaMap)((map, doc: Document) => {
      val keys = doc.keySet().asScala
      keys.foreach(name => {
        map += name -> getCombineType(getDataType(doc.get(name)), map.get(name))
      })
      map
    })

    implicit val ec: ExecutionContext = system.dispatcher
    val resultFuture = recordSrc.runWith(sink)

    val result = Await.result(resultFuture, Duration("10s"))
    SchemaUtils.apply(result, table)
  }

  /**
   * Remember to close the connection or session to release resource
   */
  override def close(): Unit = {
    controller.shutdown()
    client.close
  }

  override def create(param: MongoSourceParam): Source[IndexedRecord, NotUsed] = {
    val (kill, source) = query(Some(param.filter), param.skip).preMaterialize()
    this.controller = kill
    source
  }
}
