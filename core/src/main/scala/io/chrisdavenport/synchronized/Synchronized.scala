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
 * 4. Private Class And For Comprehension Clarity
 */
package io.chrisdavenport.synchronized

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._

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
  def apply[F[_]](implicit F: Concurrent[F]): ApplyBuilders[F] =
    new ApplyBuilders(F)

  def of[F[_], A](a: A)(implicit F: Concurrent[F]): F[Synchronized[F, A]] =
    for {
      initial <- Deferred[F, Unit]
      _ <- initial.complete(())
      ref <- Ref.of[F, Deferred[F, Unit]](initial)
    } yield new DeferredRefSynchronized[F, A](ref, a)


  private class DeferredRefSynchronized[F[_],A](
    ref: Ref[F, Deferred[F, Unit]], a: A
  )(implicit F: Concurrent[F]) extends Synchronized[F, A]{
      override def use[B](f: A => F[B]): F[B] =
        Deferred[F, Unit].flatMap { next =>
          F.bracket(ref.getAndSet(next)) { current =>
            current.get.flatMap(_ => f(a))
          }(_ => next.complete(()))
      }
  }

  final class ApplyBuilders[F[_]](private val F: Concurrent[F]) extends AnyVal {
    def of[A](a: A): F[Synchronized[F, A]] =
      Synchronized.of(a)(F)
  }
}