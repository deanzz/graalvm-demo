package graalvm.demo.common.utils

import graalvm.demo.filter.ConditionType

object FilterUtil {

   def filter(condition: ConditionType, values: Seq[String], fieldValue: String, isNumber: Boolean = false): Boolean = {
    condition match {
      case ConditionType.EQ => if (isNumber) values.head.toDouble == fieldValue.toDouble else values.head == fieldValue
      case ConditionType.NE => if (isNumber) values.head.toDouble != fieldValue.toDouble else values.head != fieldValue
      case ConditionType.LT => fieldValue.toDouble < values.head.toDouble
      case ConditionType.LET => fieldValue.toDouble <= values.head.toDouble
      case ConditionType.GT => fieldValue.toDouble > values.head.toDouble
      case ConditionType.GET => fieldValue.toDouble >= values.head.toDouble
      case ConditionType.BT =>
        val v = fieldValue.toDouble
        val min = values.head.toDouble
        val max = values.last.toDouble
        v >= min && v <= max
      case ConditionType.BT_NE_NE =>
        val v = fieldValue.toDouble
        val min = values.head.toDouble
        val max = values.last.toDouble
        v > min && v < max
      case ConditionType.BT_EQ_NE =>
        val v = fieldValue.toDouble
        val min = values.head.toDouble
        val max = values.last.toDouble
        v >= min && v < max
      case ConditionType.BT_NE_EQ =>
        val v = fieldValue.toDouble
        val min = values.head.toDouble
        val max = values.last.toDouble
        v > min && v <= max
      case ConditionType.CON =>
        values.map{
          key =>
            fieldValue.contains(key)
        }.reduce(_ && _)
      case ConditionType.NCON =>
        values.map{
          key =>
            !fieldValue.contains(key)
        }.reduce(_ && _)
      case ConditionType.REG =>
        val regex = values.head
        fieldValue.matches(regex)
      case ConditionType.IN =>
        if (isNumber) {
          values.map(_.toDouble).contains(fieldValue.toDouble)
        } else {
          values.contains(fieldValue)
        }

      case _ => true
    }
  }
}
