package graalvm.demo.common.utils

import org.apache.avro.{Schema, SchemaBuilder}
import org.slf4j.Logger

import scala.collection.JavaConversions._
import scala.collection.mutable

/**
 * @author yingzhouling on 2019-03-05
 */
object SchemaUtils {
  protected val log: Logger = org.slf4j.LoggerFactory.getLogger(this.getClass)
  private val supportType = Set("boolean", "int", "long", "float", "double", "string", "date", "timestamp")

  def apply(schemaName: Seq[String], schemaType: Seq[String], dbName: String): Schema = {
    log.info(s"schemaName: $schemaName, schemaType: $schemaType, dbName: $dbName")
    assert(schemaName.length == schemaType.length, s"the length of schema name must equal the length of schema type")
    schemaName
      .zip(schemaType)
      .foldLeft(SchemaBuilder.record(normalizeNameForAvro(dbName)).fields) {
        case (schemaBuilder, (nameStr, typeStr)) => {
          typeStr match {
            case "boolean" => schemaBuilder.optionalBoolean(nameStr)
            case "int" => schemaBuilder.optionalInt(nameStr)
            case "long" => schemaBuilder.optionalLong(nameStr)
            case "float" => schemaBuilder.optionalFloat(nameStr)
            case "double" | "number" => schemaBuilder.optionalDouble(nameStr)
            case "string" | "text" => schemaBuilder.optionalString(nameStr)
            case "date" | "timestamp" => schemaBuilder.optionalLong(nameStr)
            case o => schemaBuilder
          }
        }
      }
      .endRecord
  }

  def apply(schemaSeq: mutable.LinkedHashMap[String, Schema.Type], dbName: String): Schema =
    schemaSeq
      .foldLeft(SchemaBuilder.record(normalizeNameForAvro(dbName)).fields) {
        case (schemaBuilder, (n, t)) => {
          t match {
            case Schema.Type.BOOLEAN => schemaBuilder.optionalBoolean(n)
            case Schema.Type.INT => schemaBuilder.optionalInt(n)
            case Schema.Type.LONG => schemaBuilder.optionalLong(n)
            case Schema.Type.FLOAT => schemaBuilder.optionalFloat(n)
            case Schema.Type.DOUBLE => schemaBuilder.optionalDouble(n)
            case Schema.Type.STRING => schemaBuilder.optionalString(n)
            case o => schemaBuilder
          }
        }
      }
      .endRecord

  def normalizeNameForAvro(inputName: String): String = {
    var normalizedName: String = inputName.replaceAll("[^A-Za-z0-9_]", "_")
    if (Character.isDigit(normalizedName.charAt(0))) {
      normalizedName = "_" + normalizedName
    }
    normalizedName
  }

  def unpack(schema: Schema): Seq[(String, String)] =
    schema.getFields
      .sortBy(_.pos())
      .map(f => {
        val n = f.name
        val t = f.schema.getName match {
          case "union" => f.schema.getTypes.get(1).getName
          case other => other
        }
        (n, t)
      })

  def unpackToEngineMode(schema: Schema): Seq[(String, String)] =
    schema.getFields.sortBy(_.pos()).map(f => (f.name, f.schema.getTypes.get(1).getName)).map {
      case (n, t) =>
        t match {
          case "INT" | "LONG" | "FLOAT" | "DOUBLE" => (n, "DOUBLE")
          case _ => (n, t)
        }
    }

}
