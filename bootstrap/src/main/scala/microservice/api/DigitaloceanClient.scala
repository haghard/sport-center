package microservice.api

import java.net.InetAddress

import akka.http.Http
import akka.http.model._
import akka.http.model.headers.{ Authorization, OAuth2BearerToken }
import akka.stream.{ ActorFlowMaterializerSettings, ActorFlowMaterializer, FlowMaterializer }
import akka.stream.scaladsl.{ Sink, Source }
import microservice.SystemSettings

import scala.collection.JavaConverters._
import scala.util.{ Success, Try }

trait DigitaloceanClient extends SeedNodesSupport with SystemSettings {
  self: ClusterNetworkSupport with BootableMicroservice ⇒

  import MicroserviceKernel._

  override lazy val seedAddresses = DigitaloceanClient(settings.cloudToken)(system)

  override lazy val akkaSeedNodes =
    seedAddresses.map(s ⇒ s"akka.tcp://${ActorSystemName}@${s.getHostAddress}:${akkaSystemPort}").asJava
}

object DigitaloceanClient {
  type Droplets = List[Droplet]

  val seedPrefix = "loadbalancer" //"backend"

  case class Droplet(id: BigInt, name: String, hostIP: String)

  val dropletsUrl = "https://api.digitalocean.com/v2/droplets?page=1&per_page=10"

  def apply(apiToken: String)(implicit system: akka.actor.ActorSystem): List[InetAddress] = {
    implicit val fm = ActorFlowMaterializer(ActorFlowMaterializerSettings(system))
    val req = HttpRequest(HttpMethods.GET, dropletsUrl, List(Authorization(OAuth2BearerToken(apiToken))))

    val connection = Http().outgoingConnection("api.digitalocean.com", 8080)
    Source.single(req).via(connection.flow).runWith(Sink.head).value match {
      case Some(Success(HttpResponse(StatusCodes.OK, h, entity, _))) =>
        ResponseParser(entity, seedPrefix).fold(
          { error ⇒
            system.log.error("CloudProvider error: {}", error)
            List.empty
          }, { droplets ⇒
            system.log.info("Frontend droplet's addresses: {}", droplets)
            droplets.map(d ⇒ InetAddress.getByName(d.hostIP))
          })
      case None =>
        system.log.error("CloudProvider error")
        List.empty
    }
  }

  object ResponseParser {
    import org.json4s._
    import org.json4s.native.JsonMethods._

    import scalaz.Scalaz._
    import scalaz._

    def apply(entity: HttpEntity, seedPrefix: String): String \/ Droplets = {
      Try {
        parse(entity.dataBytes.toString) match {
          case JObject(dps) ⇒
            val dps0 = dps.find(_._1 == "droplets")
            dps0.map {
              case (_, JArray(sObjects)) ⇒
                for {
                  JObject(item) ← sObjects
                  JField("id", JInt(id)) ← item
                  JField("name", JString(name)) ← item
                  JField("status", JString(status)) ← item
                  if (status == "active") && (name.startsWith(seedPrefix))
                  JField("networks", JObject(networks)) ← item
                  JField("v4", JArray(adds)) ← networks
                } yield {
                  val ip = adds.head.asInstanceOf[JObject]
                    .children.head.asInstanceOf[JString]
                  Droplet(id, name, ip.values)
                }
            } \/> "droplets section not found"
        }
      }.getOrElse(-\/("json parsing error"))
    }
  }
}