package arrow.effects

import arrow.Kind
import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.Try
import arrow.core.andThen
import arrow.core.identity
import arrow.effects.typeclasses.Disposable
import arrow.effects.typeclasses.ExitCase
import arrow.effects.typeclasses.Proc
import arrow.higherkind
import arrow.typeclasses.Traverse
import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import kotlin.coroutines.experimental.CoroutineContext

fun <A> Deferred<A>.k(): DeferredK<A> =
  DeferredK(this)

fun <A> DeferredKOf<A>.value(): Deferred<A> = this.fix().deferred

@higherkind
data class DeferredK<out A>(val deferred: Deferred<A>) : DeferredKOf<A>, Deferred<A> by deferred {

  fun <B> map(f: (A) -> B): DeferredK<B> =
    flatMap { a: A -> just(f(a)) }

  fun <B> ap(fa: DeferredKOf<(A) -> B>): DeferredK<B> =
    flatMap { a -> fa.fix().map { ff -> ff(a) } }

  fun <B> flatMap(f: (A) -> DeferredKOf<B>): DeferredK<B> =
    kotlinx.coroutines.experimental.async(Unconfined, CoroutineStart.LAZY) {
      f(await()).await()
    }.k()

  fun <B> bracketCase(use: (A) -> DeferredK<B>, release: (A, ExitCase<Throwable>) -> DeferredK<Unit>): DeferredK<B> =
    flatMap { a ->
      try {
        use(a).also { release(a, ExitCase.Completed) }
      } catch (e: Exception) {
        release(a, ExitCase.Error(e))
        DeferredK.failed<B>(e)
      }
    }

  fun continueOn(ctx: CoroutineContext): DeferredK<A> =
    kotlinx.coroutines.experimental.async(ctx, CoroutineStart.LAZY) {
      deferred.await()
    }.k()

  companion object {
    fun unit(): DeferredK<Unit> =
      CompletableDeferred(Unit).k()

    fun <A> just(a: A): DeferredK<A> =
      CompletableDeferred(a).k()

    fun <A> defer(ctx: CoroutineContext = DefaultDispatcher, start: CoroutineStart = CoroutineStart.LAZY, f: suspend () -> A): DeferredK<A> =
      kotlinx.coroutines.experimental.async(ctx, start) { f() }.k()

    fun <A> defer(ctx: CoroutineContext = DefaultDispatcher, start: CoroutineStart = CoroutineStart.LAZY, fa: () -> DeferredKOf<A>): DeferredK<A> =
      kotlinx.coroutines.experimental.async(ctx, start) { fa().await() }.k()

    operator fun <A> invoke(ctx: CoroutineContext = DefaultDispatcher, start: CoroutineStart = CoroutineStart.DEFAULT, f: () -> A): DeferredK<A> =
      kotlinx.coroutines.experimental.async(ctx, start) { f() }.k()

    fun <A> failed(t: Throwable): DeferredK<A> =
      CompletableDeferred<A>().apply { completeExceptionally(t) }.k()

    fun <A> raiseError(t: Throwable): DeferredK<A> =
      failed(t)

    /**
     * Starts a coroutine that'll run [Proc].
     *
     * Matching the behavior of [async],
     * its [CoroutineContext] is set to [DefaultDispatcher]
     * and its [CoroutineStart] is [CoroutineStart.DEFAULT].
     */
    fun <A> async(ctx: CoroutineContext = DefaultDispatcher, start: CoroutineStart = CoroutineStart.DEFAULT, fa: Proc<A>): DeferredK<A> =
      kotlinx.coroutines.experimental.async(ctx, start) {
        CompletableDeferred<A>().apply {
          fa {
            it.fold(this::completeExceptionally, this::complete)
          }
        }.await()
      }.k()

    fun <A, B> tailRecM(a: A, f: (A) -> DeferredKOf<Either<A, B>>): DeferredK<B> =
      f(a).value().let { initial: Deferred<Either<A, B>> ->
        var current: Deferred<Either<A, B>> = initial
        kotlinx.coroutines.experimental.async(Unconfined, CoroutineStart.LAZY) {
          val result: B
          while (true) {
            val actual: Either<A, B> = current.await()
            if (actual is Either.Right) {
              result = actual.b
              break
            } else if (actual is Either.Left) {
              current = f(actual.a).fix()
            }
          }
          result
        }.k()
      }
  }
}

fun <A> DeferredKOf<A>.handleErrorWith(f: (Throwable) -> DeferredK<A>): DeferredK<A> =
  async(Unconfined, CoroutineStart.LAZY) {
    Try { await() }.fold({ f(it).await() }, ::identity)
  }.k()

fun <A> DeferredKOf<A>.unsafeAttemptSync(): Try<A> =
  Try { unsafeRunSync() }

fun <A> DeferredKOf<A>.unsafeRunSync(): A =
  runBlocking { await() }

fun <A> DeferredKOf<A>.runAsync(cb: (Either<Throwable, A>) -> DeferredKOf<Unit>): DeferredK<Unit> =
  DeferredK(Unconfined, CoroutineStart.DEFAULT) {
    unsafeRunAsync(cb.andThen { })
  }

fun <A> DeferredKOf<A>.runAsyncCancellable(onCancel: OnCancel = OnCancel.Silent, cb: (Either<Throwable, A>) -> DeferredKOf<Unit>): DeferredK<Disposable> =
  async {
    val call = runAsync(cb)
    val disposable: Disposable = {
      when (onCancel) {
        OnCancel.ThrowCancellationException -> call.cancel(OnCancel.CancellationException)
        OnCancel.Silent -> call.cancel()
      }
    }
    disposable
  }.k()

fun <A> DeferredKOf<A>.unsafeRunAsync(cb: (Either<Throwable, A>) -> Unit): Unit =
  async(Unconfined, CoroutineStart.DEFAULT) {
    Try { await() }.fold({ cb(Left(it)) }, { cb(Right(it)) })
  }.let {
    // Deferred swallows all exceptions. How about no.
    it.invokeOnCompletion { a: Throwable? ->
      if (a != null) throw a
    }
  }

suspend fun <A> DeferredKOf<A>.await(): A = this.fix().await()

suspend fun <F, A> Kind<F, DeferredKOf<A>>.awaitAll(T: Traverse<F>): Kind<F, A> = T.run {
  this@awaitAll.sequence(DeferredK.applicative()).await()
}
