package configuration

import scala.collection._

trait SystemPropsSupport {

  private val opt = """-D(\S+)=(\S+)""".r

  implicit def funcToRunnable(f: () ⇒ Unit) = new Runnable {
    override def run() = f()
  }

  def argsToProps(args: Array[String]) =
    args.collect { case opt(key, value) ⇒ key -> value }(breakOut)

  def applySystemProperties(args: Array[String]) = {
    for ((key, value) ← argsToProps(args)) {
      println(s"SystemProperties: $key - $value")
      System.setProperty(key, value)
    }
  }
}
