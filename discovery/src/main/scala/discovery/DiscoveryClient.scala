package discovery

import scala.concurrent.Future
import akka.http.model.StatusCode

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
   * @return
   */
  def delete(k: String): Future[StatusCode]

  /**
   *
   * @param k
   * @param v
   * @return
   */
  def delete(k: String, v: String): Future[StatusCode]
}
