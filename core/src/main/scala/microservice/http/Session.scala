package microservice.http

import com.softwaremill.session.{ SessionSerializer, SingleValueSessionSerializer }

import scala.util.Try

//import com.softwaremill.session.{ SessionSerializer, ToMapSessionSerializer }

case class Session(info: String)

object Session {

  implicit def serializer: SessionSerializer[Session, String] =
    new SingleValueSessionSerializer({ session: Session ⇒ session.info }, { v: (String) ⇒ Try(Session(v)) })
}

/*
object Session {
  implicit val serializer: SessionSerializer[Session] = new ToMapSessionSerializer[Session] {
    override def serializeToMap(t: Session) = Map("id" -> t.info)
    override def deserializeFromMap(m: Map[String, String]) = Session(m("id"))
  }
}*/

