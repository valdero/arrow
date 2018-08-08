package arrow.mtl.instances

import arrow.Kind
import arrow.core.*
import arrow.data.OptionT
import arrow.data.OptionTPartialOf
import arrow.data.fix
import arrow.extension
import arrow.instances.OptionTFunctorInstance
import arrow.instances.OptionTMonadInstance
import arrow.instances.OptionTMonoidKInstance
import arrow.instances.OptionTTraverseInstance
import arrow.mtl.typeclasses.FunctorFilter
import arrow.mtl.typeclasses.TraverseFilter
import arrow.typeclasses.*

@extension
interface OptionTFunctorFilterInstance<F> : OptionTFunctorInstance<F>, FunctorFilter<OptionTPartialOf<F>> {

  override fun <A, B> Kind<OptionTPartialOf<F>, A>.mapFilter(f: (A) -> Option<B>): OptionT<F, B> =
    fix().mapFilter(FF(), f)
}

@extension
interface OptionTTraverseFilterInstance<F> :
  OptionTTraverseInstance<F>,
  TraverseFilter<OptionTPartialOf<F>> {

  override fun FFF(): TraverseFilter<F>

  override fun <G, A, B> Kind<OptionTPartialOf<F>, A>.traverseFilter(AP: Applicative<G>, f: (A) -> Kind<G, Option<B>>): Kind<G, OptionT<F, B>> =
    fix().traverseFilter(f, AP, FFF())
}

fun <F, G, A, B> OptionT<F, A>.traverseFilter(f: (A) -> Kind<G, Option<B>>, GA: Applicative<G>, FF: Traverse<F>): Kind<G, OptionT<F, B>> {
  val fa = ComposedTraverseFilter(FF, Option.traverseFilter(), Option.applicative()).traverseFilterC(value, f, GA)
  val mapper: (Kind<Nested<F, ForOption>, B>) -> OptionT<F, B> = { OptionT(FF.run { it.unnest().map { it.fix() } }) }
  return GA.run { fa.map(mapper) }
}

class OptionTMtlContext<F>(val MF: Monad<F>, val TF: TraverseFilter<F>) : OptionTMonadInstance<F>, OptionTMonoidKInstance<F>, OptionTTraverseFilterInstance<F> {

  override fun FF(): Monad<F> = MF

  override fun FFF(): TraverseFilter<F> = TF

  override fun <A, B> Kind<OptionTPartialOf<F>, A>.map(f: (A) -> B): OptionT<F, B> =
    fix().map(FF(), f)
}

class OptionTMtlContextPartiallyApplied<F>(val MF: Monad<F>, val TF: TraverseFilter<F>) {
  infix fun <A> extensions(f: OptionTMtlContext<F>.() -> A): A =
    f(OptionTMtlContext(MF, TF))
}

fun <F> ForOptionT(MF: Monad<F>, TF: TraverseFilter<F>): OptionTMtlContextPartiallyApplied<F> =
  OptionTMtlContextPartiallyApplied(MF, TF)
