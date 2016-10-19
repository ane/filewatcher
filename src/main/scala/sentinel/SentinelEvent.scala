package sentinel

import java.nio.file.WatchEvent.Kind
import java.nio.file.{Path, WatchEvent}

/**
  * Created by ane on 19.10.2016.
  */
trait SentinelEvent {

  /**
    * The kind of watch event that occurred.
    *
    * @return
    */
  def kind: WatchEvent.Kind[_]

  /** The path where the watch event occurred.
    *
    * @return
    */
  def path: Path
}

private[sentinel] case class SentinelEventImpl[T](kind: WatchEvent.Kind[T],
                                               path: Path)
    extends SentinelEvent

object SentinelEvent {
  def apply[T](kind: WatchEvent.Kind[T], path: Path): SentinelEvent = new SentinelEventImpl[T](kind, path)
}
