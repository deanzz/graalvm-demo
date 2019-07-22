package graalvm.demo.controllers

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.google.protobuf.any.Any
import graalvm.demo.actors.PingMan.Ping
import graalvm.demo.actors.PipelineRegistry.PipelineSubmitResponse
import graalvm.demo.actors.PipelineStateForQuery.GetTaskResult
import graalvm.demo.actors.PongMan.Pong
import graalvm.demo.filter.{Condition, ConditionType, FieldFilterParam, FilterParam}
import graalvm.demo.node.{ConnectionType, DataFormat, MongoConfig, MongoSourceParam, Schema, SourceInfo, TaskInfo, TransformerInfo, TransformerType}
import javax.inject.{Inject, Named}
import play.api.Configuration
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DemoController @Inject()(configuration: Configuration,
                               @Named("ping-man") pingMan: ActorRef,
                               @Named("node-state-query") nodeStateQuery: ActorRef,
                               @Named("node-registry-proxy") registry: ActorRef)
                              (implicit cc: ControllerComponents) extends AbstractController(cc) {
  implicit val ec = cc.executionContext
  implicit val timeout = Timeout(10.seconds)
  val mongoUri = configuration.get[String]("mongo.uri")
  val mongoDb = configuration.get[String]("mongo.db")
  val mongoTable = configuration.get[String]("mongo.table.name")
  val mongoTableTitles = configuration.get[Seq[String]]("mongo.table.schemaNames")
  val mongoTableTypes = configuration.get[Seq[String]]("mongo.table.schemaTypes")
  val filterField = configuration.get[String]("mongo.table.filterField")

  def index = Action.async {
    implicit req =>
      Future(
        Ok(s"hello graalvm")
      )
  }

  def ping(n: Long) = Action.async {
    implicit req =>
      val start = System.nanoTime()
      (pingMan ? Ping(n)).map {
        res =>
          val pong = res.asInstanceOf[Pong]
          val end = System.nanoTime()
          val seq = s"used ${end - start}ns\n${pong.s}"
          Ok(seq)
      }
  }

  def filter(keyword: String) = Action.async {
    implicit req =>
      val taskId = System.nanoTime().toString
      val connConfig = MongoConfig(
        uri = Seq(mongoUri),
        db = mongoDb,
        table = mongoTable,
        schema = Some(
          Schema(
            name = mongoTableTitles,
            `type` = mongoTableTypes
          )
        )
      )


      val source = SourceInfo(s"$taskId-source", ConnectionType.MONGO, Some(Any.pack(connConfig)), DataFormat.AVRO, Some( Any.pack(MongoSourceParam(filter = "{}", skip = 0))))
      val filterParam = FilterParam(Seq(
        FieldFilterParam(filterField, Seq(
          Condition(
            ConditionType.CON,
            Seq(keyword)
          )
        ))
      ))
      val transformers = Seq(
        TransformerInfo(TransformerType.STATELESS_FILTER, Some(Any.pack(filterParam)))
      )
      val taskInfo = TaskInfo(taskId, Some(source), transformers)
      (registry ? taskInfo).map{
        case r: PipelineSubmitResponse =>
          Ok(s"Task ${r.id} is ${r.pipeStatus.name}, $connConfig")
      }
  }

  def status = Action.async {
    implicit req =>
      Future(Ok)
  }

  def taskResult(id: String) = Action.async {
    implicit req =>
      (nodeStateQuery ? GetTaskResult(id)).map{
        res =>
          val seq = res.asInstanceOf[Seq[String]].map(JsString)
          Ok(Json.arr(seq))
      }
  }

  def mongoInsert = Action.async {
    implicit req =>
      Future(Ok)
  }

  def mongoUpdate(id: String) = Action.async {
    implicit req =>
      Future(Ok)
  }

  def mongoDelete(id: String) = Action.async {
    implicit req =>
      Future(Ok)
  }

  def redisInsert = Action.async {
    implicit req =>
      Future(Ok)
  }

  def redisUpdate(id: String) = Action.async {
    implicit req =>
      Future(Ok)
  }

  def redisDelete(id: String) = Action.async {
    implicit req =>
      Future(Ok)
  }

}
