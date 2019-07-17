package graalvm.demo

object Main {

  // sbt 'show graalvm-native-image:packageBin'
  def main(args: Array[String]): Unit ={
    val lib = new DeanLib
    println(s"hello graalvm, ${lib.getName}")
  }
}
