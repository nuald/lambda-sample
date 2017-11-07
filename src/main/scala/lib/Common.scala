package object lib {

  import akka.event.LoggingAdapter
  import scala.util._

  def using[A, B](resource: A)(cleanup: A => Unit)(doWork: A => B)(implicit log: LoggingAdapter): Try[B] = {
    try {
      Success(doWork(resource))
    } catch {
      case e: Exception => Failure(e)
    }
    finally {
      try {
        if (resource != null) {
          cleanup(resource)
        }
      } catch {
        case e: Exception => log.error(e, "Cleanup failed")
      }
    }
  }
}
