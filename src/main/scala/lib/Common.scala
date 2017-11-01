package object lib {

  import org.slf4j.Logger
  import org.slf4j.LoggerFactory
  import org.slf4j.impl.SimpleLogger
  import scala.util._

  def using[A, B](resource: A)(cleanup: A => Unit)(doWork: A => B)(implicit logger: Logger): Try[B] = {
    try {
      Success(doWork(resource))
    } catch {
      case e: Exception => Failure(e)
    } finally {
      try {
        if (resource != null) {
          cleanup(resource)
        }
      } catch {
        case e: Exception => logger.error("Cleanup failed", e)
      }
    }
  }

  def getLogger(conf: LoggerConfig): Logger = {
    if (conf.debug) {
      System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
    }
    LoggerFactory.getLogger(conf.name)
  }
}
