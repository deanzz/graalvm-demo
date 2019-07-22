package graalvm.demo.common.utils

import org.apache.avro.generic.IndexedRecord

import scala.util.Try

object RecordUtil {

  def getFieldPos(record: IndexedRecord, field: String): Int = record.getSchema.getField(field).pos()

  def getStringFieldValue(record: IndexedRecord, field: String): Option[String] = {
    val obj = getValue(record, field)
    if (obj == null) None else Some(obj.toString)
  }

  def getDoubleFieldValue(record: IndexedRecord, field: String): Option[Double] = getValue(record, field) match {
    case v: Integer => Some(v.toDouble)
    case v: java.lang.Long => Some(v.toDouble)
    case v: java.lang.Float => Some(v.toDouble)
    case v: java.lang.Double => Some(v.toDouble)
    case v: java.lang.String => Try(Some(v.toDouble)).getOrElse(None)
    case _ => None
  }

  def getLongFieldValue(record: IndexedRecord, field: String): Option[Long] = getValue(record, field) match {
    case v: Integer => Some(v.toLong)
    case v: java.lang.Long => Some(v.toLong)
    case v: java.lang.Float => Some(v.toLong)
    case v: java.lang.Double => Some(v.toLong)
    case v: java.lang.String => Try(Some(v.toLong)).getOrElse(None)
    case _ => None
  }

  def getIntFieldValue(record: IndexedRecord, field: String): Option[Int] = getValue(record, field) match {
    case v: Integer => Some(v.toInt)
    case v: java.lang.Long => Some(v.toInt)
    case v: java.lang.Float => Some(v.toInt)
    case v: java.lang.Double => Some(v.toInt)
    case v: java.lang.String => Try(Some(v.toInt)).getOrElse(None)
    case _ => None
  }

  def getFloatFieldValue(record: IndexedRecord, field: String): Option[Float] = getValue(record, field) match {
    case v: Integer => Some(v.toFloat)
    case v: java.lang.Long => Some(v.toFloat)
    case v: java.lang.Float => Some(v.toFloat)
    case v: java.lang.Double => Some(v.toFloat)
    case v: java.lang.String => Try(Some(v.toFloat)).getOrElse(None)
    case _ => None
  }

  def getBooleanFieldValue(record: IndexedRecord, field: String): Option[Boolean] = getValue(record, field) match {
    case v: Integer => v.toInt match {
      case 1 => Some(true)
      case 0 => Some(false)
      case _  => None
    }
    case v: java.lang.Boolean => Some(v)
    case v: java.lang.Float => v.toInt match {
      case 1 => Some(true)
      case 0 => Some(false)
      case _  => None
    }
    case v: java.lang.Double => v.toInt match {
      case 1 => Some(true)
      case 0 => Some(false)
      case _  => None
    }
    case v: java.lang.String => Try(v.toInt match {
      case 1 => Some(true)
      case 0 => Some(false)
      case _  => None
    }).getOrElse(None)
    case _ => None
  }

  def getValue(record: IndexedRecord, field: String): AnyRef = record.get(getFieldPos(record, field))
}
