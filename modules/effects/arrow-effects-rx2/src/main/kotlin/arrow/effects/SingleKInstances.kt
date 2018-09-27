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

@instance(SingleK::class)
interface SingleKFunctorInstance : Functor<ForSingleK> {
  override fun <A, B> Kind<ForSingleK, A>.map(f: (A) -> B): SingleK<B> =
    fix().map(f)
}

@instance(SingleK::class)
interface SingleKApplicativeInstance : Applicative<ForSingleK> {
  override fun <A, B> SingleKOf<A>.ap(ff: SingleKOf<(A) -> B>): SingleK<B> =
    fix().ap(ff)

  override fun <A, B> Kind<ForSingleK, A>.map(f: (A) -> B): SingleK<B> =
    fix().map(f)

  override fun <A> just(a: A): SingleK<A> =
    SingleK.just(a)
}

@instance(SingleK::class)
interface SingleKMonadInstance : Monad<ForSingleK> {
  override fun <A, B> SingleKOf<A>.ap(ff: SingleKOf<(A) -> B>): SingleK<B> =
    fix().ap(ff)

  override fun <A, B> SingleKOf<A>.flatMap(f: (A) -> Kind<ForSingleK, B>): SingleK<B> =
    fix().flatMap(f)

  override fun <A, B> SingleKOf<A>.map(f: (A) -> B): SingleK<B> =
    fix().map(f)

  override fun <A, B> tailRecM(a: A, f: kotlin.Function1<A, SingleKOf<arrow.core.Either<A, B>>>): SingleK<B> =
    SingleK.tailRecM(a, f)

  override fun <A> just(a: A): SingleK<A> =
    SingleK.just(a)
}

@instance(SingleK::class)
interface SingleKApplicativeErrorInstance :
  SingleKApplicativeInstance,
  ApplicativeError<ForSingleK, Throwable> {
  override fun <A> raiseError(e: Throwable): SingleK<A> =
    SingleK.raiseError(e)

  override fun <A> SingleKOf<A>.handleErrorWith(f: (Throwable) -> SingleKOf<A>): SingleK<A> =
    fix().handleErrorWith { f(it).fix() }
}

@instance(SingleK::class)
interface SingleKMonadErrorInstance :
  SingleKMonadInstance,
  MonadError<ForSingleK, Throwable> {
  override fun <A> raiseError(e: Throwable): SingleK<A> =
    SingleK.raiseError(e)

  override fun <A> SingleKOf<A>.handleErrorWith(f: (Throwable) -> SingleKOf<A>): SingleK<A> =
    fix().handleErrorWith { f(it).fix() }
}

@instance(SingleK::class)
interface SingleKBracketInstance : SingleKMonadErrorInstance, Bracket<ForSingleK, Throwable> {
  override fun <A, B> Kind<ForSingleK, A>.bracketCase(use: (A) -> Kind<ForSingleK, B>, release: (A, ExitCase<Throwable>) -> Kind<ForSingleK, Unit>): SingleK<B> =
    fix().bracketCase({ use(it) }, { a, e -> release(a, e) })
}

@instance(SingleK::class)
interface SingleKMonadDeferInstance : SingleKBracketInstance, MonadDefer<ForSingleK> {
  override fun <A> defer(fa: () -> SingleKOf<A>): SingleK<A> =
    SingleK.defer(fa)
}

@instance(SingleK::class)
interface SingleKAsyncInstance :
  SingleKMonadDeferInstance,
  Async<ForSingleK> {
  override fun <A> async(fa: Proc<A>): SingleK<A> =
    SingleK.async(fa)

  override fun <A> SingleKOf<A>.continueOn(ctx: CoroutineContext): SingleK<A> =
    fix().continueOn(ctx)
}

@instance(SingleK::class)
interface SingleKEffectInstance :
  SingleKAsyncInstance,
  Effect<ForSingleK> {
  override fun <A> SingleKOf<A>.runAsync(cb: (Either<Throwable, A>) -> SingleKOf<Unit>): SingleK<Unit> =
    fix().runAsync(cb)
}

@instance(SingleK::class)
interface SingleKConcurrentEffectInstance : SingleKEffectInstance, ConcurrentEffect<ForSingleK> {
  override fun <A> Kind<ForSingleK, A>.runAsyncCancellable(cb: (Either<Throwable, A>) -> SingleKOf<Unit>): SingleK<Disposable> =
    fix().runAsyncCancellable(cb)
}

object SingleKContext : SingleKConcurrentEffectInstance

infix fun <A> ForSingleK.Companion.extensions(f: SingleKContext.() -> A): A =
  f(SingleKContext)
