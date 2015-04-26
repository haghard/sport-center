package microservice.http

import akka.http.scaladsl.server.{ Directives, Route }
import scala.concurrent.ExecutionContext

case class RestApiJunction(
    route: Option[ExecutionContext => Route] = None,
    preAction: Option[() => Unit] = None,
    postAction: Option[() => Unit] = None) extends Directives {

  private def cmbRoutes(r0: ExecutionContext => Route, r1: ExecutionContext => Route) =
    (ec: ExecutionContext) =>
      r0(ec) ~ r1(ec)

  private def cmbActions(a1: () => Unit, a2: () => Unit) =
    () =>
      { a1(); a2() }

  def and(that: RestApiJunction): RestApiJunction =
    RestApiJunction(route ++ that.route reduceOption cmbRoutes,
      preAction ++ that.preAction reduceOption cmbActions,
      postAction ++ that.postAction reduceOption cmbActions)

  /**
   * Alias for ``and`` operation
   */
  def ~(that: RestApiJunction): RestApiJunction = and(that)
}