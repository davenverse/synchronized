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
 * 2. Remove Private Status
 * 3. Switch from fine grained to bulk imports
 * 4. Backported for 0.10 and fs2
 */
package io.chrisdavenport.synchronized

import cats.effect.Effect
import cats.implicits._
import fs2.async._
import scala.concurrent.ExecutionContext

/**
  * Provides synchronized access to a resource `A`, similar to that of
  * `synchronized(a) { use(a) }`, except the blocking is semantic only,
  * and no actual threads are blocked by the implementation.
  */
sealed abstract class Synchronized[F[_], A] {

  /**
    * Runs the specified function on the resource `A`, or waits until
    * given exclusive access to the resource, and then runs the given
    * function. Can be cancelled while waiting on exclusive access.
    */
  def use[B](f: A => F[B]): F[B]
}

object Synchronized {
  def apply[F[_]](implicit F: Effect[F], EC: ExecutionContext): ApplyBuilders[F] =
    new ApplyBuilders(F, EC)

  def of[F[_], A](a: A)(implicit F: Effect[F], EC: ExecutionContext): F[Synchronized[F, A]] =
    promise[F, Unit].flatMap { initial =>
      initial.complete(()).flatMap { _ =>
        refOf[F, Promise[F, Unit]](initial).map { ref =>
          new Synchronized[F, A] {
            override def use[B](f: A => F[B]): F[B] =
              promise[F, Unit].flatMap { next =>
                ref.modify(_ => next)
                  .flatMap{ change => 
                    val current = change.previous
                    current.get.flatMap(_ => f(a))
                  }
                  .attempt
                  .flatMap{ e => 
                    next.complete(()) >> F.fromEither(e)
                  }
              }
          }
        }
      }
    }

  final class ApplyBuilders[F[_]](private val F: Effect[F], EC: ExecutionContext) {
    def of[A](a: A): F[Synchronized[F, A]] =
      Synchronized.of(a)(F, EC)
  }
}