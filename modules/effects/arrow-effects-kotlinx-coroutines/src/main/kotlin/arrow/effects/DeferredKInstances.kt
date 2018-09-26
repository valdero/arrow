package arrow.effects

import arrow.Kind
import arrow.core.Either
import arrow.effects.typeclasses.Async
import arrow.effects.typeclasses.Bracket
import arrow.effects.typeclasses.ConcurrentEffect
import arrow.effects.typeclasses.Disposable
import arrow.effects.typeclasses.Effect
import arrow.effects.typeclasses.ExitCase
import arrow.effects.typeclasses.MonadDefer
import arrow.effects.typeclasses.Proc
import arrow.instance
import arrow.typeclasses.Applicative
import arrow.typeclasses.ApplicativeError
import arrow.typeclasses.Functor
import arrow.typeclasses.Monad
import arrow.typeclasses.MonadError
import kotlin.coroutines.experimental.CoroutineContext
import arrow.effects.handleErrorWith as deferredHandleErrorWith
import arrow.effects.runAsync as deferredRunAsync

@instance(DeferredK::class)
interface DeferredKFunctorInstance : Functor<ForDeferredK> {
  override fun <A, B> Kind<ForDeferredK, A>.map(f: (A) -> B): DeferredK<B> =
    fix().map(f)
}

@instance(DeferredK::class)
interface DeferredKApplicativeInstance : Applicative<ForDeferredK> {
  override fun <A, B> Kind<ForDeferredK, A>.map(f: (A) -> B): DeferredK<B> =
    fix().map(f)

  override fun <A> just(a: A): DeferredK<A> =
    DeferredK.just(a)

  override fun <A, B> DeferredKOf<A>.ap(ff: DeferredKOf<(A) -> B>): DeferredK<B> =
    fix().ap(ff)
}

@instance(DeferredK::class)
interface DeferredKMonadInstance : Monad<ForDeferredK> {
  override fun <A, B> Kind<ForDeferredK, A>.flatMap(f: (A) -> Kind<ForDeferredK, B>): DeferredK<B> =
    fix().flatMap(f)

  override fun <A, B> Kind<ForDeferredK, A>.map(f: (A) -> B): DeferredK<B> =
    fix().map(f)

  override fun <A, B> tailRecM(a: A, f: (A) -> DeferredKOf<Either<A, B>>): DeferredK<B> =
    DeferredK.tailRecM(a, f)

  override fun <A, B> DeferredKOf<A>.ap(ff: DeferredKOf<(A) -> B>): DeferredK<B> =
    fix().ap(ff)

  override fun <A> just(a: A): DeferredK<A> =
    DeferredK.just(a)
}

@instance(DeferredK::class)
interface DeferredKApplicativeErrorInstance : DeferredKApplicativeInstance, ApplicativeError<ForDeferredK, Throwable> {
  override fun <A> raiseError(e: Throwable): DeferredK<A> =
    DeferredK.raiseError(e)

  override fun <A> DeferredKOf<A>.handleErrorWith(f: (Throwable) -> DeferredKOf<A>): DeferredK<A> =
    deferredHandleErrorWith { f(it).fix() }
}

@instance(DeferredK::class)
interface DeferredKMonadErrorInstance : DeferredKMonadInstance, MonadError<ForDeferredK, Throwable> {
  override fun <A> raiseError(e: Throwable): DeferredK<A> =
    DeferredK.raiseError(e)

  override fun <A> DeferredKOf<A>.handleErrorWith(f: (Throwable) -> DeferredKOf<A>): DeferredK<A> =
    deferredHandleErrorWith { f(it).fix() }
}

@instance(DeferredK::class)
interface DeferredKBracketInstance : DeferredKMonadErrorInstance, Bracket<ForDeferredK, Throwable> {
  override fun <A, B> Kind<ForDeferredK, A>.bracketCase(
    use: (A) -> Kind<ForDeferredK, B>,
    release: (A, ExitCase<Throwable>) -> Kind<ForDeferredK, Unit>): DeferredK<B> =
    fix().bracketCase({ a -> use(a).fix() }, { a, e -> release(a, e).fix() })
}

@instance(DeferredK::class)
interface DeferredKMonadDeferInstance : DeferredKBracketInstance, MonadDefer<ForDeferredK> {
  override fun <A> defer(fa: () -> DeferredKOf<A>): DeferredK<A> =
    DeferredK.defer(fa = fa)
}

@instance(DeferredK::class)
interface DeferredKAsyncInstance : DeferredKMonadDeferInstance, Async<ForDeferredK> {
  override fun <A> async(fa: Proc<A>): DeferredK<A> =
    DeferredK.async(fa = fa)

  override fun <A> DeferredKOf<A>.continueOn(ctx: CoroutineContext): DeferredK<A> =
    fix().continueOn(ctx)

  override fun <A> invoke(f: () -> A): DeferredK<A> =
    DeferredK.invoke(f = f)

  override fun <A> invoke(ctx: CoroutineContext, f: () -> A): Kind<ForDeferredK, A> =
    DeferredK.invoke(ctx = ctx, f = f)
}

@instance(DeferredK::class)
interface DeferredKEffectInstance : DeferredKAsyncInstance, Effect<ForDeferredK> {
  override fun <A> Kind<ForDeferredK, A>.runAsync(cb: (Either<Throwable, A>) -> DeferredKOf<Unit>): DeferredK<Unit> =
    fix().deferredRunAsync(cb)
}

@instance(DeferredK::class)
interface DeferredKConcurrentEffectInstance : DeferredKEffectInstance, ConcurrentEffect<ForDeferredK> {
  override fun <A> Kind<ForDeferredK, A>.runAsyncCancellable(cb: (Either<Throwable, A>) -> Kind<ForDeferredK, Unit>): Kind<ForDeferredK, Disposable> =
    fix().runAsyncCancellable(OnCancel.ThrowCancellationException, cb)
}

object DeferredKContext : DeferredKConcurrentEffectInstance

infix fun <A> ForDeferredK.Companion.extensions(f: DeferredKContext.() -> A): A =
  f(DeferredKContext)
