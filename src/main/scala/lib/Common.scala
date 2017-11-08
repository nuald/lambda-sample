package lib

import akka.event.LoggingAdapter

import scala.util._

object Common {
  def using[A, B](resource: A)(cleanup: A => Unit)(doWork: A => B)(implicit logger: LoggingAdapter): Try[B] = {
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
        case e: Exception => logger.error(e, "Cleanup failed")
      }
    }
  }
}
