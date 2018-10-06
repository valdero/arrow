package arrow.data

import arrow.Kind
import arrow.core.Either
import arrow.core.ForId
import arrow.core.ForTry
import arrow.core.Id
import arrow.core.Try
import arrow.core.fix
import arrow.core.monad
import arrow.core.monadError
import arrow.effects.ForIO
import arrow.effects.IO
import arrow.effects.bracket
import arrow.effects.monadError
import arrow.instances.ForKleisli
import arrow.test.UnitSpec
import arrow.test.laws.BracketLaws
import arrow.test.laws.ContravariantLaws
import arrow.test.laws.MonadErrorLaws
import arrow.typeclasses.Conested
import arrow.typeclasses.Eq
import arrow.typeclasses.conest
import arrow.typeclasses.counnest
import io.kotlintest.KTestJUnitRunner
import io.kotlintest.matchers.shouldBe
import org.junit.runner.RunWith

@Suppress("TestFunctionName")
@RunWith(KTestJUnitRunner::class)
class KleisliTest : UnitSpec() {
  private fun <A> EQ(): Eq<KleisliOf<ForTry, Int, A>> = Eq { a, b ->
    a.fix().run(1) == b.fix().run(1)
  }

  private fun <A> ConestTryEQ(): Eq<Kind<Conested<Kind<ForKleisli, ForTry>, A>, Int>> = Eq { a, b ->
    a.counnest().fix().run(1) == b.counnest().fix().run(1)
  }

  private fun EQBracket(): Eq<Kind<KleisliPartialOf<ForIO, Int>, Int>> = Eq { a, b ->
    a.fix().run(1) == b.fix().run(1)
  }

  private fun EQBracketError(): Eq<Kind<KleisliPartialOf<ForIO, Int>, Either<Throwable, Int>>> = Eq { a, b ->
    a.fix().run(1) == b.fix().run(1)
  }

  init {

    ForKleisli<ForTry, Int, Throwable>(Try.monadError()) extensions {
      testLaws(
        ContravariantLaws.laws(Kleisli.contravariant(), { Kleisli { x: Int -> Try.just(x) }.conest() }, ConestTryEQ()),
        MonadErrorLaws.laws(this, EQ(), EQ())
      )
    }

    ForKleisli<ForIO, Int, Throwable>(IO.monadError()) extensions {
      testLaws(
        BracketLaws.laws(
          Kleisli.bracket<ForIO, Int, Throwable>(IO.bracket()),
          { Kleisli { x: Int -> IO.just(x) } },
          EQBracket(),
          EQBracketError(),
          EQBracket())
      )
    }

    "andThen should continue sequence" {
      val kleisli: Kleisli<ForId, Int, Int> = Kleisli { a: Int -> Id(a) }

      kleisli.andThen(Id.monad(), Id(3)).run(0).fix().value shouldBe 3

      kleisli.andThen(Id.monad()) { b -> Id(b + 1) }.run(0).fix().value shouldBe 1
    }
  }
}
