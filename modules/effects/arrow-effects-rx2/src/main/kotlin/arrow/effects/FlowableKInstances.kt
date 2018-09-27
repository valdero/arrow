package arrow.effects

import arrow.Kind
import arrow.core.Either
import arrow.core.Eval
import arrow.effects.typeclasses.*
import arrow.instance
import arrow.typeclasses.*
import io.reactivex.BackpressureStrategy
import kotlin.coroutines.experimental.CoroutineContext

@instance(FlowableK::class)
interface FlowableKFunctorInstance : Functor<ForFlowableK> {
  override fun <A, B> Kind<ForFlowableK, A>.map(f: (A) -> B): FlowableK<B> =
    fix().map(f)
}

@instance(FlowableK::class)
interface FlowableKApplicativeInstance : Applicative<ForFlowableK> {
  override fun <A, B> FlowableKOf<A>.ap(ff: FlowableKOf<(A) -> B>): FlowableK<B> =
    fix().ap(ff)

  override fun <A, B> Kind<ForFlowableK, A>.map(f: (A) -> B): FlowableK<B> =
    fix().map(f)

  override fun <A> just(a: A): FlowableK<A> =
    FlowableK.just(a)
}

@instance(FlowableK::class)
interface FlowableKMonadInstance : Monad<ForFlowableK> {
  override fun <A, B> FlowableKOf<A>.ap(ff: FlowableKOf<(A) -> B>): FlowableK<B> =
    fix().ap(ff)

  override fun <A, B> FlowableKOf<A>.flatMap(f: (A) -> Kind<ForFlowableK, B>): FlowableK<B> =
    fix().flatMap(f)

  override fun <A, B> FlowableKOf<A>.map(f: (A) -> B): FlowableK<B> =
    fix().map(f)

  override fun <A, B> tailRecM(a: A, f: kotlin.Function1<A, FlowableKOf<arrow.core.Either<A, B>>>): FlowableK<B> =
    FlowableK.tailRecM(a, f)

  override fun <A> just(a: A): FlowableK<A> =
    FlowableK.just(a)
}

@instance(FlowableK::class)
interface FlowableKFoldableInstance : Foldable<ForFlowableK> {
  override fun <A, B> Kind<ForFlowableK, A>.foldLeft(b: B, f: (B, A) -> B): B =
    fix().foldLeft(b, f)

  override fun <A, B> Kind<ForFlowableK, A>.foldRight(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): arrow.core.Eval<B> =
    fix().foldRight(lb, f)
}

@instance(FlowableK::class)
interface FlowableKTraverseInstance : Traverse<ForFlowableK> {
  override fun <A, B> Kind<ForFlowableK, A>.map(f: (A) -> B): FlowableK<B> =
    fix().map(f)

  override fun <G, A, B> FlowableKOf<A>.traverse(AP: Applicative<G>, f: (A) -> Kind<G, B>): Kind<G, FlowableK<B>> =
    fix().traverse(AP, f)

  override fun <A, B> Kind<ForFlowableK, A>.foldLeft(b: B, f: (B, A) -> B): B =
    fix().foldLeft(b, f)

  override fun <A, B> Kind<ForFlowableK, A>.foldRight(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): arrow.core.Eval<B> =
    fix().foldRight(lb, f)
}

@instance(FlowableK::class)
interface FlowableKApplicativeErrorInstance :
  FlowableKApplicativeInstance,
  ApplicativeError<ForFlowableK, Throwable> {
  override fun <A> raiseError(e: Throwable): FlowableK<A> =
    FlowableK.raiseError(e)

  override fun <A> FlowableKOf<A>.handleErrorWith(f: (Throwable) -> FlowableKOf<A>): FlowableK<A> =
    fix().handleErrorWith { f(it).fix() }
}

@instance(FlowableK::class)
interface FlowableKMonadErrorInstance :
  FlowableKMonadInstance,
  MonadError<ForFlowableK, Throwable> {
  override fun <A> raiseError(e: Throwable): FlowableK<A> =
    FlowableK.raiseError(e)

  override fun <A> FlowableKOf<A>.handleErrorWith(f: (Throwable) -> FlowableKOf<A>): FlowableK<A> =
    fix().handleErrorWith { f(it).fix() }
}

@instance(FlowableK::class)
interface FlowableKBracketInstance : FlowableKMonadErrorInstance, Bracket<ForFlowableK, Throwable> {
  override fun <A, B> Kind<ForFlowableK, A>.bracketCase(use: (A) -> Kind<ForFlowableK, B>, release: (A, ExitCase<Throwable>) -> Kind<ForFlowableK, Unit>): FlowableK<B> =
    fix().bracketCase({ use(it) }, { a, e -> release(a, e) })
}

@instance(FlowableK::class)
interface FlowableKMonadDeferInstance :
  FlowableKBracketInstance,
  MonadDefer<ForFlowableK> {
  override fun <A> defer(fa: () -> FlowableKOf<A>): FlowableK<A> =
    FlowableK.defer(fa)

  fun BS(): BackpressureStrategy = BackpressureStrategy.BUFFER
}

@instance(FlowableK::class)
interface FlowableKAsyncInstance :
  FlowableKMonadDeferInstance,
  Async<ForFlowableK> {
  override fun <A> async(fa: Proc<A>): FlowableK<A> =
    FlowableK.async(fa, BS())

  override fun <A> FlowableKOf<A>.continueOn(ctx: CoroutineContext): FlowableK<A> =
    fix().continueOn(ctx)
}

@instance(FlowableK::class)
interface FlowableKEffectInstance :
  FlowableKAsyncInstance,
  Effect<ForFlowableK> {
  override fun <A> FlowableKOf<A>.runAsync(cb: (Either<Throwable, A>) -> FlowableKOf<Unit>): FlowableK<Unit> =
    fix().runAsync(cb)
}

@instance(FlowableK::class)
interface FlowableKConcurrentEffectInstance : FlowableKEffectInstance, ConcurrentEffect<ForFlowableK> {
  override fun <A> Kind<ForFlowableK, A>.runAsyncCancellable(cb: (Either<Throwable, A>) -> FlowableKOf<Unit>): FlowableK<Disposable> =
    fix().runAsyncCancellable(cb)
}

object FlowableKContext : FlowableKConcurrentEffectInstance, FlowableKTraverseInstance {
  override fun <A, B> FlowableKOf<A>.map(f: (A) -> B): FlowableK<B> =
    fix().map(f)
}

infix fun <A> ForFlowableK.Companion.extensions(f: FlowableKContext.() -> A): A =
  f(FlowableKContext)
