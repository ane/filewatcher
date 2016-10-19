package sentinel

import java.nio.file.{Path, Paths, StandardWatchEventKinds, WatchEvent}
import java.util.concurrent.atomic.AtomicBoolean

import org.reactivestreams.Publisher
import rx.RxReactiveStreams
import rx.lang.scala.Observable

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

/**
  * A `Watcher` is a reusable reactive filesystem watcher that listens to a [[java.nio.file.Path Path]]
  * for changes (deletion, modification, creation).
  *
  * If the specified Path is a file, it will watch that file (and only that file). If the file is deleted,
  * the watcher stops, and the subscription is terminated.
  *
  * If the resource is a directory, it will watch the whole directory - if the directory is deleted, the watcher
  * is terminated.
  *
  * The watcher is intended to be used in a reactive manner, i.e. not directly but as a, e.g.,
  * [[org.reactivestreams.Publisher]] or [[rx.Observable]]. A watcher can be instantiated like so:
  *
  * {{{
  * // create RxJava Observable
  * val obs = Watcher.fromPath("/tmp/foo", 100 millis, StandardWatchEventKinds.ENTRY_MODIFY)
  *                  .asObservable()
  *                  .foreach(p => println($"got event ${p.kind} for ${p.path}")
  * }}}
  *
  * Once instantiated, a `Watcher` is freely reusable, and will begin once the resulting Publisher or Observable
  * is subscribed to.
  *
  * @author Antoine Kalmbach
  */
trait Watcher {

  import rx.lang.scala.JavaConverters._

  /** Turn this watcher into a [[org.reactivestreams.Publisher]] of [[java.nio.file.Path Path]]s.
    *
    * @return the watcher as a publisher
    */
  def asPublisher(): Publisher[_ <: SentinelEvent]

  /** Turn this watcher into a [[rx.lang.scala.Observable]].
    *
    * @return the watcher as a RxScala observable
    */
  def asObservable(): Observable[SentinelEvent]

  /** Turn this watcher into a [[rx.Observable]]
    *
    * @return the watcher as a RxJava observable
    */
  def asJavaObservable(): rx.Observable[_ <: SentinelEvent] =
    asObservable().asJava
}

private[sentinel] class WatcherImpl(path: Path,
                                    interval: FiniteDuration,
                                    kinds: WatchEvent.Kind[_]*)
  extends Watcher {

  import rx.lang.scala.JavaConverters._

  override def asPublisher(): Publisher[_ <: SentinelEvent] =
    RxReactiveStreams.toPublisher(observable.asJava)

  override def asObservable(): Observable[SentinelEvent] = observable

  val observable: Observable[SentinelEvent] = Observable { subscriber =>
    new Thread(
      new Runnable() {
        def run() = {
          val on = new AtomicBoolean(true)
          try {
            // get our own cop
            val target = Paths.get(path.toString)
            val isFile = target.toFile.isFile
            // can't watch files... only directories
            val p = if (isFile) target.getParent else target
            val validTarget = (eventPath: Path) => {
              if (!isFile) {
                true
              } else {
                // check if the path of the file matches what we wanted
                eventPath.equals(target)
              }
            }

            val watcher = p.getFileSystem.newWatchService()
            p.register(watcher, (kinds :+ StandardWatchEventKinds.ENTRY_DELETE).toArray)

            while (on.get()) {
              if (subscriber.isUnsubscribed || !target.toFile.exists()) {
                on.set(false)
              }

              val key = watcher.poll()
              if (key ne null) {
                val evts = key.pollEvents()
                for (event <- evts) {
                  val kind = event.kind()
                  val file = event.context().asInstanceOf[Path]
                  val eventPath = p.resolve(file)
                  if (kinds.contains(kind) && validTarget(eventPath)) {
                    subscriber.onNext(SentinelEvent(kind, eventPath))
                  }
                }
                if (target.toFile.exists()) {
                  key.reset()
                }
              }

              Thread.sleep(interval.toMillis)
            }

            subscriber.onCompleted()
            watcher.close()
          } catch {
            case t: Throwable => subscriber.onError(t)
          }
        }
      }
    ).start()
  }
}

object Watcher {

  /** Creates a new watcher from `path`, a [[java.nio.file.Path Path]], polling it every `interval` and watching for
    * the watchevent kinds provided.
    *
    * @param path     the path
    * @param interval the interval
    * @param kinds    the [[WatchEvent.Kind]] to listen for, eg. `ENTRY_MODIFY`
    * @return a Watcher
    */
  def fromPath(path: Path,
               interval: FiniteDuration,
               kinds: WatchEvent.Kind[_]*): Watcher = {
    new WatcherImpl(path, interval, kinds: _*)
  }

  /** Same as [[Watcher.fromPath]] but from a string.
    *
    * @param path     the path
    * @param interval the interval
    * @param kinds    the [[WatchEvent.Kind Kind]] to listen for, e.g. `ENTRY_CREATE`
    * @return a Watcher instance
    */
  def fromString(path: String,
                 interval: FiniteDuration,
                 kinds: WatchEvent.Kind[_]*) =
    new WatcherImpl(Paths.get(path), interval, kinds: _*)
}
