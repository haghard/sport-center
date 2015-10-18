package microservice.http

import com.softwaremill.session.{ SessionSerializer, ToMapSessionSerializer }

case class Session(info: String)

object Session {
  implicit val serializer: SessionSerializer[Session] = new ToMapSessionSerializer[Session] {
    override def serializeToMap(t: Session) = Map("id" -> t.info)
    override def deserializeFromMap(m: Map[String, String]) = Session(m("id"))
  }
}