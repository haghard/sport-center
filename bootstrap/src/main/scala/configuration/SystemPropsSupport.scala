package configuration

import scala.collection._

trait SystemPropsSupport {

  private val opt = """-D(\S+)=(\S+)""".r

  implicit def funcToRunnable(f: () ⇒ Unit) = new Runnable {
    override def run() = f()
  }

  def applySystemProperties(args: Array[String]): Unit = {
    def argsToProps(args: Array[String]) =
      args.collect { case opt(key, value) ⇒ key -> value }(breakOut)
    for ((key, value) ← argsToProps(args)) {
      println(s"SystemProperties: $key - $value")
      System.setProperty(key, value)
    }
  }
}
