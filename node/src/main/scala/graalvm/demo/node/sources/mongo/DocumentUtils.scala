package graalvm.demo.node.sources.mongo

import java.util.Date

import org.apache.avro.Schema
import org.apache.avro.Schema.Type._
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.util.parsing.json.JSONArray

object DocumentUtils {
  def getDataType(value: Any): Schema.Type =
    value match {
      case _: java.lang.String => STRING
      case _: java.lang.Integer => INT
      case _: java.lang.Long => LONG
      case _: java.lang.Double => DOUBLE
      case _: java.lang.Boolean => BOOLEAN
      case _: java.util.List[_] => STRING
      case _: Date => LONG
      case _: ObjectId => STRING
      case _: Document => STRING
      case _ => NULL
    }

  def getCombineType(t: Schema.Type, existType: Option[Schema.Type]): Schema.Type =
    if (!existType.isDefined || existType.get == NULL) {
      t
    } else if (existType.get == STRING) {
      STRING
    } else if (t == STRING) {
      STRING
    } else {
      existType match {
        case Some(INT) =>
          t match {
            case INT => INT
            case LONG => LONG
            case DOUBLE => DOUBLE
            case _ => STRING
          }
        case Some(LONG) =>
          t match {
            case INT => LONG
            case LONG => LONG
            case DOUBLE => DOUBLE
            case _ => STRING
          }
        case Some(DOUBLE) =>
          t match {
            case INT => DOUBLE
            case LONG => DOUBLE
            case DOUBLE => DOUBLE
            case _ => STRING
          }
        case Some(BOOLEAN) =>
          t match {
            case BOOLEAN => BOOLEAN
            case _ => STRING
          }
      }
    }

  @throws[ClassCastException]
  def dealStringValue(value: java.lang.String, expectType: Schema.Type) =
    try {
      expectType match {
        case Schema.Type.BOOLEAN => java.lang.Boolean.valueOf(value)
        case Schema.Type.INT => value.toInt
        case Schema.Type.LONG => value.toLong
        case Schema.Type.FLOAT => value.toFloat
        case Schema.Type.DOUBLE => value.toDouble
        case Schema.Type.STRING => value
        case _ => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
      }
    } catch {
      case _: Throwable => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
    }

  @throws[ClassCastException]
  def dealIntegerValue(value: java.lang.Integer, expectType: Schema.Type) =
    try {
      expectType match {
        case Schema.Type.BOOLEAN => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
        case Schema.Type.INT => value
        case Schema.Type.LONG => value.toLong
        case Schema.Type.FLOAT => value.toFloat
        case Schema.Type.DOUBLE => value.toDouble
        case Schema.Type.STRING => value.toString
        case _ => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
      }
    } catch {
      case _: Throwable => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
    }

  @throws[ClassCastException]
  def dealLongValue(value: java.lang.Long, expectType: Schema.Type) =
    try {
      expectType match {
        case Schema.Type.BOOLEAN => java.lang.Boolean.valueOf(value.toString)
        case Schema.Type.INT => value
        case Schema.Type.LONG => value.toLong
        case Schema.Type.FLOAT => value.toFloat
        case Schema.Type.DOUBLE => value.toDouble
        case Schema.Type.STRING => value.toString
        case _ => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
      }
    } catch {
      case _: Throwable => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
    }

  @throws[ClassCastException]
  def dealDoubleValue(value: java.lang.Double, expectType: Schema.Type) =
    try {
      expectType match {
        case Schema.Type.BOOLEAN => java.lang.Boolean.valueOf(value.toString)
        case Schema.Type.INT => value.toInt
        case Schema.Type.LONG => value.toLong
        case Schema.Type.FLOAT => value.toFloat
        case Schema.Type.DOUBLE => value
        case Schema.Type.STRING => value.toString
      }
    } catch {
      case _: Throwable => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
    }

  @throws[ClassCastException]
  def dealBooleanValue(value: java.lang.Boolean, expectType: Schema.Type) =
    try {
      expectType match {
        case Schema.Type.BOOLEAN => value
        case Schema.Type.STRING => value.toString
        case _ => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
      }
    } catch {
      case _: Throwable => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
    }

  def dealListValue(value: java.util.List[_], expectType: Schema.Type) =
    try {
      expectType match {
        case Schema.Type.STRING => JSONArray.apply(value.asScala.toList).toString()
        case _ => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
      }
    } catch {
      case _: Throwable => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
    }

  def dealDateValue(value: java.util.Date, expectType: Schema.Type) =
    try {
      expectType match {
        case Schema.Type.LONG => value.getTime
        case Schema.Type.FLOAT => value.getTime.toFloat
        case Schema.Type.DOUBLE => value.getTime.toDouble
        case Schema.Type.STRING => value.getTime.toString
        case _ => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
      }
    } catch {
      case _: Throwable => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
    }

  def dealObjectIdValue(value: org.bson.types.ObjectId, expectType: Schema.Type) =
    try {
      expectType match {
        case Schema.Type.STRING => value.toString
        case _ => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
      }
    } catch {
      case _: Throwable => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
    }

  def dealDocumentValue(value: Document, expectType: Schema.Type) =
    try {
      expectType match {
        case Schema.Type.STRING => value.toString
        case _ => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
      }
    } catch {
      case _: Throwable => throw new ClassCastException(s"cannot cast value: $value to type: $expectType.")
    }
}
