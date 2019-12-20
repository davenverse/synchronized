/*
 * Copyright 2018-2019 OVO Energy Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * 
 * Modifications 
 * Copyright (C) 2019 Christopher Davenport
 * Edits: 
 * 1. Change Package
 * 2. Modify Test to not required additional Spec
 */
package io.chrisdavenport.synchronized

import cats.effect.concurrent.{MVar, Ref}
import cats.effect.{Fiber, IO, ContextShift, Timer}
import cats.implicits._
import org.scalatest.{Assertions}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext

final class SynchronizedSpec extends AnyFunSpec with Assertions with Matchers {

  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  implicit val timer: Timer[IO] =
    IO.timer(ExecutionContext.global)

  it("should synchronize concurrent access to the resource") {
    val success =
      (for {
        success <- Ref[IO].of(true)
        resource <- MVar[IO].of("resource")
        synch <- Synchronized[IO].of(resource)
        use = (n: Int) =>
          IO.shift >> synch
            .use { mVar =>
              mVar.tryTake.flatMap { takenOption =>
                takenOption
                  .map(mVar.put(_) >> {
                    if (n % 50 == 0)
                      IO.raiseError(new RuntimeException)
                    else IO.unit
                  })
                  .getOrElse(success.set(false))
              }
            }
            .attempt
            .start
        uses <- (1 to 1000).toList.map(use).combineAll
        _ <- uses.join
        succeeded <- success.get
      } yield succeeded).unsafeRunSync

    assert(success)
  }

  it("should continue to work when use is cancelled") {
    val success =
      (for {
        synch <- Synchronized[IO].of("resource")
        fiberUnit = Fiber(IO.unit, IO.unit)
        use = (n: Int) =>
          IO.shift >> synch
            .use(_ => IO.unit)
            .start
            .flatMap { fiber =>
              if (n % 50 == 0)
                fiber.cancel.as(fiberUnit)
              else IO.pure(fiber)
          }
        used <- (1 to 1000).toList.map(use).combineAll
        _ <- used.join
      } yield true).unsafeRunSync

    assert(success)
  }
}