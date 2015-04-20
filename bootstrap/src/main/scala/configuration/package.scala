package object configuration {

  val AKKA_PORT_VAR = "AKKA_PORT"
  val HTTP_PORT_VAR = "HTTP_PORT"

  object Ports {
    object cloud {
      implicit val defaultPort = "2551"
    }
  }
}
