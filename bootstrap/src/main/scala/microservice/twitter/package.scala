package microservice

package object twitter {

  case object GetBrokerAddresses

  case class User(id: String = "", name: String = "", screenName: String = "", lang: String = "")

  case class Tweet(id: String = "", createdAt: String = "", user: Option[User] = None, text: String = "", topic: Option[String] = None)

}
