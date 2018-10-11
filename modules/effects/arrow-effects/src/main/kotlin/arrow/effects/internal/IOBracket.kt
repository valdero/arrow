package arrow.effects.internal

import arrow.Kind
import arrow.core.Either
import arrow.effects.ForIO
import arrow.effects.IO
import arrow.effects.IOFrame
import arrow.effects.IORunLoop
import arrow.effects.monad
import arrow.effects.typeclasses.ExitCase

internal typealias CancelToken<F> = Kind<F, Unit>

internal object IOBracket {

  /**
   * Implementation for `IO.bracketCase`.
   */
  operator fun <A, B> invoke(acquire: IO<A>, use: (A) -> IO<B>, release: (A, ExitCase<Throwable>) -> IO<Unit>): IO<B> =
    IO.Async { conn, cb ->
      // Doing manual plumbing; note that `acquire` here cannot be
      // cancelled due to executing it via `IORunLoop.start`
      IORunLoop.start(acquire, BracketStart(use, release, conn, cb), null)
    }

  // Internals of `IO.bracketCase`.
  private class BracketStart<A, B>(
    val use: (A) -> IO<B>,
    val release: (A, ExitCase<Throwable>) -> IO<Unit>,
    val conn: IOConnection,
    val cb: (Either<Throwable, B>) -> Unit) : (Either<Throwable, A>) -> Unit, Runnable {

    // This runnable is a dirty optimization to avoid some memory allocations;
    // This class switches from being a Callback to a Runnable, but relies on the internal IO callback protocol to be
    // respected (called at most once).
    private var result: Either<Throwable, A>? = null

    override fun invoke(ea: Either<Throwable, A>): Unit {
      if (result != null) {
        throw IllegalStateException("callback called multiple times!")
      }
      // Introducing a light async boundary, otherwise executing the required
      // logic directly will yield a StackOverflowException
      result = ea
      IO.async<Unit> { _, _ -> this.run() }
    }

    override fun run() {
      result!!.let { result ->
        when (result) {
          is Either.Right -> {
            val frame = BracketReleaseFrame<A, B>(result.b, release, conn)
            val onNext = {
              val fb = try {
                use(result.b)
              } catch (nonFatal: Exception) {
                IO.raiseError<B>(nonFatal)
              }
              fb.flatMap(frame)
            }
            // Registering our cancelable token ensures that in case
            // cancellation is detected, `release` gets called
            conn.push(frame.cancel())

            // Actual execution
            IORunLoop.startCancelable(onNext(), conn, { either -> IO { cb(either) } })
          }
          is Either.Left -> cb(result as Either<Throwable, B>)
        }
      }
    }
  }

  fun <A> guaranteeCase(source: IO<A>, release: (ExitCase<Throwable>) -> IO<Unit>): IO<A> =
    IO.Async { cb ->
      // Light async boundary, otherwise this will trigger a StackOverflowException
      val frame = EnsureReleaseFrame<A>(release)
      val onNext = source.flatMap(frame)
      // Registering our cancelable token ensures that in case
      // cancellation is detected, `release` gets called
      // conn.push(frame.cancel)

      // Actual execution
      onNext.runAsyncCancellable(cb = { either -> IO { cb(either) } })
    }

  private class BracketReleaseFrame<A, B>(val a: A, val releaseFn: (A, ExitCase<Throwable>) -> IO<Unit>, conn: IOConnection) : BaseReleaseFrame<A, B>(conn) {

    override fun release(c: ExitCase<Throwable>): CancelToken<ForIO> =
      releaseFn(a, c)
  }

  private class EnsureReleaseFrame<A>(val releaseFn: (ExitCase<Throwable>) -> IO<Unit>) : BaseReleaseFrame<Unit, A>() {

    override fun release(c: ExitCase<Throwable>): CancelToken<ForIO> = releaseFn(c)
  }

  private abstract class BaseReleaseFrame<A, B>(conn: IOConnection) : IOFrame<B, IO<B>> {

    abstract fun release(c: ExitCase<Throwable>): CancelToken<ForIO>

    override fun recover(e: Throwable): IO<B> = IO.monad().run {
      release(ExitCase.Error(e)).flatMap { ReleaseRecover(e).invoke(Unit) }
    }

    override fun invoke(a: B): IO<B> = IO.monad().run { release(ExitCase.Completed).map { a } }
  }

  private class ReleaseRecover(val e: Throwable) : IOFrame<Unit, IO<Nothing>> {

    override fun recover(e2: Throwable): IO<Nothing> =
      IO.raiseError(composeErrors(e, e2))

    private fun composeErrors(first: Throwable, vararg rest: Throwable): Throwable {
      rest.forEach { if (it != first) first.addSuppressed(it) }
      return first
    }

    override fun invoke(a: Unit): IO<Nothing> = IO.raiseError(e)
  }
}
