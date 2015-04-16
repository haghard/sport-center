package object configuration {

  val AKKA_PORT = "AKKA_PORT"
  val HTTP_PORT = "HTTP_PORT"

  object Ports {
    object cloud {
      implicit val defaultPort = "2551"
    }
  }
}
