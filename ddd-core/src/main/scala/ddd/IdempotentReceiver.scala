package ddd

trait IdempotentReceiver {
  def nonDuplicate(p: => Boolean)(action: => Unit)(compensation: => Unit) =
    if (p) action else compensation
}
