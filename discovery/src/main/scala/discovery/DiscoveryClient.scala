package discovery

import scala.concurrent.Future
import akka.http.scaladsl.model.StatusCode

trait DiscoveryClient {

  /**
   *
   * @param k
   * @param v
   * @return
   */
  def set(k: String, v: String): Future[StatusCode]

  /**
   *
   * @param k
   * @param v
   * @return
   */
  def delete(k: String, v: String): Future[StatusCode]
}
